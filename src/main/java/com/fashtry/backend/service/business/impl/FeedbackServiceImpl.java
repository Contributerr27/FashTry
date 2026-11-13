package com.fashtry.backend.service.business.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserFeedback;
import com.fashtry.backend.bean.dto.FeedbackSubmitRequest;
import com.fashtry.backend.bean.vo.AdminFeedbackListVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.UserFeedbackMapper;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.security.LoginUser;
import com.fashtry.backend.service.business.FeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserFeedbackMapper userFeedbackMapper;
    private final UserMapper userMapper;

    public FeedbackServiceImpl(UserFeedbackMapper userFeedbackMapper, UserMapper userMapper) {
        this.userFeedbackMapper = userFeedbackMapper;
        this.userMapper = userMapper;
    }

    @Override
    public long submit(FeedbackSubmitRequest body, LoginUser loginUser, String clientIp, String userAgent) {
        Long uid = loginUser != null ? loginUser.getUserId() : null;
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录后再提交反馈");
        }

        UserFeedback row = new UserFeedback();
        row.setUserId(uid);
        row.setCategory(body.getCategory().trim());
        row.setContent(body.getContent().trim());
        row.setContact(trimToNull(body.getContact()));
        row.setPagePath(trimToNull(body.getPagePath()));
        row.setClientIp(trimToNull(clientIp));
        row.setUserAgent(truncate(trimToNull(userAgent), 500));
        row.setStatus(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        userFeedbackMapper.insert(row);
        return row.getId();
    }

    @Override
    public PageResult<AdminFeedbackListVO> adminPage(int page, int pageSize, String keyword, Long userId) {
        int p = Math.max(page, 1);
        int ps = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<UserFeedback> pg = new Page<>(p, ps);
        LambdaQueryWrapper<UserFeedback> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(UserFeedback::getUserId, userId);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            boolean numeric = k.matches("^\\d{1,20}$");
            long num = 0L;
            if (numeric) {
                try {
                    num = Long.parseLong(k);
                } catch (NumberFormatException e) {
                    numeric = false;
                }
            }
            boolean idMatch = numeric;
            long idVal = num;
            w.and(q -> {
                q.like(UserFeedback::getContent, k)
                        .or().like(UserFeedback::getContact, k)
                        .or().like(UserFeedback::getPagePath, k);
                if (idMatch) {
                    q.or().eq(UserFeedback::getId, idVal).or().eq(UserFeedback::getUserId, idVal);
                }
            });
        }
        w.orderByDesc(UserFeedback::getId);
        IPage<UserFeedback> res = userFeedbackMapper.selectPage(pg, w);
        List<UserFeedback> records = res.getRecords();
        Set<Long> uids = records.stream()
                .map(UserFeedback::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = uids.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(uids).stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AdminFeedbackListVO> vos = records.stream()
                .map(r -> {
                    User u = r.getUserId() != null ? userMap.get(r.getUserId()) : null;
                    String nick = u != null ? u.getNickname() : null;
                    return AdminFeedbackListVO.builder()
                            .id(r.getId())
                            .userId(r.getUserId())
                            .userNickname(nick)
                            .category(r.getCategory())
                            .content(r.getContent())
                            .contact(r.getContact())
                            .pagePath(r.getPagePath())
                            .clientIp(r.getClientIp())
                            .userAgent(r.getUserAgent())
                            .status(r.getStatus())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();
        return PageResult.of(vos, res.getTotal(), p, ps);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
