package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gemini_key_policy")
public class GeminiKeyPolicy {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** ROUND_ROBIN | FAILOVER | RANDOM */
    private String strategy;
}
