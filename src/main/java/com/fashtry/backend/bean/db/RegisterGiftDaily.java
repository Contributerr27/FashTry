package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("register_gift_daily")
public class RegisterGiftDaily {

    @TableId(value = "gift_date", type = IdType.INPUT)
    private LocalDate giftDate;

    private Integer usedCredits;

    private LocalDateTime updatedAt;
}
