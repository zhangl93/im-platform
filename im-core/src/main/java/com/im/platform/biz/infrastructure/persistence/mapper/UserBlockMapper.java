package com.im.platform.biz.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.platform.biz.infrastructure.persistence.UserBlockPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlockPO> {
}
