-- 用户问题反馈（PC 提交 + 后台查询）
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '登录用户 ID，匿名为空',
    category VARCHAR(40) NOT NULL COMMENT '问题类型代码',
    content TEXT NOT NULL COMMENT '反馈正文',
    contact VARCHAR(200) NULL COMMENT '联系方式（匿名时建议填写）',
    page_path VARCHAR(512) NULL COMMENT '前端上报的页面路径',
    user_agent VARCHAR(512) NULL,
    client_ip VARCHAR(64) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 未处理 1 已处理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_feedback_created (created_at),
    KEY idx_user_feedback_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户问题反馈';
