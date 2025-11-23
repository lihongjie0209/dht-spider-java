package cn.lihongjie.dht.btclient.parser;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import com.dampcake.bencode.Bencode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RawInfoParserTest {

    private final RawInfoParser parser = new RawInfoParser();
    private final Bencode bencode = new Bencode();

    @Test
    void parsesSingleFileInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "file.bin");
        info.put("length", 100L);
        byte[] bytes = bencode.encode(info);

        RawInfoParser.RawInfoResult res = parser.parse("ih", bytes);

        assertEquals("file.bin", res.getName());
        assertEquals(100L, res.getTotalSize());
        assertEquals(1, res.getFiles().size());
        TorrentMetadata.FileInfo f = res.getFiles().get(0);
        assertEquals("file.bin", f.getPath());
        assertEquals(100L, f.getLength());
    }

    @Test
    void parsesMultiFileInfo() {
        Map<String, Object> f1 = new HashMap<>();
        f1.put("length", 10L);
        f1.put("path", List.of("a.txt"));
        Map<String, Object> f2 = new HashMap<>();
        f2.put("length", 20L);
        f2.put("path", List.of("b.txt"));

        Map<String, Object> info = new HashMap<>();
        info.put("name", "dir");
        List<Object> files = new ArrayList<>();
        files.add(f1);
        files.add(f2);
        info.put("files", files);

        byte[] bytes = bencode.encode(info);

        RawInfoParser.RawInfoResult res = parser.parse("ih", bytes);

        assertEquals("dir", res.getName());
        assertEquals(30L, res.getTotalSize());
        assertEquals(2, res.getFiles().size());
        assertEquals("a.txt", res.getFiles().get(0).getPath());
        assertEquals(10L, res.getFiles().get(0).getLength());
        assertEquals("b.txt", res.getFiles().get(1).getPath());
        assertEquals(20L, res.getFiles().get(1).getLength());
    }

    @Test
    void supportsByteStringNameAndPath() {
        Map<String, Object> f1 = new HashMap<>();
        f1.put("length", 5L);
        f1.put("path", List.of("子.txt"));

        Map<String, Object> info = new HashMap<>();
        // name as normal string; path elements as bytes
        info.put("name", "目录");
        info.put("files", List.of(f1));

        byte[] bytes = bencode.encode(info);
        RawInfoParser.RawInfoResult res = parser.parse("ih", bytes);

        assertEquals("目录", res.getName());
        assertEquals(5L, res.getTotalSize());
        assertEquals("子.txt", res.getFiles().get(0).getPath());
    }

    @Test
    void throwsOnNonDictionary() {
        byte[] listBytes = bencode.encode(List.of("not-a-dict"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("ih", listBytes));
    }
}
