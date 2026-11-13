package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 收藏一张生成图（同一用户同一 URL 幂等） */
@Data
public class FavoriteAddRequest {

    @NotBlank
    @Size(max = 1024)
    private String imageUrl;

    @Size(max = 1024)
    private String thumbUrl;

    @Size(max = 32)
    private String sourceType;

    @Size(max = 64)
    private String sourceTaskId;

    @Size(max = 120)
    private String title;
}
