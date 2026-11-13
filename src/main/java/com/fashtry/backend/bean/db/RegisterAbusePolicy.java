package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("register_abuse_policy")
public class RegisterAbusePolicy {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 同一 IP 统计窗口（分钟） */
    private Integer ipWindowMinutes;

    /** 同一 IP 从第几个新账号起不送点，0 关闭 */
    private Integer ipDenyFrom;

    /** 号段统计窗口（分钟） */
    private Integer prefixWindowMinutes;

    /** 号段前 7 位从第几个起不送点，0 关闭 */
    private Integer prefixDenyFrom;

    /** 全站统计窗口（分钟） */
    private Integer globalWindowMinutes;

    /** 全站窗口内前几个仍送点，超出不送，0 关闭 */
    private Integer globalAllow;

    /** 短信统计窗口（分钟） */
    private Integer smsWindowMinutes;

    /** 同一 IP 窗口内最多短信条数，0 关闭 */
    private Integer smsMaxPerIp;

    private LocalDateTime updatedAt;
}
