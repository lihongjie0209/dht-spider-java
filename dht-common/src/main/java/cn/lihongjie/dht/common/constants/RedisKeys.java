package cn.lihongjie.dht.common.constants;

/**
 * Redis键常量
 */
public class RedisKeys {
    
    /**
     * InfoHash去重集合前缀
     */
    public static final String INFOHASH_DEDUP_PREFIX = "dht:dedup:infohash:";
    
    /**
     * InfoHash处理中集合
     */
    public static final String INFOHASH_PROCESSING = "dht:processing:infohash";
    
    /**
     * 统计信息前缀
     */
    public static final String STATS_PREFIX = "dht:stats:";
    
    /**
     * 获取InfoHash去重键
     */
    public static String getInfoHashDedupKey(String infoHash) {
        return INFOHASH_DEDUP_PREFIX + infoHash;
    }
    
    /**
     * 获取日期统计键
     */
    public static String getStatsKey(String date) {
        return STATS_PREFIX + date;
    }
    
    private RedisKeys() {
        // 工具类，禁止实例化
    }
}
