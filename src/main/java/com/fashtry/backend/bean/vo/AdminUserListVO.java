package com.fashtry.backend.bean.vo;

import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserWallet;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserListVO {

    /**
     * 字符串形式，避免 JSON 数字超出 JS 安全整数导致与数据库雪花 ID 不一致
     */
    private String id;
    private String nickname;
    private String mobile;
    private String email;
    private Integer status;
    private Integer userLevel;
    private String invitationCode;
    /** 注册来源：web | mp_weixin | app */
    private String registerSource;
    private LocalDateTime createdAt;
    /** 当前可用点数，无钱包记录时为 0 */
    private Integer totalCredits;
    /** 任务冻结点数 */
    private Integer frozenCredits;

    public static AdminUserListVO from(User u, UserWallet wallet) {
        return AdminUserListVO.builder()
                .id(u.getId() != null ? u.getId().toString() : null)
                .nickname(u.getNickname())
                .mobile(u.getMobile())
                .email(u.getEmail())
                .status(u.getStatus())
                .userLevel(u.getUserLevel())
                .invitationCode(u.getInvitationCode())
                .registerSource(u.getRegisterSource())
                .createdAt(u.getCreatedAt())
                .totalCredits(wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0)
                .frozenCredits(wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0)
                .build();
    }
}
