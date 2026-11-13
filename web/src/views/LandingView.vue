<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import Icon from '../components/ui/Icon.vue';
import LocaleSwitch from '../components/LocaleSwitch.vue';
import { useT } from '../lib/useT.js';
import { useScrollReveal } from '../composables/useScrollReveal.js';

const { t } = useT();
const root = useScrollReveal();
const scrolled = ref(false);
const menuOpen = ref(false);
const stage = ref(null);
const reduceMotion = ref(false);

const loginHref = '/login?return=' + encodeURIComponent('/clothing-suite');

const TICKER = computed(() => [
  t('suiteGroupWhiteMain'),
  t('suiteGroupPremiumMain'),
  t('suiteGroupScenes'),
  t('suiteGroupDetails'),
  t('suiteGroupSells'),
  t('navPackshot'),
  t('navProductRetouch'),
  t('uiNavTryon'),
]);

const PAIN = computed(() => [
  { n: '01', title: t('ldPain1Title'), body: t('ldPain1Body') },
  { n: '02', title: t('ldPain2Title'), body: t('ldPain2Body') },
  { n: '03', title: t('ldPain3Title'), body: t('ldPain3Body') },
  { n: '04', title: t('ldPain4Title'), body: t('ldPain4Body') },
]);

const STEPS = computed(() => [
  { n: '01', title: t('ldFlow1Title'), body: t('ldFlow1Body') },
  { n: '02', title: t('ldFlow2Title'), body: t('ldFlow2Body') },
  { n: '03', title: t('ldFlow3Title'), body: t('ldFlow3Body') },
]);

const CARDS = computed(() => [
  {
    key: 'suite',
    to: '/clothing-suite',
    img: '/static/landing/ability-suite.jpg',
    kicker: t('ldCardSuiteKicker'),
    title: t('ldCardSuiteTitle'),
    body: t('ldCardSuiteBody'),
  },
  {
    key: 'packshot',
    to: '/model-shots',
    img: '/static/landing/ability-packshot.jpg',
    kicker: t('ldCardModelKicker'),
    title: t('ldCardModelTitle'),
    body: t('ldCardModelBody'),
  },
  {
    key: 'retouch',
    to: '/product-retouch',
    img: '/static/landing/ability-retouch.jpg',
    kicker: t('ldCardRetouchKicker'),
    title: t('ldCardRetouchTitle'),
    body: t('ldCardRetouchBody'),
  },
  {
    key: 'tryon',
    to: '/tryon',
    img: '/static/landing/ability-tryon.jpg',
    kicker: t('ldCardTryonKicker'),
    title: t('ldCardTryonTitle'),
    body: t('ldCardTryonBody'),
  },
]);

const CASES = [
  { img: '/static/landing/model-check.jpg', cap: 'ldCaseOriginal', fit: 'person' },
  { img: '/static/landing/garment-vest.png', cap: 'ldCaseGarment', fit: 'product' },
  { img: '/static/landing/model-vest.jpg', cap: 'ldCaseResult', fit: 'person' },
];

function onScroll() {
  scrolled.value = window.scrollY > 16;
}

function onMove(e) {
  if (reduceMotion.value || !stage.value) return;
  const r = stage.value.getBoundingClientRect();
  const x = (e.clientX - r.left) / r.width - 0.5;
  const y = (e.clientY - r.top) / r.height - 0.5;
  stage.value.style.setProperty('--mx', String(x));
  stage.value.style.setProperty('--my', String(y));
}

function onLeave() {
  if (!stage.value) return;
  stage.value.style.setProperty('--mx', '0');
  stage.value.style.setProperty('--my', '0');
}

function closeMenu() {
  menuOpen.value = false;
}

onMounted(() => {
  reduceMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });
  const hash = window.location.hash;
  if (hash) {
    requestAnimationFrame(() => {
      document.querySelector(hash)?.scrollIntoView({ behavior: reduceMotion.value ? 'auto' : 'smooth' });
    });
  }
});

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll);
});
</script>

<template>
  <div ref="root" class="ld">
    <header class="ld-nav" :class="{ 'ld-nav--on': scrolled }">
      <RouterLink to="/" class="ld-logo" @click="closeMenu">
        <span class="brand-mark" aria-hidden="true">
          <img src="/static/logo-mark.png" alt="" />
        </span>
        FashTry
      </RouterLink>
      <nav class="ld-nav__links" aria-label="page">
        <a href="#ld-flow">{{ t('ldNavFlow') }}</a>
        <a href="#ld-ability">{{ t('ldNavAbility') }}</a>
        <a href="#ld-cases">{{ t('ldNavCases') }}</a>
      </nav>
      <div class="ld-nav__end">
        <span class="ld-nav__locale"><LocaleSwitch /></span>
        <RouterLink :to="loginHref" class="ld-link">{{ t('ldNavLogin') }}</RouterLink>
        <RouterLink to="/clothing-suite" class="ld-btn ld-btn--solid">{{ t('ldCtaTry') }}</RouterLink>
      </div>
      <button
        type="button"
        class="ld-nav__burger"
        :class="{ 'ld-nav__burger--on': menuOpen }"
        :aria-label="t('uiOpenMenu')"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
      >
        <Icon :name="menuOpen ? 'x' : 'menu'" :size="20" />
      </button>
    </header>

    <div v-if="menuOpen" class="ld-sheet">
      <a href="#ld-flow" @click="closeMenu">{{ t('ldNavFlow') }}</a>
      <a href="#ld-ability" @click="closeMenu">{{ t('ldNavAbility') }}</a>
      <a href="#ld-cases" @click="closeMenu">{{ t('ldNavCases') }}</a>
      <RouterLink :to="loginHref" @click="closeMenu">{{ t('ldNavLogin') }}</RouterLink>
      <div class="ld-sheet__locale"><LocaleSwitch /></div>
      <RouterLink to="/clothing-suite" class="ld-btn ld-btn--solid" @click="closeMenu">{{ t('ldCtaTry') }}</RouterLink>
    </div>

    <section class="ld-hero">
      <div class="ld-hero__copy">
        <p class="ld-kicker ld-hero__kicker">{{ t('ldEyebrow') }}</p>
        <h1 class="ld-hero__title">
          <span class="ld-hero__line"><em>{{ t('ldHeroTitle1') }}</em></span>
          <span class="ld-hero__line"><em>{{ t('ldHeroTitle2') }}</em></span>
        </h1>
        <p class="ld-hero__lead">{{ t('ldHeroLead') }}</p>
        <div class="ld-hero__cta">
          <RouterLink to="/clothing-suite" class="ld-btn ld-btn--solid ld-btn--lg">
            {{ t('ldCtaTry') }}
            <span class="ld-btn__shine" aria-hidden="true" />
          </RouterLink>
          <a href="#ld-flow" class="ld-btn ld-btn--ghost ld-btn--lg">{{ t('ldCtaFlow') }}</a>
        </div>
      </div>

      <div
        ref="stage"
        class="ld-stage"
        @mousemove="onMove"
        @mouseleave="onLeave"
      >
        <div class="ld-frame ld-frame--main">
          <div class="ld-frame__bob">
            <img src="/static/landing/model-vest.jpg" alt="" />
          </div>
        </div>
        <div class="ld-frame ld-frame--side">
          <div class="ld-frame__bob">
            <img src="/static/landing/model-check.jpg" alt="" />
          </div>
        </div>
        <div class="ld-frame ld-frame--polaroid">
          <div class="ld-frame__bob">
            <img src="/static/landing/garment-vest.png" alt="" />
            <span>{{ t('ldPolaroidFrom') }}</span>
          </div>
        </div>
        <div class="ld-frame ld-frame--chip">{{ t('ldHeroChip') }}</div>
      </div>
    </section>

    <div class="ld-ticker" aria-hidden="true">
      <div class="ld-ticker__track">
        <span v-for="n in 2" :key="n" class="ld-ticker__run">
          <span v-for="(item, i) in TICKER" :key="n + '-' + i">{{ item }}</span>
        </span>
      </div>
    </div>

    <section class="ld-dark" data-reveal>
      <p class="ld-kicker ld-kicker--brand">{{ t('ldPainKicker') }}</p>
      <h2 class="ld-h2">{{ t('ldPainTitle') }}</h2>
      <p class="ld-lead">{{ t('ldPainLead') }}</p>
      <figure class="ld-pain__visual">
        <img src="/static/landing/studio.jpg" alt="" />
      </figure>
      <div class="ld-pain">
        <article v-for="(p, i) in PAIN" :key="p.n" class="ld-pain__card" :style="{ '--d': i * 80 + 'ms' }">
          <span>{{ p.n }}</span>
          <h3>{{ p.title }}</h3>
          <p>{{ p.body }}</p>
        </article>
      </div>
    </section>

    <section id="ld-flow" class="ld-flow">
      <p class="ld-kicker" data-reveal>{{ t('ldFlowKicker') }}</p>
      <h2 class="ld-h2 ld-h2--ink" data-reveal>{{ t('ldFlowTitle') }}</h2>
      <ol class="ld-steps">
        <li v-for="(s, i) in STEPS" :key="s.n" data-reveal :style="{ '--d': i * 120 + 'ms' }">
          <span class="ld-steps__n">{{ s.n }}</span>
          <h3>{{ s.title }}</h3>
          <p>{{ s.body }}</p>
        </li>
      </ol>
    </section>

    <section id="ld-ability" class="ld-ability">
      <p class="ld-kicker" data-reveal>{{ t('ldAbilityKicker') }}</p>
      <h2 class="ld-h2 ld-h2--ink" data-reveal>{{ t('ldAbilityTitle') }}</h2>
      <div class="ld-bento">
        <RouterLink
          v-for="(c, i) in CARDS"
          :key="c.key"
          :to="c.to"
          class="ld-card"
          data-reveal
          :style="{ '--d': i * 90 + 'ms' }"
        >
          <span class="ld-card__media">
            <img :src="c.img" alt="" />
          </span>
          <span class="ld-card__kicker">{{ c.kicker }}</span>
          <strong>{{ c.title }}</strong>
          <span class="ld-card__body">{{ c.body }}</span>
        </RouterLink>
      </div>
    </section>

    <section id="ld-cases" class="ld-dark ld-cases">
      <p class="ld-kicker ld-kicker--brand" data-reveal>{{ t('ldCasesKicker') }}</p>
      <h2 class="ld-h2" data-reveal>{{ t('ldCasesTitle') }}</h2>
      <p class="ld-lead" data-reveal>{{ t('ldCasesLead') }}</p>
      <div class="ld-strip" data-reveal>
        <figure v-for="shot in CASES" :key="shot.cap" :class="'ld-strip__shot ld-strip__shot--' + shot.fit">
          <img :src="shot.img" alt="" />
          <figcaption>{{ t(shot.cap) }}</figcaption>
        </figure>
      </div>
    </section>

    <section class="ld-end">
      <div class="ld-end__row" data-reveal>
        <div>
          <h2>{{ t('ldFootTitle') }}</h2>
          <p>{{ t('ldFootLead') }}</p>
        </div>
        <RouterLink to="/clothing-suite" class="ld-btn ld-btn--solid ld-btn--lg">
          {{ t('ldCtaTry') }}
          <span class="ld-btn__shine" aria-hidden="true" />
        </RouterLink>
      </div>
      <p class="ld-end__mark" aria-hidden="true">FashTry</p>
      <footer class="ld-foot">
        <span>© 2026 可腾网络科技工作室</span>
        <RouterLink to="/contact">{{ t('navContact') }}</RouterLink>
        <RouterLink to="/brand-join">{{ t('navBrandJoin') }}</RouterLink>
        <RouterLink to="/privacy">{{ t('navPrivacy') }}</RouterLink>
        <RouterLink to="/terms">{{ t('navTerms') }}</RouterLink>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2022144912号-5</a>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.ld {
  /* 与 Fashion Studio 工作台共用同一套浅紫设计令牌 */
  --ld-paper: #faf9fc;
  --ld-soft: #f2eeff;
  --ld-ink: #1b1927;
  --ld-muted: #6c6880;
  --ld-line: rgba(27, 25, 39, 0.10);
  --ld-brand: #6c4cf6;
  --ld-brand-ink: #ffffff;
  --ld-card: #ffffff;
  min-height: 100vh;
  background: var(--ld-paper);
  color: var(--ld-ink);
  overflow-x: hidden;
}

.ld :deep(.lang-dropdown-trigger) {
  border-color: var(--ld-line);
  color: var(--ld-muted);
  background: transparent;
}
.ld :deep(.lang-dropdown-trigger:hover) {
  background: rgba(108, 76, 246, 0.07);
  color: var(--ld-ink);
}
.ld :deep(.lang-dropdown-panel) {
  background: #ffffff;
  border-color: var(--ld-line);
  box-shadow: 0 16px 40px rgba(28, 20, 60, 0.12);
}
.ld :deep(.lang-dropdown-option) {
  color: var(--ld-ink);
}
.ld :deep(.lang-dropdown-option:hover) {
  background: var(--ld-paper);
}
.ld :deep(.lang-dropdown-option.active) {
  color: var(--ld-brand);
}

.ld-nav {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 20px;
  height: 72px;
  padding: 0 28px;
  min-width: 0;
  background: rgba(250, 249, 252, 0.78);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid transparent;
  transition: border-color 0.35s ease, background 0.35s ease, height 0.35s ease;
}
.ld-nav--on {
  height: 64px;
  border-bottom-color: var(--ld-line);
  background: rgba(250, 249, 252, 0.94);
}
.ld-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  color: var(--ld-ink);
  font-weight: 750;
  font-size: 1.05rem;
  letter-spacing: -0.03em;
}
.ld-logo:hover {
  color: var(--ld-ink);
}
.ld-nav__links {
  display: flex;
  gap: 28px;
  margin-left: 18px;
  font-size: 0.86rem;
  font-weight: 550;
}
.ld-nav__links a {
  color: var(--ld-muted);
}
.ld-nav__links a:hover {
  color: var(--ld-ink);
}
.ld-nav__end {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.ld-link {
  color: var(--ld-ink);
  font-size: 0.86rem;
  font-weight: 600;
}
.ld-link:hover {
  color: var(--ld-ink);
  opacity: 0.7;
}
.ld-nav__burger {
  display: none;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border: 1px solid var(--ld-line);
  border-radius: 50%;
  background: transparent;
  color: var(--ld-ink);
  cursor: pointer;
}
.ld-sheet {
  display: none;
  padding: 8px 28px 24px;
  background: var(--ld-paper);
  border-bottom: 1px solid var(--ld-line);
}
.ld-sheet a {
  display: block;
  padding: 12px 0;
  color: var(--ld-ink);
  font-weight: 600;
}

.ld-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), background 0.2s ease;
}
.ld-btn--solid {
  background: var(--ld-brand);
  color: var(--ld-brand-ink);
  box-shadow: 0 6px 18px rgba(108, 76, 246, 0.22);
}
.ld-btn--solid:hover {
  color: var(--ld-brand-ink);
  transform: translateY(-1px);
}
.ld-btn--ghost {
  background: transparent;
  color: var(--ld-ink);
  border-color: var(--ld-line);
}
.ld-btn--ghost:hover {
  color: var(--ld-ink);
  background: rgba(108, 76, 246, 0.06);
}
.ld-btn--lg {
  height: 48px;
  padding: 0 22px;
  font-size: 0.92rem;
}
.ld-btn__shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.45) 48%, transparent 62%);
  transform: translateX(-120%);
  animation: ld-shine 3.2s 1.2s ease-in-out infinite;
}

.ld-hero {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 40px;
  align-items: center;
  background: radial-gradient(880px 540px at 82% 22%, rgba(108, 76, 246, 0.10), transparent 70%);
  min-height: calc(100vh - 72px);
  padding: 48px 7vw 72px;
}
.ld-kicker {
  display: inline-flex;
  margin-bottom: 16px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--ld-line);
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  color: var(--ld-muted);
}
.ld-kicker--brand {
  border-color: rgba(108, 76, 246, 0.28);
  color: var(--ld-brand);
}
.ld-hero__title {
  font-size: clamp(2.4rem, 5.4vw, 4.6rem);
  font-weight: 790;
  line-height: 1.05;
  letter-spacing: -0.045em;
}
.ld-hero__line {
  display: block;
  overflow: hidden;
}
.ld-hero__line em {
  display: block;
  font-style: normal;
  animation: ld-rise 1s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.ld-hero__line:nth-child(2) em {
  animation-delay: 0.12s;
}
.ld-hero__lead {
  max-width: 34rem;
  margin-top: 22px;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--ld-muted);
  animation: ld-fade 0.9s 0.32s both;
}
.ld-hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
  animation: ld-fade 0.9s 0.48s both;
}
.ld-hero__kicker {
  animation: ld-fade 0.8s both;
}

.ld-stage {
  --mx: 0;
  --my: 0;
  position: relative;
  height: min(78vh, 740px);
  animation: ld-fade 1.1s 0.18s both;
}
.ld-frame {
  position: absolute;
  overflow: hidden;
  background: #f4f1ff;
  transition: transform 0.45s cubic-bezier(0.16, 1, 0.3, 1);
}
.ld-frame img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center bottom;
  display: block;
}
.ld-frame--main {
  top: 0;
  right: 0;
  bottom: 0;
  left: 30%;
  border-radius: 22px;
  transform: translate(calc(var(--mx) * -10px), calc(var(--my) * -6px));
  box-shadow: 0 30px 80px rgba(28, 20, 60, 0.16);
  background: #fff;
}
.ld-frame--side {
  width: 28%;
  height: 44%;
  left: 0;
  bottom: 4%;
  right: auto;
  border-radius: 16px;
  background: #fff;
  transform: translate(calc(var(--mx) * 12px), calc(var(--my) * 8px));
  box-shadow: 0 18px 40px rgba(28, 20, 60, 0.14);
}
.ld-frame--polaroid {
  width: 26%;
  left: 0;
  top: 6%;
  padding: 10px 10px 36px;
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 16px 40px rgba(28, 20, 60, 0.16);
  transform: rotate(-8deg) translate(calc(var(--mx) * 22px), calc(var(--my) * 16px));
}
.ld-frame--polaroid img {
  height: 170px;
  animation: none;
  object-fit: contain;
  background: #f2eeff;
}
.ld-frame--polaroid span {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 10px;
  text-align: center;
  font-size: 0.7rem;
  letter-spacing: 0.12em;
  color: var(--ld-muted);
}
.ld-frame--chip {
  left: 2%;
  right: auto;
  top: auto;
  bottom: 48%;
  height: auto;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(23, 19, 43, 0.86);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 650;
  backdrop-filter: blur(8px);
  animation: ld-float 4.5s ease-in-out infinite;
  transform: translate(calc(var(--mx) * 10px), calc(var(--my) * 8px));
}
.ld-frame__bob {
  height: 100%;
  animation: ld-float 6s ease-in-out infinite;
}
.ld-frame--side .ld-frame__bob {
  animation-delay: -2s;
}

.ld-ticker {
  border-top: 1px solid var(--ld-line);
  border-bottom: 1px solid var(--ld-line);
  overflow: hidden;
  padding: 16px 0;
  color: var(--ld-muted);
  font-size: 0.82rem;
  font-weight: 620;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}
.ld-ticker__track {
  display: flex;
  width: max-content;
  animation: ld-marquee 28s linear infinite;
}
.ld-ticker__run {
  display: flex;
  gap: 40px;
  padding-right: 40px;
}

.ld-dark {
  background: var(--ld-soft);
  color: var(--ld-ink);
  padding: 96px 7vw;
}
.ld-dark .ld-lead {
  color: var(--ld-muted);
}
.ld-h2 {
  margin: 10px 0 16px;
  font-size: clamp(2rem, 4vw, 3.4rem);
  font-weight: 780;
  letter-spacing: -0.04em;
  line-height: 1.2;
  text-wrap: balance;
}
.ld-h2--ink {
  color: var(--ld-ink);
}
.ld-pain__visual {
  margin: 0 0 28px;
  overflow: hidden;
  border-radius: 18px;
  max-height: 360px;
}
.ld-pain__visual img {
  width: 100%;
  height: 360px;
  object-fit: cover;
  display: block;
}
.ld-lead {
  max-width: 40rem;
  margin-bottom: 32px;
  font-size: 1.02rem;
  line-height: 1.7;
  color: var(--ld-muted);
}
.ld-pain {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.ld-pain__card {
  padding: 22px 20px 26px;
  border-radius: 16px;
  background: var(--ld-card);
  border: 1px solid var(--ld-line);
  box-shadow: 0 6px 22px rgba(28, 20, 60, 0.05);
  transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1), border-color 0.35s ease;
}
.ld-pain__card:hover {
  transform: translateY(-6px);
  border-color: rgba(108, 76, 246, 0.45);
}
.ld-pain__card span {
  display: block;
  margin-bottom: 28px;
  color: var(--ld-brand);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.ld-pain__card h3 {
  margin-bottom: 8px;
  font-size: 1.15rem;
}
.ld-pain__card p {
  color: var(--ld-muted);
  font-size: 0.88rem;
  line-height: 1.65;
}

.ld-flow,
.ld-ability {
  padding: 96px 7vw;
}
.ld-steps {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
  margin-top: 48px;
  counter-reset: none;
}
.ld-steps li {
  position: relative;
  padding-top: 8px;
}
.ld-steps li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 22px;
  left: 72px;
  right: -16px;
  height: 1px;
  background: linear-gradient(90deg, var(--ld-ink), transparent);
  transform-origin: left;
  animation: ld-line 1.1s 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.ld-steps__n {
  display: block;
  margin-bottom: 16px;
  font-size: 2.4rem;
  font-weight: 780;
  letter-spacing: -0.06em;
  color: rgba(108, 76, 246, 0.24);
}
.ld-steps h3 {
  margin-bottom: 8px;
  font-size: 1.2rem;
}
.ld-steps p {
  color: var(--ld-muted);
  line-height: 1.65;
}

.ld-bento {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 40px;
}
.ld-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid var(--ld-line);
  color: var(--ld-ink);
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.ld-card:hover {
  color: var(--ld-ink);
  transform: translateY(-8px);
}
.ld-card__media {
  display: block;
  overflow: hidden;
  border-radius: 14px;
  aspect-ratio: 3 / 4;
  margin-bottom: 16px;
  background: #fff;
}
.ld-card img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
}
.ld-card__kicker {
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  color: var(--ld-brand);
  margin-bottom: 6px;
}
.ld-card strong {
  font-size: 1.25rem;
  letter-spacing: -0.03em;
}
.ld-card__body {
  margin-top: 8px;
  color: var(--ld-muted);
  font-size: 0.9rem;
  line-height: 1.6;
}

.ld-cases {
  padding-bottom: 0;
}
.ld-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 36px;
}
.ld-strip__shot {
  overflow: hidden;
  border-radius: 18px;
  background: #fff;
  margin: 0;
}
.ld-strip img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: contain;
  object-position: center;
  background: #fff;
  display: block;
}
.ld-strip figcaption {
  padding: 10px 12px 14px;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 650;
  letter-spacing: 0.12em;
  color: var(--ld-muted);
  background: #fff;
}

.ld-end {
  background: linear-gradient(135deg, #6c4cf6 0%, #5535e8 55%, #3f26c4 100%);
  color: #ffffff;
  padding: 72px 7vw 28px;
}
.ld-end .ld-btn--solid {
  background: #ffffff;
  color: var(--ld-brand);
  box-shadow: 0 10px 28px rgba(23, 12, 80, 0.22);
}
.ld-end .ld-btn--solid:hover {
  color: var(--ld-brand);
}
.ld-end .ld-btn__shine {
  display: none;
}
.ld-end__row {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding-bottom: 48px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.24);
}
.ld-end h2 {
  font-size: clamp(1.6rem, 3vw, 2.4rem);
  letter-spacing: -0.04em;
}
.ld-end p {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.82);
}
.ld-end__mark {
  margin: 48px 0 8px;
  font-size: clamp(4.5rem, 18vw, 14rem);
  font-weight: 800;
  letter-spacing: -0.07em;
  line-height: 0.8;
  color: rgba(255, 255, 255, 0.13);
  user-select: none;
}
.ld-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 8px 0 12px;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.74);
}
.ld-foot a {
  color: rgba(255, 255, 255, 0.74);
}
.ld-foot a:hover {
  color: #fff;
}

[data-reveal] {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1);
  transition-delay: var(--d, 0ms);
}
[data-reveal].is-in {
  opacity: 1;
  transform: none;
}

@keyframes ld-rise {
  from {
    transform: translateY(110%);
  }
  to {
    transform: translateY(0);
  }
}
@keyframes ld-fade {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes ld-ken {
  from {
    transform: scale(1.04);
  }
  to {
    transform: scale(1.14) translate(-2%, 1%);
  }
}
@keyframes ld-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
@keyframes ld-shine {
  0%,
  55% {
    transform: translateX(-120%);
  }
  75%,
  100% {
    transform: translateX(120%);
  }
}
@keyframes ld-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}
@keyframes ld-line {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

@media (max-width: 980px) {
  .ld-nav {
    padding: 0 16px;
    gap: 8px;
  }
  .ld-nav__links,
  .ld-nav__end {
    display: none;
  }
  .ld-nav__burger {
    display: flex;
    margin-left: auto;
    align-items: center;
    justify-content: center;
  }
  .ld-sheet {
    display: block;
  }
  .ld-sheet__locale {
    padding: 8px 0 16px;
  }
  .ld-hero {
    grid-template-columns: 1fr;
    min-height: auto;
    padding: 20px 20px 36px;
    gap: 28px;
  }
  .ld-stage {
    height: 520px;
    order: -1;
  }
  .ld-frame--main {
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
  }
  .ld-frame--chip,
  .ld-frame--side,
  .ld-frame--polaroid {
    display: none;
  }
  .ld-pain,
  .ld-steps,
  .ld-bento,
  .ld-strip {
    grid-template-columns: 1fr;
  }
  .ld-steps li:not(:last-child)::after {
    display: none;
  }
  .ld-dark,
  .ld-flow,
  .ld-ability,
  .ld-end {
    padding-left: 22px;
    padding-right: 22px;
  }
  .ld-end__row {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ld-hero__line em,
  .ld-hero__lead,
  .ld-hero__cta,
  .ld-hero__kicker,
  .ld-stage,
  .ld-frame img,
  .ld-frame__bob,
  .ld-frame--chip,
  .ld-ticker__track,
  .ld-btn__shine,
  .ld-steps li::after {
    animation: none !important;
  }
  [data-reveal] {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
