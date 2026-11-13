package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Product to Model 任务状态（轮询返回）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductToModelStatusVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    @JsonIgnore
    private Long userId;

    private Integer status;
    private String statusDesc;

    private String productUrl;
    private String modelUrl;

    /** 首张结果图 URL（完成时返回） */
    private String resultUrl;

    /** 多图时全部结果 URL 列表 */
    private List<String> resultUrls;

    private String thumbUrl;
    private String errorMsg;
    private Integer consumeCredits;

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
