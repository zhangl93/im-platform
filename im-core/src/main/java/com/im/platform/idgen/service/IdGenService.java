package com.im.platform.idgen.service;

import com.im.platform.idgen.algorithm.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 biz_type 隔离发号器实例,避免不同业务类型互相抢占同一把序列号锁。
 */
@Service
public class IdGenService {

    private final ConcurrentHashMap<String, SnowflakeIdGenerator> generators = new ConcurrentHashMap<>();

    public long generateId(String bizType, long shardKey) {
        return generatorFor(bizType).nextId(shardKey);
    }

    public long[] batchGenerateId(String bizType, long shardKey, int count) {
        long[] ids = new long[count];
        SnowflakeIdGenerator generator = generatorFor(bizType);
        for (int i = 0; i < count; i++) {
            ids[i] = generator.nextId(shardKey);
        }
        return ids;
    }

    private SnowflakeIdGenerator generatorFor(String bizType) {
        return generators.computeIfAbsent(bizType, k -> new SnowflakeIdGenerator());
    }
}
