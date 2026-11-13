import { createRouter, createWebHistory } from 'vue-router';
import {
  ONBOARDING_TRYON_PATH,
  shouldShowOnboardingTryon,
} from '../lib/onboardingTryonGate.js';

/** 路由级 SEO：titleKey / descriptionKey 对应 i18n 中的 pageTitle* / pageMetaDesc* */
const seo = (titleKey, descriptionKey) => ({ seo: { titleKey, descriptionKey } });

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/IndexView.vue'),
    meta: { showNav: true, ...seo('pageTitleIndex', 'pageMetaDescIndex') },
  },
  {
    path: '/create-photos',
    name: 'createPhotos',
    component: () => import('../views/CreatePhotosView.vue'),
    meta: { showNav: true, ...seo('pageTitleCreatePhotos', 'pageMetaDescCreatePhotos') },
  },
  {
    path: '/tryon',
    name: 'tryon',
    component: () => import('../views/TryonView.vue'),
    meta: { showNav: true, ...seo('pageTitleTryon', 'pageMetaDescTryon') },
  },
  {
    path: '/works',
    name: 'works',
    component: () => import('../views/WorksView.vue'),
    meta: { showNav: true, ...seo('pageTitleWorks', 'pageMetaDescWorks') },
  },
  {
    path: '/batch-tryon',
    name: 'batchTryon',
    component: () => import('../views/BatchTryonView.vue'),
    meta: { showNav: true, ...seo('pageTitleBatchTryon', 'pageMetaDescBatchTryon') },
  },
  {
    path: '/local-edit',
    name: 'localEdit',
    component: () => import('../views/LocalEditView.vue'),
    meta: { showNav: true, ...seo('pageTitleLocalEdit', 'pageMetaDescLocalEdit') },
  },
  {
    path: '/model-shots',
    name: 'modelShots',
    component: () => import('../views/ClothingPackshotView.vue'),
    meta: { showNav: true, ...seo('pageTitleModelShots', 'pageMetaDescModelShots') },
  },
  { path: '/clothing-packshot', redirect: '/model-shots' },
  {
    path: '/clothing-suite',
    name: 'clothingSuite',
    component: () => import('../views/ClothingProductSuiteView.vue'),
    meta: { showNav: true, ...seo('pageTitleProductSuite', 'pageMetaDescProductSuite') },
  },
  {
    path: '/product-retouch',
    name: 'productRetouch',
    component: () => import('../views/ProductRetouchView.vue'),
    meta: { showNav: true, ...seo('pageTitleProductRetouch', 'pageMetaDescProductRetouch') },
  },
  {
    path: '/portrait-studio',
    name: 'portraitStudio',
    component: () => import('../views/PortraitStudioView.vue'),
    meta: { showNav: true, ...seo('pageTitlePortraitStudio', 'pageMetaDescPortraitStudio') },
  },
  {
    path: '/model-video',
    name: 'modelVideo',
    component: () => import('../views/ModelVideoView.vue'),
    meta: { showNav: true, ...seo('pageTitleModelVideo', 'pageMetaDescModelVideo') },
  },
  { path: '/xhs-card', redirect: '/' },
  {
    path: '/create',
    name: 'create',
    component: () => import('../views/MobileCreateView.vue'),
    meta: { showNav: true, ...seo('pageTitleCreate', 'pageMetaDescCreate') },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { showNav: false, ...seo('pageTitleLogin', 'pageMetaDescLogin') },
  },
  {
    path: '/recharge',
    name: 'recharge',
    component: () => import('../views/RechargeView.vue'),
    meta: { showNav: true, ...seo('pageTitleRecharge', 'pageMetaDescRecharge') },
  },
  {
    path: '/wallet',
    name: 'wallet',
    component: () => import('../views/WalletView.vue'),
    meta: { showNav: true, ...seo('pageTitleWallet', 'pageMetaDescWallet') },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { showNav: true, ...seo('pageTitleProfile', 'pageMetaDescProfile') },
  },
  {
    path: '/invite',
    name: 'invite',
    component: () => import('../views/InviteView.vue'),
    meta: { showNav: true, ...seo('pageTitleInvite', 'pageMetaDescInvite') },
  },
  {
    path: '/contact',
    name: 'contact',
    component: () => import('../views/ContactView.vue'),
    meta: { showNav: true, ...seo('pageTitleContact', 'pageMetaDescContact') },
  },
  {
    path: '/feedback',
    name: 'feedback',
    component: () => import('../views/FeedbackView.vue'),
    meta: { showNav: true, ...seo('pageTitleFeedback', 'pageMetaDescFeedback') },
  },
  {
    path: '/brand-join',
    name: 'brandJoin',
    component: () => import('../views/BrandJoinView.vue'),
    meta: { showNav: true, ...seo('pageTitleBrandJoin', 'pageMetaDescBrandJoin') },
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: () => import('../views/PrivacyView.vue'),
    meta: { showNav: true, ...seo('pageTitlePrivacy', 'pageMetaDescPrivacy') },
  },
  {
    path: '/terms',
    name: 'terms',
    component: () => import('../views/TermsView.vue'),
    meta: { showNav: true, ...seo('pageTitleTerms', 'pageMetaDescTerms') },
  },
  {
    path: '/commercial-license',
    name: 'commercialLicense',
    component: () => import('../views/CommercialLicenseView.vue'),
    meta: { showNav: true, ...seo('pageTitleCommercialLicense', 'pageMetaDescCommercialLicense') },
  },
  {
    path: '/developer',
    name: 'developer',
    component: () => import('../views/DeveloperConsoleView.vue'),
    meta: { showNav: true, ...seo('pageTitleDeveloper', 'pageMetaDescDeveloper') },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, saved) {
    if (saved) return saved;
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth', top: 80 };
    }
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  const pending = shouldShowOnboardingTryon();
  if (pending && to.name !== 'tryon' && to.name !== 'login') {
    return { path: ONBOARDING_TRYON_PATH };
  }
  return true;
});

export default router;
