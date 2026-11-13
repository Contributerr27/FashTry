package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminProfileVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminMeController {

    @GetMapping("/me")
    public Result<AdminProfileVO> me(@AuthenticationPrincipal LoginUser user) {
        if (user == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        AdminProfileVO vo = AdminProfileVO.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .build();
        return Result.success(vo);
    }
}
