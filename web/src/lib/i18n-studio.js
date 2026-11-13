/**
 * Fashion Studio 重构界面文案（侧栏 / 顶栏 / Create Photos / 预设占位页）
 * 由 i18n.js 合并进 messages.zh / messages.en，沿用现有 localechange 机制。
 * 命名统一 st* 前缀，避免与旧页面键冲突。
 */
export const studioMessages = {
  zh: {
    // 品牌与侧栏
    stBrand: '时尚工作室',
    stBrandSub: 'Fashion Studio',
    stNavDashboard: '工作台首页',
    stNavCreate: '创作照片',
    stNavProjects: '我的项目',
    stNavPresets: '品牌预设',
    stNavModels: '模特库',
    stNavBilling: '账单',
    stNavSettings: '设置',
    stMoreTools: '更多工具',
    stMoreToolsHint: '原有全部创作功能',
    stUpgradeTitle: '升级专业版',
    stUpgradeBody: '更多点数、进阶功能与优先生成。',
    stUpgradeBtn: '立即升级',
    stCredits: '点数',
    stNotifications: '通知',
    stNoNotifications: '暂无新通知',
    stNewBadge: '新',

    // 英雄区
    stHeroLine1: '让你的商品',
    stHeroLine2: '秒变惊艳',
    stHeroAccent: '时尚大片',
    stHeroLead: '上传服装、选择模特与风格，让 AI 生成专业的上身大片。',

    // 步骤条
    stStep1: '上传商品',
    stStep2: '选择模特',
    stStep3: '选择场景',
    stStep4: '生成',

    // 左侧配置
    stUploadTitle: '上传你的服装',
    stUploadTip: '支持单件服装的平铺图或白底图，效果最佳',
    stUploadClick: '点击上传',
    stUploadOr: '或拖拽到此处',
    stUploadHint: 'JPG、PNG（最大 10MB）',
    stUploadPreview: '已上传，点击可更换',
    stChooseModel: '选择模特',
    stViewAll: '查看全部',
    stSelectScene: '选择场景',
    stSceneStudio: '影棚',
    stSceneStreet: '城市街头',
    stSceneCafe: '咖啡馆',
    stSceneGarden: '花园庭院',
    stGenerate: '生成图片',
    stGenerating: '生成中…',
    stCostHint: '本次消耗 4 点数',

    // 右侧结果
    stBefore: '原图',
    stAfter: '效果',
    stResultCount: '张结果',
    stDownloadAll: '下载全部',
    stAddProject: '加入项目',
    stRegenerate: '重新生成',
    stDemoHint: '演示模式 · 不消耗点数、不调用生成接口',

    // 反馈
    stToastUploaded: '服装图片已上传',
    stToastBadFile: '请上传 JPG 或 PNG 图片（不超过 10MB）',
    stToastGenerated: '已生成 {n} 张效果图（演示）',
    stToastDownload: '演示模式：接入下载接口后即可导出',
    stToastProject: '已加入「我的项目」（演示）',
    stToastRegenerate: '已重新生成效果图（演示）',
    stToastLogin: '登录后可保存项目与下载成片',

    // 占位页
    stPresetsTitle: '品牌预设',
    stPresetsBody: '把常用模特、场景与色调存成一个预设，下次创作一键套用。该功能即将上线。',
    stModelsTitle: '模特库',
    stModelsBody: '统一管理你的专属模特：不同身型、肤色与风格，随时切换。该功能即将上线。',
    stStubBack: '返回创作',

    pageTitleCreatePhotos: '创作照片 - Fashion Studio',
    pageMetaDescCreatePhotos: '上传服装、选择模特与场景，用 AI 一键生成专业上身大片。',
  },

  en: {
    // Brand & sidebar
    stBrand: 'Fashion',
    stBrandSub: 'Studio',
    stNavDashboard: 'Dashboard',
    stNavCreate: 'Create Photos',
    stNavProjects: 'My Projects',
    stNavPresets: 'Brand Presets',
    stNavModels: 'Model Library',
    stNavBilling: 'Billing',
    stNavSettings: 'Settings',
    stMoreTools: 'More Tools',
    stMoreToolsHint: 'All original creation tools',
    stUpgradeTitle: 'Upgrade to Pro',
    stUpgradeBody: 'Get more credits, advanced features and priority generation.',
    stUpgradeBtn: 'Upgrade Now',
    stCredits: 'Credits',
    stNotifications: 'Notifications',
    stNoNotifications: "You're all caught up",
    stNewBadge: 'NEW',

    // Hero
    stHeroLine1: 'Turn Your Products',
    stHeroLine2: 'Into Stunning',
    stHeroAccent: 'Fashion Photos',
    stHeroLead:
      'Upload your clothing, choose your model and style, and let AI create professional on-model images.',

    // Steps
    stStep1: 'Upload Item',
    stStep2: 'Choose Model',
    stStep3: 'Select Scene',
    stStep4: 'Generate',

    // Left config
    stUploadTitle: 'Upload Your Clothing',
    stUploadTip: 'A flat-lay or white-background shot of a single garment works best',
    stUploadClick: 'Click to upload',
    stUploadOr: 'or drag and drop',
    stUploadHint: 'JPG, PNG (Max 10MB)',
    stUploadPreview: 'Uploaded — click to replace',
    stChooseModel: 'Choose Model',
    stViewAll: 'View All',
    stSelectScene: 'Select Scene',
    stSceneStudio: 'Studio',
    stSceneStreet: 'City Street',
    stSceneCafe: 'Café',
    stSceneGarden: 'Garden',
    stGenerate: 'Generate Images',
    stGenerating: 'Generating…',
    stCostHint: 'Uses 4 credits',

    // Right result
    stBefore: 'Before',
    stAfter: 'After',
    stResultCount: 'results',
    stDownloadAll: 'Download All',
    stAddProject: 'Add to Project',
    stRegenerate: 'Regenerate',
    stDemoHint: 'Demo mode — no credits used, no generation API called',

    // Feedback
    stToastUploaded: 'Garment image uploaded',
    stToastBadFile: 'Please upload a JPG or PNG under 10MB',
    stToastGenerated: '{n} on-model images generated (demo)',
    stToastDownload: 'Demo mode: export available once the download API is wired',
    stToastProject: 'Added to My Projects (demo)',
    stToastRegenerate: 'Results regenerated (demo)',
    stToastLogin: 'Log in to save projects and download results',

    // Stub pages
    stPresetsTitle: 'Brand Presets',
    stPresetsBody:
      'Save your go-to model, scene and colour grade as a preset, then apply it in one click. Coming soon.',
    stModelsTitle: 'Model Library',
    stModelsBody:
      'Manage your own models — different body types, skin tones and looks, ready to switch. Coming soon.',
    stStubBack: 'Back to Create',

    pageTitleCreatePhotos: 'Create Photos - Fashion Studio',
    pageMetaDescCreatePhotos:
      'Upload a garment, pick a model and a scene, and generate professional on-model images with AI.',
  },
};
