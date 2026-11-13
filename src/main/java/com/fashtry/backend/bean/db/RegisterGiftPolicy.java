package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("register_gift_policy")
public class RegisterGiftPolicy {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 每人注册赠送点数 */
    private Integer perUserCredits;

    /** 每天赠送总额度 */
    private Integer dailyTotalCredits;

    private LocalDateTime updatedAt;
}
