-- 用户收藏的生成图（我的作品 → 收藏）
CREATE TABLE IF NOT EXISTS `user_favorites` (
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `image_url` VARCHAR(1024) NOT NULL COMMENT '图片 URL',
    `url_hash` CHAR(64) NOT NULL COMMENT 'image_url 的 SHA-256，用于唯一约束',
    `thumb_url` VARCHAR(1024) NULL COMMENT '缩略图 URL',
    `source_type` VARCHAR(32) NULL COMMENT 'tryon | image_edit | packshot | suite | retouch | portrait',
    `source_task_id` VARCHAR(64) NULL COMMENT '来源任务 ID',
    `title` VARCHAR(120) NULL COMMENT '标签，如「白底主图」',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_favorites_user_url` (`user_id`, `url_hash`),
    KEY `idx_user_favorites_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏图片';
