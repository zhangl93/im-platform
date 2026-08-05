package com.im.platform.biz.domain.user;

import java.util.Optional;

/**
 * 领域层只依赖此接口,不关心是 MySQL + MyBatis-Plus 还是别的存储。
 * 实现在 infrastructure.persistence.UserRepositoryImpl。
 *
 * 联系人/好友列表不在这里——那是 FriendshipApplicationService 的职责,不是 User 聚合
 * 自身的持久化关注点(早期版本把 findContacts 放在这里,只是个占位空实现,已经挪走)。
 */
public interface UserRepository {

    Optional<User> findById(long userId);

    void save(User user);
}
