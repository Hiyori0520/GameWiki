package com.gamekb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gamekb.entity.GameInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameInfoMapper extends BaseMapper<GameInfo> {
}