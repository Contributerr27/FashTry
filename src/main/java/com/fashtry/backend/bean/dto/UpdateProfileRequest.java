package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称最多50字")
    private String nickname;

    @Size(max = 512, message = "头像地址过长")
    private String avatarUrl;
}
