package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
