package com.fashtry.backend.bean.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserApiKeyCreatedVO {

    private Long id;
    private String name;
    /** 完整密钥，仅创建时返回一次 */
    private String apiKey;
    private LocalDateTime createdAt;
}
