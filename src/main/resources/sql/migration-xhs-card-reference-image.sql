-- 小红书卡片：补充参考模板图字段（表已存在时执行；若列已存在会报错可忽略）
ALTER TABLE `xhs_card_tasks`
    ADD COLUMN `reference_image_url` VARCHAR(512) NULL COMMENT '用户上传的参考模板图' AFTER `text_color`;
