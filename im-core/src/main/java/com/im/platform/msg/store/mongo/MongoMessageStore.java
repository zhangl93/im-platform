package com.im.platform.msg.store.mongo;

import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.store.MessageStore;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** 消息存储固定用 MongoDB,不再有 MySQL 分表这条路可选——按 chat_id 水平扩展这件事交给
 * Mongo 自己的分片集群(shard key + mongos)原生处理,不在应用层重新发明"选表逻辑"。
 * 用户/群组/会话/已读游标等其它数据仍然固定在 MySQL,这个类只管消息这一块。 */
@Component
public class MongoMessageStore implements MessageStore {

    private final MongoTemplate mongoTemplate;

    public MongoMessageStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void insert(MessageEntity entity) {
        mongoTemplate.insert(toDocument(entity));
    }

    @Override
    public MessageEntity findById(long chatId, long messageId) {
        MessageDocument doc = mongoTemplate.findById(messageId, MessageDocument.class);
        return doc == null ? null : toEntity(doc);
    }

    @Override
    public List<MessageEntity> pullHistory(long chatId, long beforeMessageId, int limit) {
        Criteria criteria = Criteria.where("chatId").is(chatId);
        if (beforeMessageId > 0) {
            criteria = criteria.and("messageId").lt(beforeMessageId);
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "messageId"))
                .limit(limit);
        return mongoTemplate.find(query, MessageDocument.class).stream()
                .map(MongoMessageStore::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public long countAfter(long chatId, long afterMessageId) {
        Criteria criteria = Criteria.where("chatId").is(chatId).and("messageId").gt(afterMessageId);
        return mongoTemplate.count(Query.query(criteria), MessageDocument.class);
    }

    private static MessageDocument toDocument(MessageEntity entity) {
        MessageDocument doc = new MessageDocument();
        doc.setMessageId(entity.getMessageId());
        doc.setChatId(entity.getChatId());
        doc.setSenderId(entity.getSenderId());
        doc.setContent(entity.getContent());
        doc.setMsgType(entity.getMsgType());
        doc.setServerTime(entity.getServerTime());
        doc.setClientMsgId(entity.getClientMsgId());
        doc.setEx(entity.getEx());
        return doc;
    }

    private static MessageEntity toEntity(MessageDocument doc) {
        MessageEntity entity = new MessageEntity();
        entity.setMessageId(doc.getMessageId());
        entity.setChatId(doc.getChatId());
        entity.setSenderId(doc.getSenderId());
        entity.setContent(doc.getContent());
        entity.setMsgType(doc.getMsgType());
        entity.setServerTime(doc.getServerTime());
        entity.setClientMsgId(doc.getClientMsgId());
        entity.setEx(doc.getEx());
        return entity;
    }
}
