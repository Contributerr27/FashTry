package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.RegisterGiftPolicy;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RegisterGiftPolicyMapper extends BaseMapper<RegisterGiftPolicy> {

    @Insert("INSERT IGNORE INTO register_gift_policy (id, per_user_credits, daily_total_credits) "
            + "VALUES (1, #{perUserCredits}, #{dailyTotalCredits})")
    int insertIgnore(@Param("perUserCredits") int perUserCredits, @Param("dailyTotalCredits") int dailyTotalCredits);
}
