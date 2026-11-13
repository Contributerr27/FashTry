package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product to Model 提交响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductToModelSubmitVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;
}
