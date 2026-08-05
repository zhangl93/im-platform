package com.im.platform.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.common.core.constant.BizType;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import com.im.platform.idgen.IdGenClient;
import org.springframework.stereotype.Service;

/**
 * 单聊会话的创建与查询。chat_id 是幂等分配的:同一对用户不管调多少次,拿到的都是同一个 chat_id
 * ——靠 (user_a, user_b) 的唯一索引 + 插入冲突后重新查一次实现,不用分布式锁。
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final IdGenClient idGenClient;

    public ConversationService(ConversationMapper conversationMapper, IdGenClient idGenClient) {
        this.conversationMapper = conversationMapper;
        this.idGenClient = idGenClient;
    }

    public long getOrCreateSingleChat(long userA, long userB) {
        long lo = Math.min(userA, userB);
        long hi = Math.max(userA, userB);

        ConversationEntity existing = findByPair(lo, hi);
        if (existing != null) {
            return existing.getChatId();
        }

        long chatId = idGenClient.generateId(BizType.CHAT_ID);
        ConversationEntity entity = new ConversationEntity();
        entity.setChatId(chatId);
        entity.setUserA(lo);
        entity.setUserB(hi);
        entity.setCreatedAt(System.currentTimeMillis());
        try {
            conversationMapper.insert(entity);
            return chatId;
        } catch (Exception e) {
            // 唯一索引冲突:并发场景下两边同时各自建了一次,谁先插进去就用谁的,
            // 重新查一遍返回已经存在的那条,不让第二个请求失败。
            ConversationEntity winner = findByPair(lo, hi);
            if (winner != null) {
                return winner.getChatId();
            }
            throw e;
        }
    }

    private ConversationEntity findByPair(long lo, long hi) {
        return conversationMapper.selectOne(
                new LambdaQueryWrapper<ConversationEntity>()
                        .eq(ConversationEntity::getUserA, lo)
                        .eq(ConversationEntity::getUserB, hi));
    }
}
