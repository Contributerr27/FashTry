ALTER TABLE `tryon_tasks`
  ADD COLUMN `credits_deducted_at` timestamp(0) NULL DEFAULT NULL COMMENT '成功扣点时间（NULL 表示未扣）' AFTER `consume_credits`;

ALTER TABLE `gemini_image_tasks`
  ADD COLUMN `credits_deducted_at` timestamp(0) NULL DEFAULT NULL COMMENT '成功扣点时间（NULL 表示未扣）' AFTER `consume_credits`;
