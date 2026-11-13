-- Gemini API Key 池：后台可配多把 Key，按策略选用，失败时换下一把
CREATE TABLE IF NOT EXISTS `gemini_api_key` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '备注名',
    `api_key` VARCHAR(256) NOT NULL COMMENT 'AI Studio API Key',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '故障转移顺序，小的优先',
    `cooldown_until` DATETIME NULL COMMENT '失败后的冷却截止时间',
    `last_error` VARCHAR(500) NULL,
    `last_error_at` DATETIME NULL,
    `last_ok_at` DATETIME NULL,
    `fail_count` INT NOT NULL DEFAULT 0,
    `success_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_gemini_api_key_enabled_sort` (`enabled`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Gemini API Key 池';

CREATE TABLE IF NOT EXISTS `gemini_key_policy` (
    `id` TINYINT NOT NULL PRIMARY KEY,
    `strategy` VARCHAR(32) NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'ROUND_ROBIN | FAILOVER | RANDOM'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Gemini Key 选用策略';

INSERT INTO `gemini_key_policy` (`id`, `strategy`)
SELECT 1, 'ROUND_ROBIN' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `gemini_key_policy` WHERE `id` = 1);
