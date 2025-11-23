package cn.lihongjie.dht.btclient.service;

import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
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

    @Value("${direct.enabled:true}")
    private boolean directEnabled;

    @Value("${direct.connect.timeout-millis:1000}")
    private int connectTimeoutMillis;

    @Value("${direct.handshake.timeout-millis:2000}")
    private int handshakeTimeoutMillis;

    @Value("${direct.bloom.key:dht:bloom:direct}")
    private String directBloomKey;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final SecureRandom random = new SecureRandom();

    /**
     * 异步尝试直连握手。返回 true 表示握手成功（可继续后续优化做 ut_metadata），false 表示失败或未启用。
     */
    public CompletableFuture<Boolean> tryDirect(String infoHashHex, String ip, Integer port) {
        if (!directEnabled || ip == null || port == null || port <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        String dedupKey = infoHashHex + '|' + ip + '|' + port;
        if (bloomFilterService.exists(directBloomKey, dedupKey)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> doHandshake(infoHashHex, ip, port, dedupKey), executor);
    }

    private boolean doHandshake(String infoHashHex, String ip, int port, String dedupKey) {
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
                return false;
            }
            // 校验协议字符串长度与内容
            int pstrlen = resp[0] & 0xff;
            if (pstrlen != 19) {
                bloomFilterService.add(directBloomKey, dedupKey);
                return false;
            }
            String proto = new String(resp, 1, 19);
            if (!"BitTorrent protocol".equals(proto)) {
                bloomFilterService.add(directBloomKey, dedupKey);
                return false;
            }
            // 校验 infohash 匹配
            for (int i = 0; i < 20; i++) {
                if (resp[28 + i] != infoHashBytes[i]) { // 1 + 19 + 8 保留 = 28
                    bloomFilterService.add(directBloomKey, dedupKey);
                    return false;
                }
            }
            bloomFilterService.add(directBloomKey, dedupKey);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.info("Direct handshake SUCCESS infoHash={} peer={}:{} elapsed={}ms", infoHashHex, ip, port, elapsedMs);
            return true;
        } catch (Exception e) {
            bloomFilterService.add(directBloomKey, dedupKey);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.debug("Direct handshake FAIL infoHash={} peer={}:{} elapsed={}ms reason={}", infoHashHex, ip, port, elapsedMs, e.getMessage());
            return false;
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
}
