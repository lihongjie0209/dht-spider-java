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

    public static final int METADATA_PIECE_SIZE = PeerProtocolUtil.METADATA_PIECE_SIZE; // reuse constant

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
            byte[] peerId = PeerProtocolUtil.generatePeerId();
            byte[] reserved = new byte[8];
            // 置位扩展协议支持（BEP 10），ut_metadata 需要此位：第 20 字节第 0 位（0x10）
            reserved[5] = 0x10; // extension protocol flag

            byte[] hs = PeerProtocolUtil.buildHandshake(infoHashBytes, peerId, reserved);
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
                byte[] extHandshakeMsg = PeerProtocolUtil.buildExtendedMessage(0, extHandshakePayload);
                out.write(extHandshakeMsg);
                out.flush();

                // 读取对方扩展握手
                PeerProtocolUtil.ExtendedHandshake peerExt = PeerProtocolUtil.readExtendedHandshake(in);
                if (peerExt != null && peerExt.utMetadataId() > 0 && peerExt.metadataSize() > 0 && peerExt.metadataSize() < 2_000_000) {
                    int pieces = (peerExt.metadataSize() + METADATA_PIECE_SIZE - 1) / METADATA_PIECE_SIZE;
                    byte[] metadata = new byte[peerExt.metadataSize()];
                    int written = 0;
                    for (int piece = 0; piece < pieces; piece++) {
                        // 发送请求 msg_type=0
                        java.util.Map<String,Object> req = new java.util.HashMap<>();
                        req.put("msg_type", 0L);
                        req.put("piece", (long) piece);
                        byte[] reqPayload = bencode.encode(req);
                        byte[] reqMsg = PeerProtocolUtil.buildExtendedMessage(peerExt.utMetadataId(), reqPayload);
                        out.write(reqMsg);
                        out.flush();
                        // 读取响应 (msg_type=1)
                        PeerProtocolUtil.MetadataPiece pieceResp = PeerProtocolUtil.readMetadataPiece(in, peerExt.utMetadataId());
                        if (pieceResp == null || pieceResp.pieceIndex() != piece || pieceResp.data() == null) {
                            log.debug("ut_metadata piece mismatch infoHash={} piece={}", infoHashHex, piece);
                            break; // 失败回退
                        }
                        int offset = piece * METADATA_PIECE_SIZE;
                        int length = Math.min(pieceResp.data().length, metadata.length - offset);
                        System.arraycopy(pieceResp.data(), 0, metadata, offset, length);
                        written += length;
                    }
                    if (written == peerExt.metadataSize()) {
                        // 直接发布 raw info 字典
                        metadataPublisher.publishRawInfo(infoHashHex, metadata);
                        metadataSuccess = true;
                        log.info("ut_metadata DIRECT PUBLISH infoHash={} size={} bytes", infoHashHex, peerExt.metadataSize());
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

    // Private parsing methods extracted to PeerProtocolUtil for testability.
}
