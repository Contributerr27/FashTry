package com.fashtry.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fashtry.backend.bean.db.RegisterGiftDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface RegisterGiftDailyMapper extends BaseMapper<RegisterGiftDaily> {

    @Insert("INSERT IGNORE INTO register_gift_daily (gift_date, used_credits) VALUES (#{giftDate}, 0)")
    int insertIgnore(@Param("giftDate") LocalDate giftDate);

    @Select("SELECT gift_date, used_credits, updated_at FROM register_gift_daily WHERE gift_date = #{giftDate} FOR UPDATE")
    RegisterGiftDaily selectForUpdate(@Param("giftDate") LocalDate giftDate);
}
