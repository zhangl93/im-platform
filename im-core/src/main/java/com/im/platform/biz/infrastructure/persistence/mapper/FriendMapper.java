package com.im.platform.biz.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.platform.biz.infrastructure.persistence.FriendPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 联合主键 (user_id, friend_id),没有 @TableId,跟 UserBlockPO/GroupMemberPO 一样——
 * 全部用 LambdaQueryWrapper 操作,不依赖 BaseMapper 的 xxById 系方法,启动时的
 * "Can not find table primary key" 警告是预期的,不影响这里的用法。
 */
@Mapper
public interface FriendMapper extends BaseMapper<FriendPO> {
}
