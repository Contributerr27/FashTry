package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicModelVO {
    private Integer id;
    private String name;
    private String gender;
    private String region;
    private String ageGroup;
    private String style;
    private String imageUrl;
    private String thumbUrl;
}
