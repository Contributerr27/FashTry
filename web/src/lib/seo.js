/**
 * PC 端 SEO：title、description、canonical、Open Graph、Twitter Card、首页 JSON-LD。
 * 站点根 URL 可通过 VITE_SITE_URL 配置（构建时），默认 https://fashtry.com
 */
import { i18n } from './i18n.js';

const SITE_URL = String(import.meta.env.VITE_SITE_URL || 'https://fashtry.com').replace(/\/$/, '');
const OG_IMAGE = `${SITE_URL}/static/logo.png`;
const JSON_LD_ID = 'fashtry-jsonld';

/** titleKey 与 descriptionKey 的默认映射（pageTitleX → pageMetaDescX） */
export function descriptionKeyFromTitleKey(titleKey) {
  if (!titleKey || typeof titleKey !== 'string') return 'pageMetaDescIndex';
  if (titleKey.startsWith('pageTitle')) {
    return 'pageMetaDesc' + titleKey.slice('pageTitle'.length);
  }
  return 'pageMetaDescIndex';
}

export function keywordsKeyFromTitleKey(titleKey) {
  if (!titleKey || typeof titleKey !== 'string') return 'pageMetaKeywordsIndex';
  if (titleKey.startsWith('pageTitle')) {
    return 'pageMetaKeywords' + titleKey.slice('pageTitle'.length);
  }
  return 'pageMetaKeywordsIndex';
}

function absoluteUrl(path) {
  const p = path && path !== '/' ? path.replace(/\/$/, '') : '/';
  return p === '/' ? `${SITE_URL}/` : `${SITE_URL}${p.startsWith('/') ? p : `/${p}`}`;
}

function upsertMetaByName(name, content) {
  if (!content) return;
  let el = document.head.querySelector(`meta[name="${name}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute('name', name);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

function upsertMetaByProperty(property, content) {
  if (!content) return;
  let el = document.head.querySelector(`meta[property="${property}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute('property', property);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

function upsertLink(rel, href) {
  if (!href) return;
  let el = document.head.querySelector(`link[rel="${rel}"]`);
  if (!el) {
    el = document.createElement('link');
    el.setAttribute('rel', rel);
    document.head.appendChild(el);
  }
  el.setAttribute('href', href);
}

function removeJsonLd() {
  const el = document.getElementById(JSON_LD_ID);
  if (el) el.remove();
}

function applyHomeJsonLd() {
  removeJsonLd();
  const isZh = i18n.getLocale() === 'zh';
  const data = {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Organization',
        '@id': `${SITE_URL}/#organization`,
        name: 'FashTry',
        url: SITE_URL,
        logo: OG_IMAGE,
        description: i18n.t('pageMetaDescIndex'),
      },
      {
        '@type': 'WebSite',
        '@id': `${SITE_URL}/#website`,
        url: SITE_URL,
        name: 'FashTry',
        description: i18n.t('pageMetaDescIndex'),
        publisher: { '@id': `${SITE_URL}/#organization` },
        inLanguage: isZh ? 'zh-CN' : 'en',
      },
      {
        '@type': 'SoftwareApplication',
        '@id': `${SITE_URL}/#app`,
        name: isZh ? 'FashTry AI时尚换装' : 'FashTry AI Fashion Try-on',
        applicationCategory: 'DesignApplication',
        operatingSystem: 'Web',
        url: SITE_URL,
        description: i18n.t('pageMetaDescIndex'),
        offers: {
          '@type': 'Offer',
          price: '0',
          priceCurrency: 'CNY',
          description: isZh ? '注册即可免费体验' : 'Free trial on sign-up',
        },
      },
    ],
  };
  const script = document.createElement('script');
  script.id = JSON_LD_ID;
  script.type = 'application/ld+json';
  script.textContent = JSON.stringify(data);
  document.head.appendChild(script);
}

/**
 * 根据 i18n 键应用全套 SEO 标签。
 * @param {{ titleKey: string, descriptionKey?: string, path?: string }} opts
 */
export function applySeo(opts) {
  const titleKey = opts.titleKey || 'pageTitleIndex';
  const descriptionKey = opts.descriptionKey || descriptionKeyFromTitleKey(titleKey);
  const keywordsKey = keywordsKeyFromTitleKey(titleKey);
  const path = opts.path || (typeof location !== 'undefined' ? location.pathname : '/');

  const title = i18n.t(titleKey);
  const description = i18n.t(descriptionKey);
  const keywords = i18n.t(keywordsKey);
  const url = absoluteUrl(path);
  const locale = i18n.getLocale() === 'zh' ? 'zh_CN' : 'en_US';

  document.title = title;

  upsertMetaByName('description', description);
  upsertMetaByName('keywords', keywords);
  upsertMetaByName('robots', 'index, follow');

  upsertLink('canonical', url);

  upsertMetaByProperty('og:title', title);
  upsertMetaByProperty('og:description', description);
  upsertMetaByProperty('og:url', url);
  upsertMetaByProperty('og:image', OG_IMAGE);
  upsertMetaByProperty('og:type', 'website');
  upsertMetaByProperty('og:site_name', 'FashTry');
  upsertMetaByProperty('og:locale', locale);

  upsertMetaByName('twitter:card', 'summary_large_image');
  upsertMetaByName('twitter:title', title);
  upsertMetaByName('twitter:description', description);
  upsertMetaByName('twitter:image', OG_IMAGE);

  if (path === '/' || path === '') {
    applyHomeJsonLd();
  } else {
    removeJsonLd();
  }
}

/** 从 vue-router 路由记录更新 SEO */
export function updateSeoForRoute(route) {
  if (!route) return;
  const seo = route.meta?.seo;
  if (seo?.titleKey) {
    applySeo({
      titleKey: seo.titleKey,
      descriptionKey: seo.descriptionKey,
      path: route.path,
    });
    return;
  }
  applySeoFromPath(route.path);
}

/** 无路由 meta 时按路径回退（与历史 updatePageTitle 逻辑一致） */
export function applySeoFromPath(pathname) {
  const path = (pathname || '/').replace(/\/index\.html$/, '/') || '/';
  let titleKey = 'pageTitleIndex';
  if (path.includes('/login')) titleKey = 'pageTitleLogin';
  else if (path.includes('/recharge')) titleKey = 'pageTitleRecharge';
  else if (path.includes('/invite')) titleKey = 'pageTitleInvite';
  else if (path.includes('/wallet')) titleKey = 'pageTitleWallet';
  else if (path.includes('/developer')) titleKey = 'pageTitleDeveloper';
  else if (path.includes('/profile')) titleKey = 'pageTitleProfile';
  else if (path.includes('/privacy')) titleKey = 'pageTitlePrivacy';
  else if (path.includes('/terms')) titleKey = 'pageTitleTerms';
  else if (path.includes('/commercial-license')) titleKey = 'pageTitleCommercialLicense';
  else if (path.includes('/contact')) titleKey = 'pageTitleContact';
  else if (path.includes('/feedback')) titleKey = 'pageTitleFeedback';
  else if (path.includes('/brand-join')) titleKey = 'pageTitleBrandJoin';
  else if (path === '/create' || path.startsWith('/create/')) titleKey = 'pageTitleCreate';
  else if (path.includes('/batch-tryon')) titleKey = 'pageTitleBatchTryon';
  else if (path === '/tryon') titleKey = 'pageTitleTryon';
  else if (path === '/works') titleKey = 'pageTitleWorks';
  else if (path.includes('/local-edit')) titleKey = 'pageTitleLocalEdit';
  else if (path.includes('/clothing-suite')) titleKey = 'pageTitleProductSuite';
  else if (path.includes('/product-retouch')) titleKey = 'pageTitleProductRetouch';
  else if (path.includes('/portrait-studio')) titleKey = 'pageTitlePortraitStudio';
  else if (path.includes('/model-shots') || path.includes('/clothing-packshot')) {
    titleKey = 'pageTitleModelShots';
  }

  applySeo({ titleKey, path });
}

export const seoSiteUrl = SITE_URL;
