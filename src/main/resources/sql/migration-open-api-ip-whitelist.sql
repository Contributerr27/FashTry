-- 开放 API：按来源 IP 放行（支持 IPv4 精确或 CIDR，如 203.0.113.0/24）
CREATE TABLE IF NOT EXISTS `open_api_ip_rules` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ip_rule` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IPv4 地址或 CIDR，如 1.2.3.4 或 10.0.0.0/24',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_enabled` (`enabled`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '开放 API IP 白名单';
