-- 注册风控阈值：后台可改。填 0 表示关闭对应规则。
CREATE TABLE IF NOT EXISTS `register_abuse_policy` (
    `id` TINYINT NOT NULL PRIMARY KEY,
    `ip_window_minutes` INT NOT NULL DEFAULT 10 COMMENT '同一 IP 统计窗口（分钟）',
    `ip_deny_from` INT NOT NULL DEFAULT 3 COMMENT '同一 IP 从第几个新账号起不送点，0 关闭',
    `prefix_window_minutes` INT NOT NULL DEFAULT 60 COMMENT '号段统计窗口（分钟）',
    `prefix_deny_from` INT NOT NULL DEFAULT 3 COMMENT '号段前 7 位从第几个起不送点，0 关闭',
    `global_window_minutes` INT NOT NULL DEFAULT 10 COMMENT '全站统计窗口（分钟）',
    `global_allow` INT NOT NULL DEFAULT 8 COMMENT '全站窗口内前几个仍送点，超出不送，0 关闭',
    `sms_window_minutes` INT NOT NULL DEFAULT 60 COMMENT '短信统计窗口（分钟）',
    `sms_max_per_ip` INT NOT NULL DEFAULT 5 COMMENT '同一 IP 窗口内最多短信条数，0 关闭',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注册风控阈值';

INSERT INTO `register_abuse_policy` (
    `id`, `ip_window_minutes`, `ip_deny_from`, `prefix_window_minutes`, `prefix_deny_from`,
    `global_window_minutes`, `global_allow`, `sms_window_minutes`, `sms_max_per_ip`
)
SELECT 1, 10, 3, 60, 3, 10, 8, 60, 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `register_abuse_policy` WHERE `id` = 1);
