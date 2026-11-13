<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import Icon from '../components/ui/Icon.vue';
import ResultTile from '../components/ui/ResultTile.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import { TOOLS } from '../lib/navItems.js';
import { useT } from '../lib/useT.js';
import { api } from '../lib/api.js';
import { userState } from '../lib/userStore.js';
import { flattenWorks, kindLabel } from '../lib/works.js';

const { t } = useT();

const COVERS = {
  tryon: '/static/home/tool-tryon.jpg',
  batch: '/static/home/tool-batch.jpg',
  packshot: '/static/home/tool-packshot.jpg',
  suite: '/static/home/tool-suite.jpg',
  retouch: '/static/home/tool-retouch.jpg',
  localEdit: '/static/home/tool-localedit.jpg',
  portrait: '/static/home/tool-portrait.jpg',
  modelVideo: '/static/home/tool-video.jpg',
};

const recent = ref([]);
const recentLoading = ref(false);
const lbIndex = ref(-1);

const lbItems = computed(() =>
  recent.value.map((w) => ({ url: w.url, thumbUrl: w.thumb, label: w.label, sourceType: w.kind, sourceTaskId: w.taskId }))
);

async function loadRecent() {
  if (!userState.loggedIn) {
    recent.value = [];
    return;
  }
  recentLoading.value = true;
  try {
    const data = await api.getUnifiedTaskHistory('all', 1, 20);
    recent.value = flattenWorks(data?.records || []).slice(0, 6);
  } catch {
    recent.value = [];
  } finally {
    recentLoading.value = false;
  }
}

watch(
  () => userState.loggedIn,
  () => loadRecent()
);

onMounted(loadRecent);
</script>

<template>
  <div class="page home">
    <section class="hero">
      <img class="hero__img" src="/static/home/hero.jpg" alt="" />
      <div class="hero__text">
        <span v-if="!userState.loggedIn" class="hero__badge"><Icon name="gift" :size="14" />{{ t('uiHomeTrialBadge') }}</span>
        <h1 class="hero__title">{{ t('uiHomeHeroTitle') }}</h1>
        <p class="hero__lead">{{ t('uiHomeHeroLead') }}</p>
        <div class="hero__cta">
          <RouterLink to="/tryon" class="btn btn-primary btn-lg">
            <Icon name="sparkles" :size="17" />{{ t('uiHomeStartTryon') }}
          </RouterLink>
          <RouterLink to="/recharge" class="btn btn-secondary btn-lg">{{ t('uiHomeViewPricing') }}</RouterLink>
        </div>
      </div>
    </section>

    <section class="tools">
      <RouterLink v-for="tool in TOOLS" :key="tool.key" :to="tool.path" class="tool">
        <span class="tool__cover">
          <img :src="COVERS[tool.key]" alt="" loading="lazy" />
          <span v-if="tool.badge" class="tool__badge" :class="'tool__badge--' + tool.badge">
            {{ tool.badge === 'new' ? t('uiBadgeNew') : t('uiBadgeHot') }}
          </span>
        </span>
        <span class="tool__body">
          <strong class="tool__title">{{ t(tool.titleKey) }}</strong>
          <span class="tool__desc">{{ t(tool.descKey) }}</span>
          <span class="tool__cost"><Icon name="bolt" :size="13" />{{ t('uiCostFrom', { n: tool.cost }) }}</span>
        </span>
      </RouterLink>
    </section>

    <section class="block">
      <div class="block__head">
        <h2 class="section-title">{{ t('uiHomeRecent') }}</h2>
        <RouterLink v-if="userState.loggedIn" to="/works" class="block__link">
          {{ t('uiHomeViewAll') }} <Icon name="chevronRight" :size="14" />
        </RouterLink>
      </div>
      <div v-if="!userState.loggedIn" class="recent-empty">
        <Icon name="images" :size="22" />
        <span>{{ t('uiHomeRecentLogin') }}</span>
        <RouterLink to="/login" class="btn btn-primary btn-sm">{{ t('uiLoginRegister') }}</RouterLink>
      </div>
      <div v-else-if="recentLoading && !recent.length" class="recent">
        <div v-for="i in 6" :key="i" class="recent__sk skeleton"></div>
      </div>
      <div v-else-if="!recent.length" class="recent-empty">
        <Icon name="sparkles" :size="22" />
        <span>{{ t('uiHomeRecentEmpty') }}</span>
        <RouterLink to="/tryon" class="btn btn-primary btn-sm">{{ t('uiHomeStartTryon') }}</RouterLink>
      </div>
      <div v-else class="recent">
        <ResultTile
          v-for="(w, i) in recent"
          :key="w.key"
          :url="w.url"
          :thumb-url="w.thumb"
          :label="kindLabel(w.kind)"
          :source-type="w.kind"
          :source-task-id="w.taskId"
          :filename="`fashtry-${w.kind}-${w.taskId}-${i + 1}`"
          @zoom="lbIndex = i"
        />
      </div>
    </section>

    <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-work" />
  </div>
</template>

<style scoped>
.home {
  max-width: 1360px;
}

.hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 236px;
  padding: 32px 40px;
  border-radius: var(--radius-lg);
  background: linear-gradient(115deg, #f3efff 0%, #faf9fc 58%, #f6f2ff 100%);
  border: 1px solid var(--line);
  overflow: hidden;
}
/* 浅色主题下人像图偏暗，给文字区铺一层浅色渐变衬底，避免副标题压进深色区后读不清 */
.hero::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(
    90deg,
    rgba(250, 249, 252, 0.97) 0%,
    rgba(250, 249, 252, 0.9) 34%,
    rgba(250, 249, 252, 0.6) 52%,
    rgba(250, 249, 252, 0) 70%
  );
  pointer-events: none;
}
.hero__img {
  position: absolute;
  z-index: 0;
  top: 0;
  right: 0;
  width: 62%;
  height: 100%;
  object-fit: cover;
  object-position: 80% 22%;
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, #000 38%);
  mask-image: linear-gradient(90deg, transparent 0%, #000 38%);
  pointer-events: none;
}
.hero__text {
  position: relative;
  z-index: 1;
  max-width: 560px;
}
.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--brand);
  background: var(--brand-soft);
  border: 1px solid rgba(108, 76, 246, 0.22);
}
.hero__title {
  font-size: clamp(1.8rem, 3vw, 2.5rem);
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.hero__lead {
  margin-top: 10px;
  font-size: 0.95rem;
  color: var(--text-2);
}
.hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.tools {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}
.tool {
  display: flex;
  flex-direction: column;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
  text-decoration: none;
  overflow: hidden;
  transition: border-color var(--transition), transform var(--transition);
}
.tool:hover {
  color: var(--text);
  border-color: rgba(108, 76, 246, 0.45);
  transform: translateY(-2px);
}
.tool__cover {
  position: relative;
  display: block;
  aspect-ratio: 16 / 7;
  background: var(--panel-2);
  overflow: hidden;
}
.tool__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 30%;
  transition: transform 0.4s ease;
}
.tool:hover .tool__cover img {
  transform: scale(1.04);
}
.tool__badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 700;
}
.tool__badge--hot {
  background: rgba(17, 19, 23, 0.82);
  color: #fbbf24;
}
.tool__badge--new {
  background: var(--brand);
  color: var(--brand-ink);
}
.tool__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px 12px;
}
.tool__title {
  font-size: 0.95rem;
}
.tool__desc {
  font-size: 0.78rem;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tool__cost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 0.75rem;
  color: var(--text-3);
}

.block {
  margin-top: 24px;
}
.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.block__link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 0.85rem;
  font-size: 0.82rem;
  color: var(--text-2);
}
.block__link:hover {
  color: var(--brand);
}

.recent {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
.recent__sk {
  aspect-ratio: 3 / 4;
  border-radius: var(--radius);
}
.recent-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 32px 16px;
  border-radius: var(--radius-lg);
  border: 1px dashed var(--line-strong);
  background: var(--bg-elev);
  color: var(--text-2);
  font-size: 0.875rem;
}
.recent-empty > :deep(.icon) {
  color: var(--brand);
}

@media (max-width: 1200px) {
  .tools {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .recent {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (min-width: 769px) and (max-width: 1200px) {
  .recent > :nth-child(n + 5) {
    display: none;
  }
}
@media (max-width: 768px) {
  .hero {
    min-height: 0;
    padding: 24px 18px;
  }
  .hero__img {
    width: 100%;
    opacity: 0.45;
    object-position: 85% 20%;
    -webkit-mask-image: linear-gradient(90deg, transparent 0%, #000 60%);
    mask-image: linear-gradient(90deg, transparent 0%, #000 60%);
  }
  .hero__cta .btn {
    flex: 1;
  }
  .tools {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
  .recent {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
