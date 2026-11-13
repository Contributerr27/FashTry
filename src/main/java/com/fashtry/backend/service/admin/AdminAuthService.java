package com.fashtry.backend.service.admin;

import com.fashtry.backend.bean.dto.AdminChangePasswordRequest;
import com.fashtry.backend.bean.dto.AdminLoginRequest;
import com.fashtry.backend.bean.vo.AdminLoginVO;
import com.fashtry.backend.security.LoginUser;

public interface AdminAuthService {

    AdminLoginVO login(AdminLoginRequest request);

    void logout(String bearerToken);

    void changePassword(LoginUser loginUser, AdminChangePasswordRequest request);
}
