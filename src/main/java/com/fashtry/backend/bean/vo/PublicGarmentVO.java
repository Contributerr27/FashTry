package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicGarmentVO {
    private Integer id;
    private String name;
    private String nameEn;
    private String sceneCategory;
    private String gender;
    private String garmentType;
    private String imageUrl;
    private String thumbUrl;
}
