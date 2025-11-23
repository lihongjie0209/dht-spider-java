package cn.lihongjie.dht.btclient.parser;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import cn.lihongjie.dht.common.model.TorrentMetadata;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser for the raw "info" dictionary bytes acquired via ut_metadata.
 * Extracts name, total size and file list (multi-file or single-file form).
 */
public class RawInfoParser {

    // Use UTF-8 decoding so byte strings are returned as Java Strings
    private final Bencode bencode = new Bencode(StandardCharsets.UTF_8);

    /**
     * Result structure containing parsed fields.
     */
    public static class RawInfoResult {
        private final String name; // may be null
        private final long totalSize;
        private final List<TorrentMetadata.FileInfo> files;

        public RawInfoResult(String name, long totalSize, List<TorrentMetadata.FileInfo> files) {
            this.name = name;
            this.totalSize = totalSize;
            this.files = files;
        }

        public String getName() { return name; }
        public long getTotalSize() { return totalSize; }
        public List<TorrentMetadata.FileInfo> getFiles() { return files; }
    }

    /**
     * Parse raw bencoded info dictionary bytes. Returns a RawInfoResult.
     * @throws IllegalArgumentException if the bytes cannot be parsed into a dictionary.
     */
    public RawInfoResult parse(String infoHash, byte[] rawInfoBytes) {
        Object decoded;
        try {
            decoded = bencode.decode(rawInfoBytes, Type.DICTIONARY);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to decode info dictionary for infoHash=" + infoHash, e);
        }
        if (!(decoded instanceof Map)) {
            throw new IllegalArgumentException("Info dictionary is not a map for infoHash=" + infoHash);
        }
        @SuppressWarnings("unchecked") Map<String, Object> info = (Map<String, Object>) decoded;
        String name = extractString(info.get("name"));
        long totalSize = 0L;
        List<TorrentMetadata.FileInfo> files;
        Object filesObj = info.get("files");
        if (filesObj instanceof List) { // multi-file mode
            @SuppressWarnings("unchecked") List<Object> list = (List<Object>) filesObj;
            files = new ArrayList<>();
            for (Object f : list) {
                if (f instanceof Map) {
                    @SuppressWarnings("unchecked") Map<String, Object> fm = (Map<String, Object>) f;
                    long length = extractNumber(fm.get("length"));
                    totalSize += length;
                    List<String> pathElems = new ArrayList<>();
                    Object pathObj = fm.get("path");
                    if (pathObj instanceof List) {
                        for (Object pe : (List<?>) pathObj) {
                            pathElems.add(extractString(pe));
                        }
                    }
                    files.add(TorrentMetadata.FileInfo.builder()
                            .path(String.join("/", pathElems))
                            .length(length)
                            .build());
                }
            }
        } else { // single-file mode
            long length = extractNumber(info.get("length"));
            totalSize = length;
            files = List.of(TorrentMetadata.FileInfo.builder()
                    .path(name != null ? name : infoHash)
                    .length(length)
                    .build());
        }
        return new RawInfoResult(name, totalSize, files);
    }

    private String extractString(Object v) {
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        if (v instanceof byte[]) return new String((byte[]) v, StandardCharsets.UTF_8);
        if (v.getClass().isArray() && v.getClass().getComponentType() == byte.class) {
            return new String((byte[]) v, StandardCharsets.UTF_8);
        }
        return v.toString();
    }

    private long extractNumber(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}
