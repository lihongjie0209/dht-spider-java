package cn.lihongjie.dht.common.constants;

/**
 * Kafka/RedPanda主题常量
 */
public class KafkaTopics {
    
    /**
     * InfoHash发现主题：MLDHT -> BT Client
     */
    public static final String INFOHASH_DISCOVERED = "dht.infohash.discovered";
    
    /**
     * 元数据下载主题：BT Client -> Metadata Service
     */
    public static final String METADATA_FETCHED = "dht.metadata.fetched";
    
    /**
     * 下载失败主题：BT Client记录失败的InfoHash
     */
    public static final String METADATA_FAILED = "dht.metadata.failed";
    
    private KafkaTopics() {
        // 工具类，禁止实例化
    }
}
