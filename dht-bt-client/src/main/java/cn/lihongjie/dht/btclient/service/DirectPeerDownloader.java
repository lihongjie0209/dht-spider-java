package cn.lihongjie.dht.btclient.service;

import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 直接对 Announce 中的 peer 发起 BitTorrent 握手尝试，快速直连获取后续元数据的可能性。
 * 当前实现只做握手探测（验证 infohash 与扩展位），不直接拉取 ut_metadata，成功后仍走现有 MetadataDownloader 流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectPeerDownloader {

    private final BloomFilterService bloomFilterService;
    private final MetadataPublisher metadataPublisher;

    @Value("${download.direct.enabled:false}")
    private boolean directEnabled;

    @Value("${direct.connect.timeout-millis:1000}")
    private int connectTimeoutMillis;

    @Value("${direct.handshake.timeout-millis:2000}")
    private int handshakeTimeoutMillis;

    @Value("${direct.bloom.key:dht:bloom:direct}")
    private String directBloomKey;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Bencode bencode = new Bencode();
    private final SecureRandom random = new SecureRandom();

    public static final int METADATA_PIECE_SIZE = 16 * 1024; // 16384

    public record DirectResult(boolean handshakeSuccess, boolean metadataSuccess) {}

    /**
     * 异步直连：握手 + 扩展握手 + ut_metadata 拉取。返回结果含握手和元数据是否成功。
     */
    public CompletableFuture<DirectResult> tryDirectAndFetch(String infoHashHex, String ip, Integer port) {
        if (!directEnabled || ip == null || port == null || port <= 0) {
            return CompletableFuture.completedFuture(new DirectResult(false, false));
        }
        String dedupKey = infoHashHex + '|' + ip + '|' + port;
        if (bloomFilterService.exists(directBloomKey, dedupKey)) {
            return CompletableFuture.completedFuture(new DirectResult(false, false));
        }
        return CompletableFuture.supplyAsync(() -> doHandshakeAndMetadata(infoHashHex, ip, port, dedupKey), executor);
    }

    private DirectResult doHandshakeAndMetadata(String infoHashHex, String ip, int port, String dedupKey) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), connectTimeoutMillis);
            socket.setSoTimeout(handshakeTimeoutMillis);
            byte[] infoHashBytes = HexFormat.of().parseHex(infoHashHex);
            byte[] peerId = generatePeerId();
            byte[] reserved = new byte[8];
            // 置位扩展协议支持（BEP 10），ut_metadata 需要此位：第 20 字节第 0 位（0x10）
            reserved[5] = 0x10; // extension protocol flag

            byte[] hs = buildHandshake(infoHashBytes, peerId, reserved);
            OutputStream out = socket.getOutputStream();
            out.write(hs);
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] resp = in.readNBytes(68); // 标准握手长度
            if (resp.length < 68) {
                log.debug("Direct handshake short response infoHash={} peer={}:{}", infoHashHex, ip, port);
                bloomFilterService.add(directBloomKey, dedupKey); // 标记尝试过
                return new DirectResult(false, false);
            }
            // 校验协议字符串长度与内容
            int pstrlen = resp[0] & 0xff;
            if (pstrlen != 19) {
                bloomFilterService.add(directBloomKey, dedupKey);
                return new DirectResult(false, false);
            }
            String proto = new String(resp, 1, 19);
            if (!"BitTorrent protocol".equals(proto)) {
                bloomFilterService.add(directBloomKey, dedupKey);
                return new DirectResult(false, false);
            }
            // 校验 infohash 匹配
            for (int i = 0; i < 20; i++) {
                if (resp[28 + i] != infoHashBytes[i]) { // 1 + 19 + 8 保留 = 28
                    bloomFilterService.add(directBloomKey, dedupKey);
                    return new DirectResult(false, false);
                }
            }
            // 扩展握手阶段
            boolean metadataSuccess = false;
            try {
                // 发送扩展握手 (message id 20, ext id 0)
                // 构造扩展握手：d1:m d11:ut_metadata i1e e
                java.util.Map<String,Object> m = new java.util.HashMap<>();
                m.put("ut_metadata", 1L);
                java.util.Map<String,Object> root = new java.util.HashMap<>();
                root.put("m", m);
                byte[] extHandshakePayload = bencode.encode(root);
                byte[] extHandshakeMsg = buildExtendedMessage(0, extHandshakePayload);
                out.write(extHandshakeMsg);
                out.flush();

                // 读取对方扩展握手
                ExtendedHandshake peerExt = readExtendedHandshake(in);
                if (peerExt != null && peerExt.utMetadataId > 0 && peerExt.metadataSize > 0 && peerExt.metadataSize < 2_000_000) {
                    int pieces = (peerExt.metadataSize + METADATA_PIECE_SIZE - 1) / METADATA_PIECE_SIZE;
                    byte[] metadata = new byte[peerExt.metadataSize];
                    int written = 0;
                    for (int piece = 0; piece < pieces; piece++) {
                        // 发送请求 msg_type=0
                        java.util.Map<String,Object> req = new java.util.HashMap<>();
                        req.put("msg_type", 0L);
                        req.put("piece", (long) piece);
                        byte[] reqPayload = bencode.encode(req);
                        byte[] reqMsg = buildExtendedMessage(peerExt.utMetadataId, reqPayload);
                        out.write(reqMsg);
                        out.flush();
                        // 读取响应 (msg_type=1)
                        MetadataPiece pieceResp = readMetadataPiece(in, peerExt.utMetadataId);
                        if (pieceResp == null || pieceResp.pieceIndex != piece || pieceResp.data == null) {
                            log.debug("ut_metadata piece mismatch infoHash={} piece={}", infoHashHex, piece);
                            break; // 失败回退
                        }
                        int offset = piece * METADATA_PIECE_SIZE;
                        int length = Math.min(pieceResp.data.length, metadata.length - offset);
                        System.arraycopy(pieceResp.data, 0, metadata, offset, length);
                        written += length;
                    }
                    if (written == peerExt.metadataSize) {
                        // 直接发布 raw info 字典
                        metadataPublisher.publishRawInfo(infoHashHex, metadata);
                        metadataSuccess = true;
                        log.info("ut_metadata DIRECT PUBLISH infoHash={} size={} bytes", infoHashHex, peerExt.metadataSize);
                    }
                }
            } catch (Exception extEx) {
                log.debug("Extended metadata fetch failed infoHash={} peer={}:{} reason={}", infoHashHex, ip, port, extEx.getMessage());
            }
            bloomFilterService.add(directBloomKey, dedupKey);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.info("Direct handshake SUCCESS infoHash={} peer={}:{} elapsed={}ms earlyMetadata={}", infoHashHex, ip, port, elapsedMs, metadataSuccess);
            return new DirectResult(true, metadataSuccess);
        } catch (Exception e) {
            bloomFilterService.add(directBloomKey, dedupKey);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.debug("Direct handshake FAIL infoHash={} peer={}:{} elapsed={}ms reason={}", infoHashHex, ip, port, elapsedMs, e.getMessage());
            return new DirectResult(false, false);
        }
    }

    private byte[] buildHandshake(byte[] infoHash, byte[] peerId, byte[] reserved) {
        byte[] hs = new byte[68];
        hs[0] = 19; // pstrlen
        byte[] proto = "BitTorrent protocol".getBytes();
        System.arraycopy(proto, 0, hs, 1, proto.length);
        System.arraycopy(reserved, 0, hs, 20, 8);
        System.arraycopy(infoHash, 0, hs, 28, 20);
        System.arraycopy(peerId, 0, hs, 48, 20);
        return hs;
    }

    private byte[] generatePeerId() {
        // 类似 -PC0001-xxxxxxxxxxxx
        String prefix = "-PC0001-";
        byte[] id = new byte[20];
        byte[] prefBytes = prefix.getBytes();
        System.arraycopy(prefBytes, 0, id, 0, prefBytes.length);
        for (int i = prefBytes.length; i < 20; i++) {
            int v = random.nextInt(36);
            char c = (char) (v < 10 ? ('0' + v) : ('a' + v - 10));
            id[i] = (byte) c;
        }
        return id;
    }

    private byte[] buildExtendedMessage(int extId, byte[] payload) {
        int len = 2 + payload.length; // message id + ext id + payload
        byte[] msg = new byte[4 + len];
        // length prefix
        msg[0] = (byte) ((len >>> 24) & 0xff);
        msg[1] = (byte) ((len >>> 16) & 0xff);
        msg[2] = (byte) ((len >>> 8) & 0xff);
        msg[3] = (byte) (len & 0xff);
        msg[4] = 20; // extension protocol
        msg[5] = (byte) extId;
        System.arraycopy(payload, 0, msg, 6, payload.length);
        return msg;
    }

    private ExtendedHandshake readExtendedHandshake(InputStream in) throws Exception {
        byte[] header = in.readNBytes(4);
        if (header.length < 4) return null;
        int len = ((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8) | (header[3] & 0xff);
        if (len <= 2) return null;
        byte[] body = in.readNBytes(len);
        if (body.length < len) return null;
        if (body[0] != 20 || body[1] != 0) return null; // not ext handshake
        byte[] payload = new byte[len - 2];
        System.arraycopy(body, 2, payload, 0, payload.length);
        try {
            Object decoded = bencode.decode(payload, Type.DICTIONARY);
            if (!(decoded instanceof java.util.Map)) return null;
            @SuppressWarnings("unchecked") java.util.Map<String,Object> root = (java.util.Map<String,Object>) decoded;
            Object mObj = root.get("m");
            int utId = -1;
            if (mObj instanceof java.util.Map) {
                Object ut = ((java.util.Map<?,?>) mObj).get("ut_metadata");
                if (ut instanceof Number) utId = ((Number) ut).intValue();
            }
            int size = -1;
            Object sz = root.get("metadata_size");
            if (sz instanceof Number) size = ((Number) sz).intValue();
            return new ExtendedHandshake(utId, size);
        } catch (Exception ex) {
            return null;
        }
    }

    private MetadataPiece readMetadataPiece(InputStream in, int utMetadataId) throws Exception {
        byte[] header = in.readNBytes(4);
        if (header.length < 4) return null;
        int len = ((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8) | (header[3] & 0xff);
        if (len <= 2) return null;
        byte[] body = in.readNBytes(len);
        if (body.length < len) return null;
        if (body[0] != 20 || body[1] != (byte) utMetadataId) return null;
        int dictStart = 2;
        int dictEnd = findBencodeElementEnd(body, dictStart);
        if (dictEnd < 0) return null;
        byte[] dictBytes = new byte[dictEnd - dictStart + 1];
        System.arraycopy(body, dictStart, dictBytes, 0, dictBytes.length);
        Object decoded = null;
        try { decoded = bencode.decode(dictBytes, Type.DICTIONARY); } catch (Exception ignore) { return null; }
        if (!(decoded instanceof java.util.Map)) return null;
        @SuppressWarnings("unchecked") java.util.Map<String,Object> mp = (java.util.Map<String,Object>) decoded;
        int msgType = mp.get("msg_type") instanceof Number ? ((Number) mp.get("msg_type")).intValue() : -1;
        int piece = mp.get("piece") instanceof Number ? ((Number) mp.get("piece")).intValue() : -1;
        if (msgType != 1 || piece < 0) return null;
        int dataOffset = dictStart + dictBytes.length;
        if (dataOffset > body.length) return null;
        byte[] data = new byte[body.length - dataOffset];
        System.arraycopy(body, dataOffset, data, 0, data.length);
        return new MetadataPiece(piece, data);
    }

    private int findBencodeElementEnd(byte[] buf, int offset) {
        if (offset >= buf.length) return -1;
        int i = offset;
        byte first = buf[i];
        if (first == 'd' || first == 'l') {
            i++; // consume type
            while (i < buf.length) {
                if (buf[i] == 'e') {
                    return i; // end of this container
                }
                int elemEnd = parseElement(buf, i);
                if (elemEnd < 0) return -1;
                i = elemEnd + 1;
            }
            return -1;
        } else {
            return parseElement(buf, i);
        }
    }
    private int parseElement(byte[] buf, int offset) {
        if (offset >= buf.length) return -1;
        byte b = buf[offset];
        if (b == 'i') { // integer i<digits>e
            int ePos = offset + 1;
            while (ePos < buf.length && buf[ePos] != 'e') ePos++;
            return ePos < buf.length ? ePos : -1;
        } else if (b == 'l' || b == 'd') {
            return findBencodeElementEnd(buf, offset); // recursive container
        } else if (b >= '0' && b <= '9') { // string: <len>:<data>
            int colon = offset;
            while (colon < buf.length && buf[colon] != ':') colon++;
            if (colon >= buf.length) return -1;
            int lenDigitsStart = offset;
            int lenDigitsEnd = colon - 1;
            String numStr = new String(buf, lenDigitsStart, colon - lenDigitsStart);
            int strLen;
            try { strLen = Integer.parseInt(numStr); } catch (NumberFormatException e) { return -1; }
            int end = colon + 1 + strLen - 1;
            return end < buf.length ? end : -1;
        } else {
            return -1;
        }
    }

    private record ExtendedHandshake(int utMetadataId, int metadataSize) {}
    private record MetadataPiece(int pieceIndex, byte[] data) {}
}
