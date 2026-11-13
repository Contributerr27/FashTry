import { createRouter, createWebHistory } from 'vue-router';
import { getToken } from '../lib/auth.js';

const routes = [
  {
    path: '/login',
    name: 'admin-login',
    component: () => import('../views/AdminLoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('../views/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: { name: 'admin-dashboard' } },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('../views/AdminDashboardView.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../views/AdminUsersView.vue'),
      },
      {
        path: 'tasks',
        name: 'admin-tasks',
        component: () => import('../views/AdminTasksView.vue'),
      },
      { path: 'tryon-tasks', redirect: (to) => ({ path: '/tasks', query: to.query }) },
      { path: 'product-to-model-tasks', redirect: (to) => ({ path: '/tasks', query: to.query }) },
      {
        path: 'user-uploads',
        name: 'admin-user-uploads',
        component: () => import('../views/AdminUserUploadsView.vue'),
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('../views/AdminOrdersView.vue'),
      },
      {
        path: 'credit-records',
        name: 'admin-credits',
        component: () => import('../views/AdminCreditRecordsView.vue'),
      },
      {
        path: 'packages',
        name: 'admin-packages',
        component: () => import('../views/AdminPackagesView.vue'),
      },
      {
        path: 'public-models',
        name: 'admin-models',
        component: () => import('../views/AdminPublicModelsView.vue'),
      },
      {
        path: 'public-garments',
        name: 'admin-garments',
        component: () => import('../views/AdminPublicGarmentsView.vue'),
      },
      {
        path: 'brands',
        name: 'admin-brands',
        component: () => import('../views/AdminBrandsView.vue'),
      },
      {
        path: 'brands/:brandId/garments',
        name: 'admin-brand-garments',
        component: () => import('../views/AdminBrandGarmentsView.vue'),
      },
      {
        path: 'brands/:brandId/users',
        name: 'admin-brand-users',
        component: () => import('../views/AdminBrandUsersView.vue'),
      },
      {
        path: 'feedback',
        name: 'admin-feedback',
        component: () => import('../views/AdminFeedbackView.vue'),
      },
      {
        path: 'gemini-keys',
        name: 'admin-gemini-keys',
        component: () => import('../views/AdminGeminiKeysView.vue'),
      },
      {
        path: 'register-gift',
        name: 'admin-register-gift',
        component: () => import('../views/AdminRegisterGiftView.vue'),
      },
      {
        path: 'register-abuse',
        name: 'admin-register-abuse',
        component: () => import('../views/AdminRegisterAbuseView.vue'),
      },
      {
        path: 'password',
        name: 'admin-password',
        component: () => import('../views/AdminPasswordView.vue'),
      },
      { path: ':pathMatch(.*)*', redirect: { name: 'admin-dashboard' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
];

const router = createRouter({
  history: createWebHistory('/admin0522/'),
  routes,
});

router.beforeEach((to) => {
  if (to.meta.public) return true;
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'admin-login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
