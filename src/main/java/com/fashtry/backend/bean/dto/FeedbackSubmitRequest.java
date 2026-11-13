package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackSubmitRequest {

    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "^(tryon|local_edit|model_shot|clothing_suite|billing|account|other)$",
            message = "category 无效")
    private String category;

    @NotBlank
    @Size(min = 1, max = 4000)
    private String content;

    @Size(max = 200)
    private String contact;

    @Size(max = 512)
    private String pagePath;
}
