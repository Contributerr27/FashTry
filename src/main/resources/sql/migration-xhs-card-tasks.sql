-- 小红书图文卡片生成历史
CREATE TABLE IF NOT EXISTS `xhs_card_tasks` (
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `topic` VARCHAR(120) NOT NULL COMMENT '主题',
    `selling_points` VARCHAR(400) NULL COMMENT '卖点',
    `audience` VARCHAR(120) NULL COMMENT '受众',
    `brand_name` VARCHAR(40) NULL COMMENT '底部账号名',
    `template_style` VARCHAR(32) NOT NULL DEFAULT 'clipnote' COMMENT '模板样式',
    `background_color` VARCHAR(16) NULL COMMENT '自定义背景色 #RRGGBB',
    `text_color` VARCHAR(16) NULL COMMENT '自定义文字色 #RRGGBB',
    `reference_image_url` VARCHAR(512) NULL COMMENT '用户上传的参考模板图',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1执行中 2成功 3失败',
    `consume_credits` INT NOT NULL DEFAULT 0 COMMENT '扣点',
    `result_json` MEDIUMTEXT NULL COMMENT '成功时的文案 JSON',
    `error_msg` VARCHAR(512) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `completed_at` DATETIME NULL,
    KEY `idx_xhs_card_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小红书图文卡片任务';

-- 若表已存在，执行：
-- ALTER TABLE `xhs_card_tasks` ADD COLUMN `reference_image_url` VARCHAR(512) NULL COMMENT '用户上传的参考模板图' AFTER `text_color`;
