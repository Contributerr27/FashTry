-- 注册赠送：每人额度 + 每天总额度，后台可改
CREATE TABLE IF NOT EXISTS `register_gift_policy` (
    `id` TINYINT NOT NULL PRIMARY KEY,
    `per_user_credits` INT NOT NULL DEFAULT 1 COMMENT '每人注册赠送点数',
    `daily_total_credits` INT NOT NULL DEFAULT 20 COMMENT '每天赠送总额度',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注册赠送额度策略';

INSERT INTO `register_gift_policy` (`id`, `per_user_credits`, `daily_total_credits`)
SELECT 1, 1, 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `register_gift_policy` WHERE `id` = 1);

CREATE TABLE IF NOT EXISTS `register_gift_daily` (
    `gift_date` DATE NOT NULL PRIMARY KEY COMMENT '按 Asia/Shanghai 切日',
    `used_credits` INT NOT NULL DEFAULT 0 COMMENT '当日已赠送点数',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注册赠送每日用量';
