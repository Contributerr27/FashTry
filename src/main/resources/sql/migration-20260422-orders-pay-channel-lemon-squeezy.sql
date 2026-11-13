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
