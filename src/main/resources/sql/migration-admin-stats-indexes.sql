-- 数据概览聚合用；索引已存在时会报错，可忽略
ALTER TABLE `users` ADD INDEX `idx_users_created_at` (`created_at`);
ALTER TABLE `tryon_tasks` ADD INDEX `idx_tryon_created_at` (`created_at`);
ALTER TABLE `tryon_tasks` ADD INDEX `idx_tryon_status` (`status`);
ALTER TABLE `gemini_image_tasks` ADD INDEX `idx_gemini_type_created` (`task_type`, `created_at`);
ALTER TABLE `orders` ADD INDEX `idx_orders_status_paid_at` (`status`, `paid_at`);
