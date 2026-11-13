-- ============================================================
-- AI Clothing Swap / FashTry — database schema (structure only)
-- Target: MySQL 5.7+ / 8.0
--
-- Usage:
--   CREATE DATABASE fash_try DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   mysql -u <user> -p fash_try < schema.sql
--
-- Notes:
--   * No user, order, brand or task data is included.
--   * Run once against an empty database; DDL statements are not idempotent.
--   * Replaying the migration section may raise "Duplicate key name" or
--     "Duplicate column name" errors. They mean the object already exists and
--     can be ignored.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for admin_users
-- ----------------------------
DROP TABLE IF EXISTS `admin_users`;
CREATE TABLE `admin_users`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录名',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt 密码',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '后台管理员' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '系统订单号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `package_id` int(11) UNSIGNED NULL DEFAULT NULL COMMENT '关联充值套餐ID',
  `amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `credits_bought` int(11) NULL DEFAULT 0 COMMENT '购买的点数',
  `pay_channel` enum('wxpay','alipay','paypal','stripe','usdt_tron','usdt_bsc','lemonsqueezy') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付渠道',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '状态: 0-待支付, 1-已支付, 2-超时关闭, 3-退款, 4-支付失败',
  `third_party_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '外部支付单号(渠道流水号)',
  `transaction_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '交易ID',
  `code_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '支付二维码Url',
  `open_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信openid',
  `buyer_user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付宝用户id',
  `buyer_logon_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付宝买家账号',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单详情',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `paid_at` timestamp(0) NULL DEFAULT NULL COMMENT '支付完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_third_party_no`(`third_party_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 69 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单支付表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for product_to_model_tasks
-- ----------------------------
DROP TABLE IF EXISTS `product_to_model_tasks`;
CREATE TABLE `product_to_model_tasks`  (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '分布式唯一ID（雪花ID），提交时由 SnowflakeIdGenerator 生成',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户ID，任务归属',
  `prediction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'FASHN API 返回的任务ID，用于轮询 /v1/status/{id}',
  `product_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品图片URL（服装/鞋子/帽子/珠宝/包包/配饰等），对应 API 的 product_image',
  `model_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模特图片URL，Try-on 模式必填；Product Only 模式为空则系统自动生成模特',
  `image_prompt_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '参考图URL，用于控制姿势、环境和光线，对应 image_prompt',
  `face_reference_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '脸部参考图URL，指定生成模特的脸部；使用则消耗 4 积分',
  `background_reference_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '背景参考图URL',
  `face_reference_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '脸部匹配模式: match_reference-优先匹配参考脸, match_base-优先匹配 prompt',
  `prompt` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文本描述：人物外观、穿搭或背景',
  `aspect_ratio` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '输出比例: 1:1, 3:4, 4:3, 9:16, 16:9, 2:3, 3:2, 4:5, 5:4',
  `resolution` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分辨率: 1k-标准, 4k-更高细节',
  `seed` int(11) NULL DEFAULT NULL COMMENT '随机种子，用于复现结果',
  `num_images` tinyint(4) NULL DEFAULT 1 COMMENT '生成图片数量，1-4',
  `output_format` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'png' COMMENT '输出格式: png, jpeg',
  `result_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '首张结果图 URL（FASHN 原始 CDN 链接），单图时即唯一结果',
  `result_urls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '全部结果图 URL 列表，JSON 数组，num_images>1 时存储多张',
  `result_cos_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '首张结果图转存到腾讯云 COS 后的 URL（优先使用）',
  `result_cos_urls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '全部结果图 COS URL 列表，JSON 数组',
  `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '首张结果图缩略图 URL',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '任务状态: 0-排队中, 1-生成中, 2-完成, 3-失败',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '失败时的错误描述',
  `consume_credits` int(11) NOT NULL DEFAULT 1 COMMENT '本次消耗点数：普通 1 积分，使用 face_reference 时 4 积分',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
  `completed_at` timestamp(0) NULL DEFAULT NULL COMMENT '任务完成或失败时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prediction_id`(`prediction_id`) USING BTREE,
  INDEX `idx_user_created`(`user_id`, `created_at`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品图转模特图任务表（Product to Model API）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for public_garments
-- ----------------------------
DROP TABLE IF EXISTS `public_garments`;
CREATE TABLE `public_garments`  (
  `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称，如：缎面鱼尾婚纱',
  `name_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '英文名称',
  `scene_category` enum('wedding','workplace','campus','gu_feng','trendy','western') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '场景分类：婚礼/职场/校园/古风/潮流/欧美',
  `gender` enum('male','female','unisex') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'unisex' COMMENT '适用性别',
  `garment_type` enum('tops','bottoms','one-pieces') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服装结构，与 tryon_tasks.category 一致，便于试衣参数',
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '高清原图 URL（试衣输入）',
  `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '列表缩略图',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '后台备注，不对 C 端展示',
  `purchase_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '购买链接（可选）',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否上架',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '同分类内排序，越小越靠前',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scene_active_sort`(`scene_category`, `is_active`, `sort_order`) USING BTREE,
  INDEX `idx_scene_type`(`scene_category`, `garment_type`) USING BTREE,
  INDEX `idx_gender_active`(`gender`, `is_active`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 42 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '公共服装库' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for public_models
-- ----------------------------
DROP TABLE IF EXISTS `public_models`;
CREATE TABLE `public_models`  (
  `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模特代号,如: Asian_Female_01',
  `gender` enum('male','female','unisex') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'female' COMMENT '性别',
  `region` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'Asian' COMMENT '地区/肤色: Asian, Caucasian, African, Latino',
  `age_group` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'Adult' COMMENT '年龄段: Teen, Adult, Senior',
  `style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'Casual' COMMENT '风格标签: Casual, Formal, Sport, Streetwear',
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '高清原图(用于Fashn.AI输入)',
  `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '缩略图(前端展示)',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_filter`(`gender`, `region`, `style`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模特表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for recharge_packages
-- ----------------------------
DROP TABLE IF EXISTS `recharge_packages`;
CREATE TABLE `recharge_packages`  (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '套餐代号: trial,standard,pro,master,enterprise',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '套餐名称',
  `name_en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '套餐英文名称',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '售价(元)，企业版为NULL表示议价',
  `price_usd` decimal(10, 2) NULL DEFAULT NULL COMMENT '售价(美元)，PayPal 使用',
  `credits` int(11) NOT NULL COMMENT '获得点数',
  `unit_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '单价(元/次)',
  `cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '总成本(元)',
  `is_first_purchase_only` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否仅首充: 0-否, 1-是',
  `expiration_days` int(11) NULL DEFAULT NULL COMMENT '点数有效天数，NULL表示永久有效',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '生效开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '生效结束时间',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `strategy_tag` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '策略定位描述',
  `strategy_tag_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '套餐英文介绍',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '充值套餐表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for referral_reward_configs
-- ----------------------------
DROP TABLE IF EXISTS `referral_reward_configs`;
CREATE TABLE `referral_reward_configs`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reward_type` tinyint(4) NOT NULL COMMENT '1-固定点数, 2-充值比例',
  `reward_value` decimal(10, 2) NOT NULL COMMENT '返利数值(点数或百分比)',
  `is_active` tinyint(1) NULL DEFAULT 1,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '策略描述: 如 邀请人获充值金额10%的点数',
  `description_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '英文策略描述',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '返利配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for tryon_tasks
-- ----------------------------
DROP TABLE IF EXISTS `tryon_tasks`;
CREATE TABLE `tryon_tasks`  (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '分布式唯一ID',
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `prediction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Fashn.AI返回的任务ID',
  `category` enum('tops','bottoms','one-pieces') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '服装类别',
  `model_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始模特图URL',
  `garment_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '衣服图URL',
  `result_cos_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '腾讯云URL',
  `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '缩略图URL(列表展示用)',
  `result_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成结果URL',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '0-排队, 1-生成中, 2-完成, 3-失败',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '失败原因描述',
  `consume_credits` int(11) NULL DEFAULT 1 COMMENT '本次消耗点数',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` timestamp(0) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `prediction_id`(`prediction_id`) USING BTREE,(1, '无袖条纹系带铅笔裙', 'Sleeveless Pinstriped Tie-Waist Sheath Dress', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/8fefb4ff21f54eb398c8d0f499953c8c.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/d59b3d72b4664c149971ef5c2cf51032.jpg', NULL, NULL, 2, 1, 12, '2026-04-05 19:22:27', '2026-04-09 00:17:12'),
  (1, '水蓝色玉兰花刺绣大袖衫齐腰襦裙', 'Sky Blue Magnolia Embroidered Hanfu with Wide Sleeves (Ruqun)', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/586473d07bd84774afcfb1455c6bf498.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/83c0ea25e14a4d63b242779a8bc0c1f9.jpg', NULL, NULL, 2, 1, 13, '2026-04-05 19:43:31', '2026-04-09 00:29:59'),
  (1, '白绿叠层重工珠绣仙气襦裙', 'Ethereal Beaded and Layered Gauze Hanfu (White & Mint Green)', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5ad2341d394544b0b1ac46a837f2eb32.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/04afc792638746f994c1b4329a5b90bb.jpg', NULL, NULL, 2, 1, 14, '2026-04-05 19:43:57', '2026-04-09 00:30:20'),
  (1, '薄荷绿重工刺绣大袖华服', 'Mint Green Elaborately Embroidered Grand Hanfu with Tiered Skirt', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/280d67cb739545d4a5bf199796058eb9.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/c855960c18084859938c3764b134fa0b.jpg', NULL, NULL, 2, 1, 15, '2026-04-05 19:44:15', '2026-04-09 00:30:43'),
  (1, '新中式斜肩山水绣花拼接礼服长裙', 'One-Shoulder Neo-Chinese Gown with Landscape Embroidery', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/733a02cd50c240939a6275cd76a8ade8.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/df9934938a68473fb205c245f2560528.jpg', NULL, NULL, 2, 1, 16, '2026-04-05 19:45:02', '2026-04-09 00:29:44'),
  (1, '浅绿色墨竹刺绣改良旗袍上衣配纱质阔腿裤', 'Bamboo Embroidered Modified Qipao Top with Gauze Wide-Leg Pants', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3e1999b5eb574da88baf3684bc65e72f.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/10c32e6807b44df4a631ecf64420bd6e.jpg', NULL, NULL, 2, 1, 18, '2026-04-05 19:45:28', '2026-04-09 00:30:58'),
  (1, '淡青色兰花刺绣比甲（马甲）配中式长裙', 'Pale Blue Orchid Embroidered Vest (Bijia) and Long Skirt Set', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/a4081ca57ad84f3c94094863db257ad6.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/8657ce189b4c47eea1413f611bbda804.jpg', NULL, NULL, 2, 1, 19, '2026-04-05 19:45:47', '2026-04-09 00:29:30'),
  (1, '一字肩蝴蝶结闪钻大蓬蓬裙婚纱', 'Off-the-Shoulder Bow-Accent Glitter Ball Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/64023a197072439da6e44fc50344dd17.jpg', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/022eb71567434bc1a5e9b0f09ac49438.jpg', NULL, NULL, 2, 1, 0, '2026-04-05 19:47:32', '2026-04-09 00:10:15'),
  (1, '粉色针织马甲配浅蓝格纹百褶裙', 'Pink Cardigan Vest with Light Blue Plaid Pleated Skirt', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3dba86e99add49c0b732089c1679e572.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/a29550ee95c84fedae96fece552c8175.jpg', NULL, NULL, 2, 1, 23, '2026-04-06 15:48:15', '2026-04-09 00:26:54'),
  (1, '经典黑白配色水手服套装', 'Classic Black and White Sailor Uniform', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/465083f4326b48e597f286110b98879e.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/69a9e252e45845ff9abb18e31fe87a6c.jpg', NULL, NULL, 2, 1, 22, '2026-04-06 16:25:50', '2026-04-09 00:26:13'),
  (1, '灰绿色开襟水手服', 'Sage Green Button-up Sailor Uniform with Striped Details', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5d07c11fa7054c01ac06c434479b86da.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/abc7f8b895484038aed356fc321697ca.jpg', NULL, NULL, 2, 1, 24, '2026-04-06 16:26:35', '2026-04-09 00:27:11'),
  (1, '熊猫主题花边水手服', 'Panda-themed Kawaii Sailor Uniform with Ruffled Collar', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/0020e125bd6345e5bd99318d83e6f9cd.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/95fec34a0d6742949e36b9cadc1adab4.jpg', NULL, NULL, 2, 1, 21, '2026-04-06 16:27:11', '2026-04-09 00:26:37'),
  (1, '黑色针织背心搭配绿格纹裙套装', 'Black Knit Vest with Green Plaid Skirt and Bowtie Set', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5701dd5011d246ce87bd34a5387331b8.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/aacedd03855744088a47e26d0aefd3e0.jpg', NULL, NULL, 2, 1, 25, '2026-04-06 16:32:30', '2026-04-09 00:27:29'),
  (1, '蓝格纹衬衫套装', '白衬衫配蓝格纹领结及百褶裙', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/0d209f7bdf194d4ea25864d588b02525.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/12cae07a61ba4c3c9c0020a998fae48b.jpg', NULL, NULL, 2, 1, 26, '2026-04-06 16:32:54', '2026-04-09 00:28:22'),
  (1, '深灰印花T恤配黑色牛仔短裤', 'Graphic Grey T-shirt & Black Denim Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/2a2f5846f77647a296fe2a678995f315.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/6d8574c0048447469c00c812d1fdda29.jpg', NULL, NULL, 2, 1, 27, '2026-04-06 20:02:33', '2026-04-09 00:32:21'),
  (1, '酒红撞色T恤配做旧破洞牛仔短裤', 'Burgundy Contrast T-shirt & Distressed Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3c54687147a249f29d4e4b1f0c13bca7.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/6d6301dbfb1541568fae96d0c9c8ecf7.jpg', NULL, NULL, 2, 1, 29, '2026-04-06 20:02:54', '2026-04-09 00:33:05'),
  (1, '白色T恤领巾配白色工装短裤', 'White T-shirt with Neckerchief & Cargo Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/a10c86d6dc964c1e9eb570ebc5188bf1.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/af7f078d11b244a29f6fbf20024049af.jpg', NULL, NULL, 2, 1, 28, '2026-04-06 20:03:09', '2026-04-09 00:33:17'),
  (1, '灰色字母T恤配工装阔腿牛仔裤', 'Slogan T-shirt & Baggy Cargo Jeans', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/9627df24b9fe41cb8d18e687786746b4.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/4fb8bd39f42a44f980ae0eb7c6de3fbf.jpg', NULL, NULL, 2, 1, 31, '2026-04-06 20:03:26', '2026-04-09 00:32:49'),
  (1, '湖蓝色印花T恤配浅色阔腿牛仔裤', 'Teal Graphic T-shirt & Wide-leg Light Jeans', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/12b567b09248411a9f020c01abaf33c0.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/1d79ae7081cf4aa9bf547f69bc36f7b3.jpg', NULL, NULL, 2, 1, 30, '2026-04-06 20:03:45', '2026-04-09 00:33:57'),
  (1, '紧身黑色圆领T恤配灰色褶皱西裤', 'Slim Black T-shirt & Grey Pleated Trousers', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/d9fa3d48a5244843bcda29c2c0c28f50.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/bf4ec656458b490bba7f002f113fef9c.jpg', NULL, NULL, 2, 1, 32, '2026-04-06 20:04:53', '2026-04-09 00:33:39'),
  (1, '白色挂脖露背百褶连衣裙', 'White Halter Neck Open Back Pleated Mini Dress', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/c97d36bca77b4cdfbd288089d547c095.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/5d63ac8bf8da4cf4b47bb45d440a37f5.jpg', NULL, NULL, 2, 1, 33, '2026-04-06 21:03:10', '2026-04-09 00:36:17'),
  (1, '挂脖马甲上衣与玫瑰花边半身裙套装', 'Halter Button-up Corset Top & Rosette Trim Mini Skirt Set', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/7c202000f2f44092ae1fe748ef5dec54.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/e7d35cad5f08452782571930be191f6e.jpg', NULL, NULL, 2, 1, 34, '2026-04-06 21:04:06', '2026-04-09 00:35:58'),
  (1, '挂脖深V褶皱亮面连衣裙配硬币流苏', 'Plunging Halter Ruched Satin Dress with Coin Fringe', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3227c86237604b9cbf151746287b2dee.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/a81a121b7cd3467882fea9191f359d7f.jpg', NULL, NULL, 2, 1, 35, '2026-04-06 21:04:32', '2026-04-09 00:36:35'),
  (1, '黑色亮片花卉吊带裙', 'Black Sequined Floral Cami Mini Dress', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/bf1b631f53134b5a9a9e92319e2859ff.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/63023a48b53a4e1291e3b764f04d784d.jpg', NULL, NULL, 2, 1, 36, '2026-04-06 21:04:51', '2026-04-09 00:36:51'),
  (1, '字母印花挂脖上衣与阔腿牛仔裤套装', 'Graphic Halter Crop Top & Wide-leg Denim Jeans Set', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/9fe41e02ed8448f9b98703252e6c93e4.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/bbe2236201874407af198d3811f187a9.jpg', NULL, NULL, 2, 1, 37, '2026-04-06 21:05:14', '2026-04-09 00:37:10'),
  (1, '腊肠狗印花短款T恤配阔腿工装裤', 'Dachshund Print Baby Tee & Wide-leg Cargo Pants', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/f68587425cf74e3992e649cff81ce789.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/215229f3092c4ffb8cdea5cb7541a3ad.jpg', NULL, NULL, 2, 1, 38, '2026-04-06 21:05:33', '2026-04-09 00:35:37'),
  (1, '薇迎网纱吊带裙连衣裙', NULL, 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/2814ca5ff31346239048059f3ef6a2e1.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/4fc0a77cda8c4779935222286035e67f.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:16:49', '2026-04-12 01:18:05'),
  (1, '薇迎假两件针织衫藏蓝色上衣', NULL, 'workplace', 'female', 'tops', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/16509a368dd54891b88998e0f1bd1950.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/84335d9e5ff5416e9f79ecf309f39ca5.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:17:51', '2026-04-12 01:18:57'),
  (1, '薇迎领短袖T恤针织衫上衣', NULL, 'workplace', 'female', 'tops', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/f3084ed3528546c9a3ede4c68feeee97.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/8bed372784aa477aac41b0719fd0abf0.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:20:12', '2026-04-12 01:20:26');
  
  INDEX `idx_user_task`(`user_id`, `created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_auths
-- ----------------------------
DROP TABLE IF EXISTS `user_auths`;
CREATE TABLE `user_auths`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联users.id',
  `identity_type` enum('phone','wechat','google','email') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录类型',
  `identifier` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号或OpenID/Sub',
  `credential` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码或Token(OAuth无需)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_identifier`(`identity_type`, `identifier`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户授权表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_credit_records
-- ----------------------------
DROP TABLE IF EXISTS `user_credit_records`;
CREATE TABLE `user_credit_records`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户ID',
  `order_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '关联订单ID',
  `package_id` int(11) UNSIGNED NULL DEFAULT NULL COMMENT '关联套餐ID',
  `credits` int(11) NOT NULL COMMENT '获得点数',
  `credits_remaining` int(11) NOT NULL COMMENT '剩余未消耗点数',
  `expire_at` datetime(0) NULL DEFAULT NULL COMMENT '过期时间，NULL表示永久有效',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态: 0-有效, 1-已过期',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_at` timestamp(0) NULL DEFAULT NULL COMMENT '实际过期处理时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_expire`(`user_id`, `status`, `expire_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户点数记录(支持过期扣除)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_invitations
-- ----------------------------
DROP TABLE IF EXISTS `user_invitations`;
CREATE TABLE `user_invitations`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `inviter_id` bigint(20) UNSIGNED NOT NULL COMMENT '邀请人ID',
  `invitee_id` bigint(20) UNSIGNED NOT NULL COMMENT '被邀请人ID',
  `invitation_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '使用的邀请码',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 1-已注册, 2-已完成首单, 3-已返利',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_invitee`(`invitee_id`) USING BTREE,
  INDEX `idx_inviter`(`inviter_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '邀请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_referral_codes
-- ----------------------------
DROP TABLE IF EXISTS `user_referral_codes`;
CREATE TABLE `user_referral_codes`  (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID，主键',
  `referral_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '唯一邀请码',
  `total_invites` int(11) NOT NULL DEFAULT 0 COMMENT '累计邀请人数',
  `total_rewards` int(11) NOT NULL DEFAULT 0 COMMENT '累计获得的返利点数',
  `updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uniq_referral_code`(`referral_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户邀请码表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_upload_records
-- ----------------------------
DROP TABLE IF EXISTS `user_upload_records`;
CREATE TABLE `user_upload_records`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户ID',
  `file_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件访问URL',
  `file_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'COS对象key(存储路径)',
  `category` enum('business','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '上传类型: business-业务文件, other-其他',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原始文件名',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 145 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户上传记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for user_wallets
-- ----------------------------
DROP TABLE IF EXISTS `user_wallets`;
CREATE TABLE `user_wallets`  (
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联users.id',
  `total_credits` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '当前可用点数',
  `frozen_credits` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '任务进行中冻结的点数',
  `total_recharged` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '累计充值总额',
  `total_recharged_amount` decimal(12, 2) NULL DEFAULT 0.00 COMMENT '累计充值金额(元)',
  `version` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '乐观锁版本',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户钱包' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '雪花ID',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '新用户' COMMENT '用户昵称',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像地址',
  `mobile` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备用邮箱',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
  `user_level` tinyint(4) NULL DEFAULT 0 COMMENT '等级: 0-普通, 1-Pro, 2-企业',
  `invitation_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邀请码',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `email`(`email`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- ----------------------------

-- ----------------------------
-- 品牌方（资质、法人证件、坐标等预留）
-- ----------------------------
DROP TABLE IF EXISTS `brand_audit_logs`;
DROP TABLE IF EXISTS `brand_users`;
DROP TABLE IF EXISTS `brand_garments`;
DROP TABLE IF EXISTS `brands`;
CREATE TABLE `brands`  (
  `id` bigint(20) NOT NULL COMMENT '主键雪花ID（应用生成，非自增）',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '唯一编码/slug，接口与展示用',
  `brand_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '品牌名称（C 端展示、对外简称）',
  `brand_name_en` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '品牌英文名称',
  `company_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '公司/主体全称（与营业执照一致，入驻时可后补）',
  `company_name_en` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '公司英文名称（可选）',
  `logo_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Logo URL',
  `unified_social_credit_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '统一社会信用代码（预留）',
  `business_license_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '营业执照扫描件 COS URL',
  `legal_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法定代表人姓名',
  `legal_id_front_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人身份证人像面',
  `legal_id_back_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人身份证国徽面',
  `legal_id_no_masked` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '证件号脱敏展示（可选，完整号勿明文落库）',
  `legal_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人手机号',
  `contact_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '企业对公/对接邮箱',
  `company_address` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '注册或经营地址（文本）',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '单位经度 WGS84',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '单位纬度 WGS84',
  `location_precision` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '坐标来源说明：如 map_pick / manual',
  `audit_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0-待完善 1-待审核 2-已通过 3-已驳回',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用（与审核通过后可上架配合使用）',
  `intake_user_id` bigint(20) NULL DEFAULT NULL COMMENT 'C端品牌入驻提交人用户ID（雪花，可空）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '平台内部备注',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_brands_code`(`code`) USING BTREE,
  INDEX `idx_brands_audit`(`audit_status`, `status`) USING BTREE,
  INDEX `idx_brands_intake_user`(`intake_user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '品牌方主体与资质' ROW_FORMAT = Dynamic;

-- 已有库升级：若 brands 表无 intake_user_id 可执行
-- ALTER TABLE `brands` ADD COLUMN `intake_user_id` bigint(20) NULL DEFAULT NULL COMMENT 'C端品牌入驻提交人用户ID（雪花，可空）' AFTER `status`;
-- CREATE INDEX `idx_brands_intake_user` ON `brands` (`intake_user_id`);

-- 种子品牌 id=1（供下方从公共库复制的服装数据挂载；生产环境可删改）

-- ----------------------------
-- 品牌服装库（字段与 public_garments 对齐，归属 brand_id）
-- ----------------------------
CREATE TABLE `brand_garments`  (
  `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `brand_id` bigint(20) NOT NULL COMMENT '所属品牌雪花ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '展示名称',
  `name_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '英文名称',
  `scene_category` enum('wedding','workplace','campus','gu_feng','trendy','western') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '场景分类',
  `gender` enum('male','female','unisex') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'unisex' COMMENT '适用性别',
  `garment_type` enum('tops','bottoms','one-pieces') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服装结构',
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '高清原图 URL',
  `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '列表缩略图',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '后台备注',
  `purchase_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '购买链接',
  `audit_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0-草稿 1-待审 2-已上架 3-驳回（第二阶段与平台审核联动）',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否上架（需结合 audit_status、品牌状态）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '同品牌内排序',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_bg_brand_active`(`brand_id`, `is_active`, `sort_order`) USING BTREE,
  INDEX `idx_bg_brand_scene`(`brand_id`, `scene_category`) USING BTREE,
  CONSTRAINT `fk_bg_brand` FOREIGN KEY (`brand_id`) REFERENCES `brands` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '品牌方服装库' ROW_FORMAT = Dynamic;

-- 从 public_garments 同步至品牌 id=1（audit_status=2 已上架）
(1, '宫廷风珠绣大拖尾婚纱', 'Royal Beaded Ball Gown with Cathedral Train', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/62ac6edb4ad044728cd58b320aec244b.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/f8f4469c651f443c8b8c6e530980b75c.jpg', NULL, NULL, 2, 1, 6, '2026-04-05 04:45:39', '2026-04-09 00:09:53'),
(1, '奢华珠绣鱼尾裙配百褶袖', 'Ornate Beaded Mermaid Gown with Pleated Tulle Sleeves', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/dd4da21f9cf7476284fdfa15277e87a7.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/3be96d88ffb044d49fdda1d1f87edcc3.jpg', NULL, NULL, 2, 1, 3, '2026-04-05 04:46:02', '2026-04-09 00:13:48'),
(1, '简约极简主义褶皱缎面裙', 'Minimalist Pleated Organza A-Line Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/143f504428604eecafa1aee036166808.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/1adfb341daee4556a302692328ace087.jpg', NULL, NULL, 2, 1, 5, '2026-04-05 04:48:48', '2026-04-09 00:13:20'),
(1, '立体蝴蝶贴花仙女裙', '3D Butterfly Appliqué Ethereal Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/e17ca4b869b649f5a74c8b3c52dabaaa.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/4fcacada29b2426b9fecf046a0abf50f.jpg', NULL, NULL, 2, 1, 4, '2026-04-05 04:50:24', '2026-04-09 00:12:54'),
(1, '露肩蕾丝刺绣鱼尾婚纱', 'Off-the-Shoulder Lace Embroidered Mermaid Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/7620aa2cb65b4932a3a93a8d016b31f3.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/265bcdcfc3aa4ec6850314b40ee62c2f.jpg', NULL, NULL, 2, 1, 2, '2026-04-05 04:51:33', '2026-04-09 00:14:29'),
(1, '多层蛋糕裙摆长袖高定礼服', 'Tiered Ruffle Skirt Long-Sleeve Couture Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/d232cc18fc504c769e2fc59b2e04e7b1.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/a7d87fb75c2b4382898c0ed0242872e9.jpg', NULL, NULL, 2, 1, 1, '2026-04-05 04:54:34', '2026-04-09 00:38:11'),
(1, '斜肩褶皱玫瑰刺绣伞裙', 'One-Shoulder Drape & Rose Embroidered A-Line Dress', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/b1a89255b38d4a16bc5d9ffd0d50c709.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/d51a040f292a45e2b0e0437556e9117b.jpg', NULL, NULL, 2, 1, 9, '2026-04-05 19:20:41', '2026-04-12 01:18:16'),
(1, '条纹短西装配腰带背心裙', 'Cropped Blazer & Belted Pinafore Set', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/8f19f339605e484aa3bbeb028f7feb67.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/6bfdbb5715904e1086f138777477d034.jpg', NULL, NULL, 2, 1, 9, '2026-04-05 19:20:59', '2026-04-09 00:16:23'),
(1, '荷叶边雪纺衫配修身半裙', 'Ruffled Chiffon Blouse & Pencil Skirt', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/f9a645b4f7b242c1b236c607ad8c6d70.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/c0b8005e27ef4e67a87b6a5cd51cc2b2.jpg', NULL, NULL, 2, 1, 10, '2026-04-05 19:21:20', '2026-04-09 00:16:42'),
(1, '交叉V领蕾丝钩花连衣裙', 'Surplice Neckline Floral Lace Sheath Dress', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/17233b6cf5ff4772b2d1e72fa2d5d4ca.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/5189051b19554da6b67c7423d54938ed.jpg', NULL, NULL, 2, 1, 11, '2026-04-05 19:21:40', '2026-04-09 00:19:35'),
(1, '条纹短袖衬衫裙', 'Pinstriped Short-Sleeve Shirt Dress', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/22966523f5334b418b3d5e68acaf79d3.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/60b155c853994d2db436ff738fef85f3.jpg', NULL, NULL, 2, 1, 8, '2026-04-05 19:22:01', '2026-04-09 00:16:02'),
(1, '无袖条纹系带铅笔裙', 'Sleeveless Pinstriped Tie-Waist Sheath Dress', 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/8fefb4ff21f54eb398c8d0f499953c8c.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/d59b3d72b4664c149971ef5c2cf51032.jpg', NULL, NULL, 2, 1, 12, '2026-04-05 19:22:27', '2026-04-09 00:17:12'),
(1, '水蓝色玉兰花刺绣大袖衫齐腰襦裙', 'Sky Blue Magnolia Embroidered Hanfu with Wide Sleeves (Ruqun)', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/586473d07bd84774afcfb1455c6bf498.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/83c0ea25e14a4d63b242779a8bc0c1f9.jpg', NULL, NULL, 2, 1, 13, '2026-04-05 19:43:31', '2026-04-09 00:29:59'),
(1, '白绿叠层重工珠绣仙气襦裙', 'Ethereal Beaded and Layered Gauze Hanfu (White & Mint Green)', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5ad2341d394544b0b1ac46a837f2eb32.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/04afc792638746f994c1b4329a5b90bb.jpg', NULL, NULL, 2, 1, 14, '2026-04-05 19:43:57', '2026-04-09 00:30:20'),
(1, '薄荷绿重工刺绣大袖华服', 'Mint Green Elaborately Embroidered Grand Hanfu with Tiered Skirt', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/280d67cb739545d4a5bf199796058eb9.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/c855960c18084859938c3764b134fa0b.jpg', NULL, NULL, 2, 1, 15, '2026-04-05 19:44:15', '2026-04-09 00:30:43'),
(1, '新中式斜肩山水绣花拼接礼服长裙', 'One-Shoulder Neo-Chinese Gown with Landscape Embroidery', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/733a02cd50c240939a6275cd76a8ade8.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/df9934938a68473fb205c245f2560528.jpg', NULL, NULL, 2, 1, 16, '2026-04-05 19:45:02', '2026-04-09 00:29:44'),
(1, '浅绿色墨竹刺绣改良旗袍上衣配纱质阔腿裤', 'Bamboo Embroidered Modified Qipao Top with Gauze Wide-Leg Pants', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3e1999b5eb574da88baf3684bc65e72f.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/10c32e6807b44df4a631ecf64420bd6e.jpg', NULL, NULL, 2, 1, 18, '2026-04-05 19:45:28', '2026-04-09 00:30:58'),
(1, '淡青色兰花刺绣比甲（马甲）配中式长裙', 'Pale Blue Orchid Embroidered Vest (Bijia) and Long Skirt Set', 'gu_feng', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/a4081ca57ad84f3c94094863db257ad6.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/8657ce189b4c47eea1413f611bbda804.jpg', NULL, NULL, 2, 1, 19, '2026-04-05 19:45:47', '2026-04-09 00:29:30'),
(1, '一字肩蝴蝶结闪钻大蓬蓬裙婚纱', 'Off-the-Shoulder Bow-Accent Glitter Ball Gown', 'wedding', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/64023a197072439da6e44fc50344dd17.jpg', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/022eb71567434bc1a5e9b0f09ac49438.jpg', NULL, NULL, 2, 1, 0, '2026-04-05 19:47:32', '2026-04-09 00:10:15'),
(1, '粉色针织马甲配浅蓝格纹百褶裙', 'Pink Cardigan Vest with Light Blue Plaid Pleated Skirt', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3dba86e99add49c0b732089c1679e572.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/a29550ee95c84fedae96fece552c8175.jpg', NULL, NULL, 2, 1, 23, '2026-04-06 15:48:15', '2026-04-09 00:26:54'),
(1, '经典黑白配色水手服套装', 'Classic Black and White Sailor Uniform', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/465083f4326b48e597f286110b98879e.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/69a9e252e45845ff9abb18e31fe87a6c.jpg', NULL, NULL, 2, 1, 22, '2026-04-06 16:25:50', '2026-04-09 00:26:13'),
(1, '灰绿色开襟水手服', 'Sage Green Button-up Sailor Uniform with Striped Details', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5d07c11fa7054c01ac06c434479b86da.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/abc7f8b895484038aed356fc321697ca.jpg', NULL, NULL, 2, 1, 24, '2026-04-06 16:26:35', '2026-04-09 00:27:11'),
(1, '熊猫主题花边水手服', 'Panda-themed Kawaii Sailor Uniform with Ruffled Collar', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/0020e125bd6345e5bd99318d83e6f9cd.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/95fec34a0d6742949e36b9cadc1adab4.jpg', NULL, NULL, 2, 1, 21, '2026-04-06 16:27:11', '2026-04-09 00:26:37'),
(1, '黑色针织背心搭配绿格纹裙套装', 'Black Knit Vest with Green Plaid Skirt and Bowtie Set', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/5701dd5011d246ce87bd34a5387331b8.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/aacedd03855744088a47e26d0aefd3e0.jpg', NULL, NULL, 2, 1, 25, '2026-04-06 16:32:30', '2026-04-09 00:27:29'),
(1, '蓝格纹衬衫套装', '白衬衫配蓝格纹领结及百褶裙', 'campus', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/0d209f7bdf194d4ea25864d588b02525.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/12cae07a61ba4c3c9c0020a998fae48b.jpg', NULL, NULL, 2, 1, 26, '2026-04-06 16:32:54', '2026-04-09 00:28:22'),
(1, '深灰印花T恤配黑色牛仔短裤', 'Graphic Grey T-shirt & Black Denim Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/2a2f5846f77647a296fe2a678995f315.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/6d8574c0048447469c00c812d1fdda29.jpg', NULL, NULL, 2, 1, 27, '2026-04-06 20:02:33', '2026-04-09 00:32:21'),
(1, '酒红撞色T恤配做旧破洞牛仔短裤', 'Burgundy Contrast T-shirt & Distressed Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3c54687147a249f29d4e4b1f0c13bca7.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/6d6301dbfb1541568fae96d0c9c8ecf7.jpg', NULL, NULL, 2, 1, 29, '2026-04-06 20:02:54', '2026-04-09 00:33:05'),
(1, '白色T恤领巾配白色工装短裤', 'White T-shirt with Neckerchief & Cargo Shorts', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/a10c86d6dc964c1e9eb570ebc5188bf1.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/af7f078d11b244a29f6fbf20024049af.jpg', NULL, NULL, 2, 1, 28, '2026-04-06 20:03:09', '2026-04-09 00:33:17'),
(1, '灰色字母T恤配工装阔腿牛仔裤', 'Slogan T-shirt & Baggy Cargo Jeans', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/9627df24b9fe41cb8d18e687786746b4.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/4fb8bd39f42a44f980ae0eb7c6de3fbf.jpg', NULL, NULL, 2, 1, 31, '2026-04-06 20:03:26', '2026-04-09 00:32:49'),
(1, '湖蓝色印花T恤配浅色阔腿牛仔裤', 'Teal Graphic T-shirt & Wide-leg Light Jeans', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/12b567b09248411a9f020c01abaf33c0.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/1d79ae7081cf4aa9bf547f69bc36f7b3.jpg', NULL, NULL, 2, 1, 30, '2026-04-06 20:03:45', '2026-04-09 00:33:57'),
(1, '紧身黑色圆领T恤配灰色褶皱西裤', 'Slim Black T-shirt & Grey Pleated Trousers', 'trendy', 'male', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/d9fa3d48a5244843bcda29c2c0c28f50.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/bf4ec656458b490bba7f002f113fef9c.jpg', NULL, NULL, 2, 1, 32, '2026-04-06 20:04:53', '2026-04-09 00:33:39'),
(1, '白色挂脖露背百褶连衣裙', 'White Halter Neck Open Back Pleated Mini Dress', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/c97d36bca77b4cdfbd288089d547c095.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/5d63ac8bf8da4cf4b47bb45d440a37f5.jpg', NULL, NULL, 2, 1, 33, '2026-04-06 21:03:10', '2026-04-09 00:36:17'),
(1, '挂脖马甲上衣与玫瑰花边半身裙套装', 'Halter Button-up Corset Top & Rosette Trim Mini Skirt Set', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/7c202000f2f44092ae1fe748ef5dec54.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/e7d35cad5f08452782571930be191f6e.jpg', NULL, NULL, 2, 1, 34, '2026-04-06 21:04:06', '2026-04-09 00:35:58'),
(1, '挂脖深V褶皱亮面连衣裙配硬币流苏', 'Plunging Halter Ruched Satin Dress with Coin Fringe', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/3227c86237604b9cbf151746287b2dee.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/a81a121b7cd3467882fea9191f359d7f.jpg', NULL, NULL, 2, 1, 35, '2026-04-06 21:04:32', '2026-04-09 00:36:35'),
(1, '黑色亮片花卉吊带裙', 'Black Sequined Floral Cami Mini Dress', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/bf1b631f53134b5a9a9e92319e2859ff.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/63023a48b53a4e1291e3b764f04d784d.jpg', NULL, NULL, 2, 1, 36, '2026-04-06 21:04:51', '2026-04-09 00:36:51'),
(1, '字母印花挂脖上衣与阔腿牛仔裤套装', 'Graphic Halter Crop Top & Wide-leg Denim Jeans Set', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/9fe41e02ed8448f9b98703252e6c93e4.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/bbe2236201874407af198d3811f187a9.jpg', NULL, NULL, 2, 1, 37, '2026-04-06 21:05:14', '2026-04-09 00:37:10'),
(1, '腊肠狗印花短款T恤配阔腿工装裤', 'Dachshund Print Baby Tee & Wide-leg Cargo Pants', 'western', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/f68587425cf74e3992e649cff81ce789.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/215229f3092c4ffb8cdea5cb7541a3ad.jpg', NULL, NULL, 2, 1, 38, '2026-04-06 21:05:33', '2026-04-09 00:35:37'),
(1, '薇迎网纱吊带裙连衣裙', NULL, 'workplace', 'female', 'one-pieces', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/2814ca5ff31346239048059f3ef6a2e1.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/4fc0a77cda8c4779935222286035e67f.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:16:49', '2026-04-12 01:18:05'),
(1, '薇迎假两件针织衫藏蓝色上衣', NULL, 'workplace', 'female', 'tops', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/16509a368dd54891b88998e0f1bd1950.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/84335d9e5ff5416e9f79ecf309f39ca5.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:17:51', '2026-04-12 01:18:57'),
(1, '薇迎领短袖T恤针织衫上衣', NULL, 'workplace', 'female', 'tops', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/2026/04/f3084ed3528546c9a3ede4c68feeee97.png', 'https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/public-garment/thumb/2026/04/8bed372784aa477aac41b0719fd0abf0.jpg', NULL, NULL, 2, 1, 7, '2026-04-12 01:20:12', '2026-04-12 01:20:26');

-- ----------------------------
-- 品牌方子账号（第二阶段：品牌独立登录维护服装库）
-- ----------------------------
CREATE TABLE `brand_users`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `brand_id` bigint(20) NOT NULL COMMENT '所属品牌雪花ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录名（同品牌内唯一）',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt 密码',
  `display_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示名',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号绑定手机（可选）',
  `role` enum('owner','admin','editor','viewer') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'editor' COMMENT '角色',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
  `last_login_at` timestamp(0) NULL DEFAULT NULL COMMENT '最近登录时间',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_brand_username`(`brand_id`, `username`) USING BTREE,
  INDEX `idx_bu_brand`(`brand_id`, `status`) USING BTREE,
  CONSTRAINT `fk_bu_brand` FOREIGN KEY (`brand_id`) REFERENCES `brands` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '品牌方登录账号' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 品牌相关操作审计（第二阶段：合规留痕，可选启用）
-- ----------------------------
CREATE TABLE `brand_audit_logs`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `brand_id` bigint(20) NOT NULL COMMENT '关联品牌雪花ID',
  `actor_type` enum('platform_admin','brand_user') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作者类型',
  `actor_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '操作者ID：admin_users.id 或 brand_users.id',
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作编码，如 brand.update / garment.create',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'JSON 或简短说明',
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作 IP',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_bal_brand_time`(`brand_id`, `created_at`) USING BTREE,
  CONSTRAINT `fk_bal_brand` FOREIGN KEY (`brand_id`) REFERENCES `brands` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '品牌资质与库变更审计' ROW_FORMAT = Dynamic;

-- 用户扫码进店来源品牌（需在 brands 表存在后执行）
ALTER TABLE `users` ADD COLUMN `brand_id` bigint(20) NULL DEFAULT NULL COMMENT '扫码进店来源品牌ID' AFTER `invitation_code`;
ALTER TABLE `users` ADD INDEX `idx_users_brand`(`brand_id`);
ALTER TABLE `users` ADD CONSTRAINT `fk_users_brand` FOREIGN KEY (`brand_id`) REFERENCES `brands` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- 注册来源：web-网页/H5，mp_weixin-微信小程序，app-原生 App（预留）
ALTER TABLE `users` ADD COLUMN `register_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '注册来源: web|mp_weixin|app' AFTER `brand_id`;

-- 邮箱验证码登录
ALTER TABLE `user_auths` MODIFY COLUMN `identity_type` enum('phone','wechat','google','email') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录类型';


-- ------------------------------------------------------------
-- Tables that were created after the original schema snapshot
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `gemini_image_tasks`;
CREATE TABLE `gemini_image_tasks` (
  `id` bigint(20) NOT NULL COMMENT 'Task ID (snowflake)',
  `task_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Business task type',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT 'Owner user ID',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0-pending 1-running 2-success 3-failed',
  `consume_credits` int(11) NOT NULL DEFAULT 0 COMMENT 'Credits charged on success',
  `payload_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'Task payload',
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'Task result',
  `error_msg` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `processing_started_at` datetime NULL DEFAULT NULL COMMENT 'Claim time',
  `generation_duration_ms` bigint(20) NULL DEFAULT NULL COMMENT 'Total generation duration',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_gemini_status_created` (`status`, `created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Gemini async image tasks' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `model_video_tasks`;
CREATE TABLE `model_video_tasks` (
  `id` bigint(20) NOT NULL COMMENT 'Task ID (snowflake)',
  `user_id` bigint(20) NOT NULL COMMENT 'Owner user ID',
  `source_tryon_task_id` bigint(20) NULL DEFAULT NULL COMMENT 'Source try-on task',
  `source_image_edit_task_id` bigint(20) NULL DEFAULT NULL COMMENT 'Source image-edit task',
  `prompt` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'User prompt',
  `source_image_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'First frame image',
  `duration_seconds` int(11) NOT NULL DEFAULT 8 COMMENT 'Video duration',
  `resolution` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '720p',
  `aspect_ratio` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '9:16',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0-queued 1-running 2-done 3-failed',
  `result_video_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `error_msg` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `consume_credits` int(11) NOT NULL DEFAULT 0 COMMENT 'Credits charged',
  `processing_started_at` datetime NULL DEFAULT NULL,
  `generation_duration_ms` bigint(20) NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_model_video_user_created` (`user_id`, `created_at`) USING BTREE,
  KEY `idx_model_video_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Veo image-to-video tasks' ROW_FORMAT = Dynamic;


-- ============================================================
-- Migration: migration-20260422-orders-pay-channel-lemon-squeezy.sql
-- ============================================================
-- 已有库升级：为 orders.pay_channel ENUM 增加 Lemon Squeezy（与代码 payChannel=lemonsqueezy 一致）
-- 在目标库执行一次即可。

ALTER TABLE `orders`
  MODIFY COLUMN `pay_channel` enum(
    'wxpay',
    'alipay',
    'paypal',
    'stripe',
    'usdt_tron',
    'usdt_bsc',
    'lemonsqueezy'
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付渠道';


-- ============================================================
-- Migration: migration-admin-stats-indexes.sql
-- ============================================================
-- 数据概览聚合用；索引已存在时会报错，可忽略
ALTER TABLE `users` ADD INDEX `idx_users_created_at` (`created_at`);
ALTER TABLE `tryon_tasks` ADD INDEX `idx_tryon_created_at` (`created_at`);
ALTER TABLE `tryon_tasks` ADD INDEX `idx_tryon_status` (`status`);
ALTER TABLE `gemini_image_tasks` ADD INDEX `idx_gemini_type_created` (`task_type`, `created_at`);
ALTER TABLE `orders` ADD INDEX `idx_orders_status_paid_at` (`status`, `paid_at`);


-- ============================================================
-- Migration: migration-credits-deducted-at.sql
-- ============================================================
ALTER TABLE `tryon_tasks`
  ADD COLUMN `credits_deducted_at` timestamp(0) NULL DEFAULT NULL COMMENT '成功扣点时间（NULL 表示未扣）' AFTER `consume_credits`;

ALTER TABLE `gemini_image_tasks`
  ADD COLUMN `credits_deducted_at` timestamp(0) NULL DEFAULT NULL COMMENT '成功扣点时间（NULL 表示未扣）' AFTER `consume_credits`;


-- ============================================================
-- Migration: migration-gemini-api-keys.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-open-api-ip-whitelist.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-register-abuse-policy.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-register-gift-policy.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-user-api-keys.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-user-favorites.sql
-- ============================================================
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


-- ============================================================
-- Migration: migration-user-feedback.sql
-- ============================================================
-- 用户问题反馈（PC 提交 + 后台查询）
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '登录用户 ID，匿名为空',
    category VARCHAR(40) NOT NULL COMMENT '问题类型代码',
    content TEXT NOT NULL COMMENT '反馈正文',
    contact VARCHAR(200) NULL COMMENT '联系方式（匿名时建议填写）',
    page_path VARCHAR(512) NULL COMMENT '前端上报的页面路径',
    user_agent VARCHAR(512) NULL,
    client_ip VARCHAR(64) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 未处理 1 已处理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_feedback_created (created_at),
    KEY idx_user_feedback_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户问题反馈';


-- ============================================================
-- Migration: migration-xhs-card-tasks.sql
-- ============================================================
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


SET FOREIGN_KEY_CHECKS = 1;
