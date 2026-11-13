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
public class UserApiKeyVO {

    private Long id;
    private String name;
    /** 脱敏前缀，如 fsk_a1b2**** */
    private String keyPrefix;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
