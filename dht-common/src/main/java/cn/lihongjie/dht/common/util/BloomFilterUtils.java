package cn.lihongjie.dht.common.util;

/**
 * Bloom Filter常量定义
 * 使用Redis原生RedisBloom模块（BF.MADD / BF.MEXISTS命令）
 * <p>
 * 参数设计：
 * - 预期元素数量(n): 100,000,000 (1亿)
 * - 误判率(p): 0.01 (1%)
 * - Redis会自动计算并分配合适的bit数组大小和hash函数数量
 * <p>
 * Redis命令：
 * - BF.RESERVE {key} {error_rate} {capacity} - 创建Bloom Filter
 * - BF.MADD {key} {item...} - 批量添加元素
 * - BF.MEXISTS {key} {item...} - 批量检查元素是否存在
 */
public class BloomFilterUtils {
    
    /**
     * Redis Bloom Filter的key名称
     */
    public static final String BLOOM_KEY = "dht:bloom:infohash";
    
    /**
     * 误判率：1%
     */
    public static final double ERROR_RATE = 0.01;
    
    /**
     * 预期容量：1亿
     */
    public static final long CAPACITY = 100_000_000L;
    
    private BloomFilterUtils() {
        // 工具类，禁止实例化
    }
}
