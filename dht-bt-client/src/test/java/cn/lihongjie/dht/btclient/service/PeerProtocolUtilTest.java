package cn.lihongjie.dht.btclient.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PeerProtocolUtilTest {

    @Test
    @DisplayName("Handshake structure and offsets are correct")
    void testBuildHandshakeStructure() {
        byte[] infoHash = HexFormat.of().parseHex("00112233445566778899AABBCCDDEEFF00112233".substring(0, 40)); // ensure 20 bytes
        byte[] peerId = PeerProtocolUtil.generatePeerId();
        byte[] reserved = new byte[8];
        reserved[5] |= 0x10; // extended protocol flag

        byte[] hs = PeerProtocolUtil.buildHandshake(infoHash, peerId, reserved);
        assertEquals(68, hs.length, "Handshake length must be 68");
        assertEquals(19, hs[0], "pstrlen should be 19");
        assertEquals("BitTorrent protocol", new String(hs, 1, 19));

        // Reserved bytes
        byte[] reservedOut = new byte[8];
        System.arraycopy(hs, 20, reservedOut, 0, 8);
        assertArrayEquals(reserved, reservedOut, "Reserved bytes mismatch");

        // InfoHash
        byte[] infoHashOut = new byte[20];
        System.arraycopy(hs, 28, infoHashOut, 0, 20);
        assertArrayEquals(infoHash, infoHashOut, "InfoHash not placed correctly");

        // PeerId
        byte[] peerIdOut = new byte[20];
        System.arraycopy(hs, 48, peerIdOut, 0, 20);
        assertArrayEquals(peerId, peerIdOut, "PeerId not placed correctly");
    }

    @Test
    @DisplayName("Extended handshake parses ut_metadata id and metadata size")
    void testReadExtendedHandshakeValid() throws Exception {
        // Build correct bencoded dictionary via library to avoid manual errors
        com.dampcake.bencode.Bencode bencode = new com.dampcake.bencode.Bencode();
        java.util.Map<String,Object> mMap = new java.util.HashMap<>();
        mMap.put("ut_metadata", 3L);
        java.util.Map<String,Object> root = new java.util.HashMap<>();
        root.put("m", mMap);
        root.put("metadata_size", 12345L);
        byte[] payload = bencode.encode(root);
        byte[] body = new byte[2 + payload.length];
        body[0] = 20; // message id ext protocol
        body[1] = 0;  // ext id 0 (handshake)
        System.arraycopy(payload, 0, body, 2, payload.length);
        int len = body.length;
        byte[] header = new byte[]{(byte)(len>>>24),(byte)(len>>>16),(byte)(len>>>8),(byte)len};
        byte[] full = new byte[header.length + body.length];
        System.arraycopy(header,0,full,0,header.length);
        System.arraycopy(body,0,full,4,body.length);
        PeerProtocolUtil.ExtendedHandshake eh = PeerProtocolUtil.readExtendedHandshake(new ByteArrayInputStream(full));
        assertNotNull(eh);
        assertEquals(3, eh.utMetadataId());
        assertEquals(12345, eh.metadataSize());
    }

    @Test
    @DisplayName("Extended handshake missing ut_metadata yields utMetadataId=-1")
    void testReadExtendedHandshakeMissingUtMetadata() throws Exception {
        com.dampcake.bencode.Bencode bencode = new com.dampcake.bencode.Bencode();
        java.util.Map<String,Object> emptyM = new java.util.HashMap<>();
        java.util.Map<String,Object> root = new java.util.HashMap<>();
        root.put("m", emptyM);
        root.put("metadata_size", 100L);
        byte[] payload = bencode.encode(root);
        byte[] body = new byte[2 + payload.length];
        body[0] = 20; body[1] = 0;
        System.arraycopy(payload, 0, body, 2, payload.length);
        int len = body.length;
        byte[] header = new byte[]{(byte)(len>>>24),(byte)(len>>>16),(byte)(len>>>8),(byte)len};
        byte[] full = new byte[4 + body.length];
        System.arraycopy(header,0,full,0,4);
        System.arraycopy(body,0,full,4,body.length);
        PeerProtocolUtil.ExtendedHandshake eh = PeerProtocolUtil.readExtendedHandshake(new ByteArrayInputStream(full));
        assertNotNull(eh);
        assertEquals(-1, eh.utMetadataId());
        assertEquals(100, eh.metadataSize());
    }

    @Test
    @DisplayName("Extended handshake wrong ext id returns null")
    void testReadExtendedHandshakeWrongExtId() throws Exception {
        String dict = "d1:md13:metadata_sizei100ee";
        byte[] payload = dict.getBytes();
        byte[] body = new byte[2 + payload.length];
        body[0] = 20; body[1] = 1; // ext id != 0
        System.arraycopy(payload, 0, body, 2, payload.length);
        int len = body.length;
        byte[] header = new byte[]{(byte)(len>>>24),(byte)(len>>>16),(byte)(len>>>8),(byte)len};
        byte[] full = new byte[4 + body.length];
        System.arraycopy(header,0,full,0,4);
        System.arraycopy(body,0,full,4,body.length);
        PeerProtocolUtil.ExtendedHandshake eh = PeerProtocolUtil.readExtendedHandshake(new ByteArrayInputStream(full));
        assertNull(eh);
    }

    @Test
    @DisplayName("Metadata piece parses piece index and data")
    void testReadMetadataPieceValid() throws Exception {
        byte utId = 3;
        byte[] dict = "d5:piecei0e8:msg_typei1ee".getBytes(); // piece=0 msg_type=1
        byte[] metaData = new byte[]{1,2,3,4,5};
        int len = 2 + dict.length + metaData.length;
        byte[] body = new byte[len];
        body[0] = 20; body[1] = utId;
        System.arraycopy(dict,0,body,2,dict.length);
        System.arraycopy(metaData,0,body,2+dict.length,metaData.length);
        byte[] header = new byte[]{(byte)(len>>>24),(byte)(len>>>16),(byte)(len>>>8),(byte)len};
        byte[] full = new byte[4 + len];
        System.arraycopy(header,0,full,0,4);
        System.arraycopy(body,0,full,4,len);
        PeerProtocolUtil.MetadataPiece piece = PeerProtocolUtil.readMetadataPiece(new ByteArrayInputStream(full), utId);
        assertNotNull(piece);
        assertEquals(0, piece.pieceIndex());
        assertArrayEquals(metaData, piece.data());
    }

    @Test
    @DisplayName("Metadata piece wrong msg_type returns null")
    void testReadMetadataPieceWrongMsgType() throws Exception {
        byte utId = 4;
        byte[] dict = "d5:piecei0e8:msg_typei0ee".getBytes(); // msg_type=0 should be rejected
        byte[] metaData = new byte[]{9,9};
        int len = 2 + dict.length + metaData.length;
        byte[] body = new byte[len];
        body[0] = 20; body[1] = utId;
        System.arraycopy(dict,0,body,2,dict.length);
        System.arraycopy(metaData,0,body,2+dict.length,metaData.length);
        byte[] header = new byte[]{(byte)(len>>>24),(byte)(len>>>16),(byte)(len>>>8),(byte)len};
        byte[] full = new byte[4 + len];
        System.arraycopy(header,0,full,0,4);
        System.arraycopy(body,0,full,4,len);
        PeerProtocolUtil.MetadataPiece piece = PeerProtocolUtil.readMetadataPiece(new ByteArrayInputStream(full), utId);
        assertNull(piece);
    }
}
