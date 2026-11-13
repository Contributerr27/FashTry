package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量查询哪些 URL 已收藏 */
@Data
public class FavoriteCheckRequest {

    @NotNull
    @Size(max = 200)
    private List<String> imageUrls;
}
