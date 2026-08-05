package com.im.platform.biz.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.platform.biz.infrastructure.persistence.GroupMemberPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMemberPO> {
}
