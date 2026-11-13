package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicBrandVO {

    private Long id;

    private String code;

    private String brandName;

    private String brandNameEn;

    private String logoUrl;
}
