package com.im.platform.biz.domain.user;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;

import java.util.HashSet;
import java.util.Set;

/**
 * User 聚合根。封装资料变更、拉黑关系这些必须保持一致的不变量,
 * 应用服务只负责编排(加载 -&gt; 调用聚合方法 -&gt; 保存 -&gt; 按需触发回调),不重复业务判断。
 * 通知外部(回调)是应用层的事,聚合根本身不感知。
 */
public class User {

    private final long userId;
    private String nickname;
    private String avatar;
    private UserStatus status;
    private final Set<Long> blockedUserIds;
    /** 扩展字段,业务自定义属性,平台不解析/不校验内容,只负责原样存取。 */
    private String ex;

    public User(long userId, String nickname, String avatar, UserStatus status, Set<Long> blockedUserIds, String ex) {
        this.userId = userId;
        this.nickname = nickname;
        this.avatar = avatar;
        this.status = status;
        this.blockedUserIds = blockedUserIds == null ? new HashSet<>() : blockedUserIds;
        this.ex = ex;
    }

    public void updateProfile(String nickname, String avatar) {
        if (status == UserStatus.DEACTIVATED) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "user deactivated, cannot update profile");
        }
        this.nickname = nickname;
        this.avatar = avatar;
    }

    public void block(long targetUserId) {
        if (targetUserId == this.userId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "cannot block self");
        }
        blockedUserIds.add(targetUserId);
    }

    public boolean hasBlocked(long targetUserId) {
        return blockedUserIds.contains(targetUserId);
    }

    /** 平台不理解 ex 里装的是什么,不做任何校验,业务自己保证格式(通常是 JSON 字符串)。 */
    public void setEx(String ex) {
        this.ex = ex;
    }

    public long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<Long> getBlockedUserIds() {
        return blockedUserIds;
    }

    public String getEx() {
        return ex;
    }
}
