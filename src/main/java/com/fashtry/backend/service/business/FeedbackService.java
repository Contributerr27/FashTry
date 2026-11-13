package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.dto.FeedbackSubmitRequest;
import com.fashtry.backend.bean.vo.AdminFeedbackListVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.security.LoginUser;

public interface FeedbackService {

    long submit(FeedbackSubmitRequest body, LoginUser loginUser, String clientIp, String userAgent);

    PageResult<AdminFeedbackListVO> adminPage(int page, int pageSize, String keyword, Long userId);
}
