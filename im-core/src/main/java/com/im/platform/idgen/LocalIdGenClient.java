package com.im.platform.idgen;

import com.im.platform.idgen.service.IdGenService;
import org.springframework.stereotype.Component;

/**
 * IdGenClient 的进程内实现:直接调用同一个 JVM 里的 IdGenService,没有网络往返。
 * 这是把 idgen 从独立服务并入 im-core 之后唯一需要改的地方——外部调用方(GroupRepositoryImpl/
 * MessageWriteService/FileUploadService)拿到的还是 IdGenClient 接口,代码完全不用动。
 */
@Component
public class LocalIdGenClient implements IdGenClient {

    private final IdGenService idGenService;

    public LocalIdGenClient(IdGenService idGenService) {
        this.idGenService = idGenService;
    }

    @Override
    public long generateId(String bizType) {
        return idGenService.generateId(bizType, 0L);
    }

    @Override
    public long generateId(String bizType, long shardKey) {
        return idGenService.generateId(bizType, shardKey);
    }
}
