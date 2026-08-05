package com.im.platform.idgen.algorithm;

/**
 * 雪花算法变种:41位时间戳 + 10位分片位(承载 shard_key 低位,让同一 chat_id 的消息ID
 * 落在可推导的分片上,查询时无需额外查路由表) + 12位序列号。
 *
 * workerId 之间的唯一性依赖外部分配(Nacos 临时节点序号 / 配置中心分配),
 * 这里只做本地位运算,不做分配协调。
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1735660800000L; // 2025-01-01T00:00:00Z,自定义纪元减小时间戳位数占用

    private static final long SHARD_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_SHARD = ~(-1L << SHARD_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long SHARD_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + SHARD_BITS;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public synchronized long nextId(long shardKey) {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("clock moved backwards, refusing to generate id");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        long shard = shardKey & MAX_SHARD;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (shard << SHARD_SHIFT) | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
