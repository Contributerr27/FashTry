package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
