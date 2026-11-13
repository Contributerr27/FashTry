package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 试穿任务状态（轮询返回）
 * <p>
 * 状态码：0-排队中，1-生成中，2-已完成，3-失败
 * 完成时 resultUrl 有值，失败时 errorMsg 有值
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TryonStatusVO {

    /** 任务 ID（序列化为字符串，避免前端 JS 大整数精度丢失） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    /** 用户 ID（用于权限校验，不返回给前端） */
    @JsonIgnore
    private Long userId;

    /** 状态: 0-排队, 1-生成中, 2-完成, 3-失败 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 用户上传的模特图 URL */
    private String modelUrl;

    /** 用户上传的衣服图 URL */
    private String garmentUrl;

    /** 生成结果图 URL（完成时返回） */
    private String resultUrl;

    /** 结果图缩略图 URL（列表展示用） */
    private String thumbUrl;


    /** 失败原因（失败时返回） */
    private String errorMsg;

    /** 消耗点数 */
    private Integer consumeCredits;

    /** 用户提交时间（毫秒），用于估算排队进度 */
    private Long submittedAt;

    /** 进入生成中的时间（毫秒），用于估算生成进度 */
    private Long processingStartedAt;

    /** 本次生成耗时（毫秒），完成或失败时由库表 generation_duration_ms 带出，作进度分母参考 */
    private Long generationDurationMs;

    /** 对外为 FASHN | AI（内部实现细节不向客户端暴露） */
    private String provider;

    /** 估算进度 0–100（由服务端在查询时动态填充，写入 Redis 时不设置） */
    private Integer progress;

    /**
     * 根据状态码返回中文描述
     *
     * @param status 状态码 0/1/2/3
     * @return 状态描述
     */
    public static String statusDesc(int status) {
        return switch (status) {
            case 0 -> "排队中";
            case 1 -> "生成中";
            case 2 -> "已完成";
            case 3 -> "失败";
            default -> "未知";
        };
    }
}
