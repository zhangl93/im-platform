package com.im.platform.sync.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.sync.entity.UpdateLogEntity;
import com.im.platform.sync.mapper.UpdateLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UpdateLogService {

    private final UpdateLogMapper updateLogMapper;
    private final SeqAllocator seqAllocator;

    public UpdateLogService(UpdateLogMapper updateLogMapper, SeqAllocator seqAllocator) {
        this.updateLogMapper = updateLogMapper;
        this.seqAllocator = seqAllocator;
    }

    public void appendForUser(long userId, int eventType, byte[] payload) {
        UpdateLogEntity entity = new UpdateLogEntity();
        entity.setUserId(userId);
        entity.setSeq(seqAllocator.next(userId));
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setCreatedAt(System.currentTimeMillis());
        updateLogMapper.insert(entity);
    }

    public List<UpdateLogEntity> pullSince(long userId, long lastSeq) {
        return updateLogMapper.selectList(
                new LambdaQueryWrapper<UpdateLogEntity>()
                        .eq(UpdateLogEntity::getUserId, userId)
                        .gt(UpdateLogEntity::getSeq, lastSeq)
                        .orderByAsc(UpdateLogEntity::getSeq));
    }
}
