package com.im.platform.msg.store.mongo;

import org.bson.Document;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.stereotype.Component;

/**
 * Spring Data MongoDB 默认不启用注解驱动的自动建索引(spring.data.mongodb.auto-index-creation
 * 默认 false,官方文档明确不建议在生产环境打开——索引什么时候建、建成什么样应该是显式可控的,
 * 不该是启动时的隐式副作用)。跟这个项目 MySQL 那边用 Flyway 显式声明表结构是同一个原则,
 * 这里用同样"显式声明、启动时执行一次"的方式建 (chatId, messageId desc) 复合索引,
 * 对应 MongoMessageStore.pullHistory 的查询模式。
 */
@Component
public class MongoMessageIndexInitializer {

    private final MongoTemplate mongoTemplate;

    public MongoMessageIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void ensureIndexes() {
        Document keys = new Document("chatId", 1).append("messageId", -1);
        mongoTemplate.indexOps(MessageDocument.class)
                .ensureIndex(new CompoundIndexDefinition(keys).named("chat_message_idx"));
    }
}
