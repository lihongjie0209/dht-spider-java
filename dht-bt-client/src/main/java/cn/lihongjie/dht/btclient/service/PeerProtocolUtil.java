package cn.lihongjie.dht.btclient.service;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 抽取出的 BitTorrent 基础与扩展协议处理工具，便于单元测试。
 * 仅包含本项目 DirectPeerDownloader 需要的最小功能：
 * - 握手构造与解析（只验证协议字符串与 infohash）
 * - 扩展握手消息构造与解析 (ut_metadata 相关字段)
 * - ut_metadata piece 消息解析
 * - 基本 bencode 片段终止位置查找（有限度，满足当前解析需求）
 */
class PeerProtocolUtil {

    static final int METADATA_PIECE_SIZE = 16 * 1024; // 16384
    private static final Bencode BENCODE = new Bencode();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 构造标准 BT 握手 */
    static byte[] buildHandshake(byte[] infoHash, byte[] peerId, byte[] reserved) {
        byte[] hs = new byte[68];
        hs[0] = 19; // pstrlen
        byte[] proto = "BitTorrent protocol".getBytes();
        System.arraycopy(proto, 0, hs, 1, proto.length);
        System.arraycopy(reserved, 0, hs, 20, 8);
        System.arraycopy(infoHash, 0, hs, 28, 20);
        System.arraycopy(peerId, 0, hs, 48, 20);
        return hs;
    }

    /** 生成简单 peerId (-PC0001- + 随机) */
    static byte[] generatePeerId() {
        String prefix = "-PC0001-";
        byte[] id = new byte[20];
        byte[] prefBytes = prefix.getBytes();
        System.arraycopy(prefBytes, 0, id, 0, prefBytes.length);
        for (int i = prefBytes.length; i < 20; i++) {
            int v = RANDOM.nextInt(36);
            char c = (char) (v < 10 ? ('0' + v) : ('a' + v - 10));
            id[i] = (byte) c;
        }
        return id;
    }

    /** 构造扩展协议消息（包括扩展握手与 ut_metadata 消息） */
    static byte[] buildExtendedMessage(int extId, byte[] payload) {
        int len = 2 + payload.length; // message id + ext id + payload
        byte[] msg = new byte[4 + len];
        msg[0] = (byte) ((len >>> 24) & 0xff);
        msg[1] = (byte) ((len >>> 16) & 0xff);
        msg[2] = (byte) ((len >>> 8) & 0xff);
        msg[3] = (byte) (len & 0xff);
        msg[4] = 20; // extension protocol message id
        msg[5] = (byte) extId;
        System.arraycopy(payload, 0, msg, 6, payload.length);
        return msg;
    }

    /** 读取并解析扩展握手 (ext id 0) */
    static ExtendedHandshake readExtendedHandshake(InputStream in) throws Exception {
        byte[] header = in.readNBytes(4);
        if (header.length < 4) return null;
        int len = toInt(header);
        if (len <= 2) return null;
        byte[] body = in.readNBytes(len);
        if (body.length < len) return null;
        if (body[0] != 20 || body[1] != 0) return null; // not ext handshake
        byte[] payload = new byte[len - 2];
        System.arraycopy(body, 2, payload, 0, payload.length);
        try {
            Object decoded = BENCODE.decode(payload, Type.DICTIONARY);
            if (!(decoded instanceof java.util.Map)) return null;
            @SuppressWarnings("unchecked") java.util.Map<String,Object> root = (java.util.Map<String,Object>) decoded;
            Object mObj = root.get("m");
            int utId = -1;
            if (mObj instanceof java.util.Map<?,?> msgMap) {
                Object ut = msgMap.get("ut_metadata");
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

    /** 读取并解析 ut_metadata piece 响应 */
    static MetadataPiece readMetadataPiece(InputStream in, int utMetadataId) throws Exception {
        byte[] header = in.readNBytes(4);
        if (header.length < 4) return null;
        int len = toInt(header);
        if (len <= 2) return null;
        byte[] body = in.readNBytes(len);
        if (body.length < len) return null;
        if (body[0] != 20 || body[1] != (byte) utMetadataId) return null;
        int dictStart = 2;
        int dictEnd = findBencodeElementEnd(body, dictStart);
        if (dictEnd < 0) return null;
        byte[] dictBytes = new byte[dictEnd - dictStart + 1];
        System.arraycopy(body, dictStart, dictBytes, 0, dictBytes.length);
        Object decoded;
        try { decoded = BENCODE.decode(dictBytes, Type.DICTIONARY); } catch (Exception ignore) { return null; }
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

    /** 查找从 offset 开始的 bencode 元素结束位置（字典/列表/字符串/整数） */
    static int findBencodeElementEnd(byte[] buf, int offset) {
        if (offset >= buf.length) return -1;
        int i = offset;
        byte first = buf[i];
        if (first == 'd' || first == 'l') {
            i++;
            while (i < buf.length) {
                if (buf[i] == 'e') {
                    return i; // end of container
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

    static int parseElement(byte[] buf, int offset) {
        if (offset >= buf.length) return -1;
        byte b = buf[offset];
        if (b == 'i') { // integer i<digits>e
            int ePos = offset + 1;
            while (ePos < buf.length && buf[ePos] != 'e') ePos++;
            return ePos < buf.length ? ePos : -1;
        } else if (b == 'l' || b == 'd') {
            return findBencodeElementEnd(buf, offset);
        } else if (b >= '0' && b <= '9') { // string <len>:<data>
            int colon = offset;
            while (colon < buf.length && buf[colon] != ':') colon++;
            if (colon >= buf.length) return -1;
            String numStr = new String(buf, offset, colon - offset);
            int strLen;
            try { strLen = Integer.parseInt(numStr); } catch (NumberFormatException e) { return -1; }
            int end = colon + 1 + strLen - 1;
            return end < buf.length ? end : -1;
        } else {
            return -1;
        }
    }

    static int toInt(byte[] header) {
        return ((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8) | (header[3] & 0xff);
    }

    static record ExtendedHandshake(int utMetadataId, int metadataSize) {}
    static record MetadataPiece(int pieceIndex, byte[] data) {}
}
