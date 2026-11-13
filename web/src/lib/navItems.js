/**
 * 全站导航单一数据源：侧边栏、全局搜索、首页工具卡片共用
 */

/** 功能工具（首页卡片与搜索「功能」分组） */
export const TOOLS = [
  {
    key: 'tryon',
    path: '/tryon',
    icon: 'shirt',
    titleKey: 'uiNavTryon',
    descKey: 'uiToolTryonDesc',
    cost: 1,
    badge: 'hot',
    keywords: 'tryon try-on 试衣 换装 试穿 virtual',
  },
  {
    key: 'batch',
    path: '/batch-tryon',
    icon: 'layers',
    titleKey: 'navBatchTryon',
    descKey: 'uiToolBatchDesc',
    cost: 1,
    keywords: 'batch 批量 多件 试衣',
  },
  {
    key: 'packshot',
    path: '/model-shots',
    icon: 'model',
    titleKey: 'navPackshot',
    descKey: 'uiToolPackshotDesc',
    cost: 4,
    keywords: 'model shots packshot 模特 出图 上身 场景',
  },
  {
    key: 'suite',
    path: '/clothing-suite',
    icon: 'grid',
    titleKey: 'navEcomSuite',
    descKey: 'uiToolSuiteDesc',
    cost: 10,
    keywords: 'suite ecommerce 电商 套图 主图 详情页 卖点',
  },
  {
    key: 'retouch',
    path: '/product-retouch',
    icon: 'retouch',
    titleKey: 'navProductRetouch',
    descKey: 'uiToolRetouchDesc',
    cost: 1,
    keywords: 'retouch 精修 白底 去褶皱 商品图',
  },
  {
    key: 'localEdit',
    path: '/local-edit',
    icon: 'brush',
    titleKey: 'navLocalEdit',
    descKey: 'uiToolLocalEditDesc',
    cost: 1,
    keywords: 'local edit 局部 改款 改色 换面料 配饰 涂抹 inpaint',
  },
  {
    key: 'portrait',
    path: '/portrait-studio',
    icon: 'portrait',
    titleKey: 'navPortraitStudio',
    descKey: 'uiToolPortraitDesc',
    cost: 1,
    keywords: 'portrait 写真 自拍 韩系 日系 杂志',
  },
  {
    key: 'modelVideo',
    path: '/model-video',
    icon: 'video',
    titleKey: 'navModelVideo',
    descKey: 'uiToolModelVideoDesc',
    cost: 4,
    badge: 'new',
    keywords: 'video veo 穿搭视频 模特视频 图生视频 走动',
  },
];

const toolByKey = Object.fromEntries(TOOLS.map((t) => [t.key, t]));
const toolItem = (key) => {
  const t = toolByKey[key];
  return { path: t.path, icon: t.icon, titleKey: t.titleKey, badge: t.badge };
};

/** 侧边栏分组 */
export const NAV_GROUPS = [
  {
    key: 'main',
    items: [{ path: '/', icon: 'home', titleKey: 'uiNavHome', exact: true }],
  },
  { key: 'tryon', titleKey: 'uiGroupTryon', items: [toolItem('tryon'), toolItem('batch')] },
  {
    key: 'ecom',
    titleKey: 'uiGroupEcom',
    items: [toolItem('packshot'), toolItem('suite'), toolItem('retouch')],
  },
  {
    key: 'create',
    titleKey: 'uiGroupCreate',
    items: [toolItem('localEdit'), toolItem('portrait'), toolItem('modelVideo')],
  },
  {
    key: 'mine',
    titleKey: 'uiGroupMine',
    items: [
      { path: '/works', icon: 'images', titleKey: 'uiNavWorks', auth: true },
      { path: '/recharge', icon: 'card', titleKey: 'navRecharge' },
      { path: '/wallet', icon: 'wallet', titleKey: 'uiNavWallet', auth: true },
      { path: '/invite', icon: 'gift', titleKey: 'navInvite' },
      { path: '/developer', icon: 'code', titleKey: 'uiNavApi' },
    ],
  },
];

/** 侧边栏底部 / 搜索「页面」分组里的次要页面 */
export const SECONDARY_PAGES = [
  { path: '/profile', icon: 'user', titleKey: 'profile', keywords: 'profile 个人 主页 账号', auth: true },
  { path: '/feedback', icon: 'message', titleKey: 'navFeedback', keywords: 'feedback 反馈 问题 bug' },
  { path: '/contact', icon: 'mail', titleKey: 'navContact', keywords: 'contact 联系 客服 微信' },
  { path: '/brand-join', icon: 'building', titleKey: 'navBrandJoin', keywords: 'brand 品牌 入驻 合作' },
  { path: '/privacy', icon: 'file', titleKey: 'navPrivacy', keywords: 'privacy 隐私' },
  { path: '/terms', icon: 'file', titleKey: 'navTerms', keywords: 'terms 条款 协议' },
  { path: '/commercial-license', icon: 'file', titleKey: 'navCommercialLicense', keywords: 'license 版权 商用 授权' },
];

const PAGE_KEYWORDS = {
  '/': 'home 首页 dashboard',
  '/works': 'works 作品 历史 收藏 favorites history 下载 zip',
  '/recharge': 'recharge 充值 套餐 价格 pricing buy 购买',
  '/wallet': 'wallet 钱包 余额 点数 订单 credits orders',
  '/invite': 'invite 邀请 奖励 推广 referral',
  '/developer': 'api developer 开发者 接口 key 密钥',
};

/** 搜索用的扁平列表（去重） */
export function searchableEntries() {
  const seen = new Set();
  const out = [];
  for (const t of TOOLS) {
    seen.add(t.path);
    out.push({ group: 'tools', ...t });
  }
  for (const g of NAV_GROUPS) {
    for (const it of g.items) {
      if (seen.has(it.path)) continue;
      seen.add(it.path);
      out.push({ group: 'pages', ...it, keywords: PAGE_KEYWORDS[it.path] || '' });
    }
  }
  for (const p of SECONDARY_PAGES) {
    if (seen.has(p.path)) continue;
    seen.add(p.path);
    out.push({ group: 'pages', ...p });
  }
  return out;
}

/** 当前路由对应的标题 key（顶栏显示） */
export function titleKeyForPath(path) {
  const p = path === '/clothing-packshot' ? '/model-shots' : path;
  for (const g of NAV_GROUPS) {
    for (const it of g.items) if (it.path === p) return it.titleKey;
  }
  for (const it of SECONDARY_PAGES) if (it.path === p) return it.titleKey;
  return null;
}

/** 工具页：侧栏默认收起，给画布更多空间 */
export function isToolPath(path) {
  return TOOLS.some((t) => t.path === path) || path === '/clothing-packshot';
}
