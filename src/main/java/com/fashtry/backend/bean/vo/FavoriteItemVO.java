package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteItemVO {

    private Long id;
    private String imageUrl;
    private String thumbUrl;
    private String sourceType;
    private String sourceTaskId;
    private String title;
    private LocalDateTime createdAt;
}
