package com.im.platform.dfs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.platform.dfs.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {
}
