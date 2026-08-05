package com.im.platform.biz.domain.friend;

import com.im.platform.biz.domain.user.User;

public record FriendRecord(User user, String remark, long friendSince) {
}
