-- 用户开放 API 密钥（哈希存储，明文仅在创建时返回一次）
CREATE TABLE IF NOT EXISTS `user_api_keys` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '所属用户',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '密钥备注名',
  `key_prefix` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密钥前缀，用于列表展示',
  `key_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SHA-256 十六进制',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-启用 0-已吊销',
  `last_used_at` timestamp(0) NULL DEFAULT NULL COMMENT '最近调用时间',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_key_hash` (`key_hash`) USING BTREE,
  KEY `idx_user_enabled` (`user_id`, `enabled`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户开放 API 密钥';
