<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { adminMe, adminStatsOverview } from '../lib/api.js';

/** 动态加载，避免其它后台页打进 echarts */
let echartsLib = null;
async function ensureEcharts() {
  if (!echartsLib) {
    const m = await import('echarts');
    echartsLib = m.default ?? m;
  }
  return echartsLib;
}

const data = ref(null);
const profile = ref(null);
const err = ref('');
const loading = ref(false);

const trendChartRef = ref(null);
const barChartRef = ref(null);
let chartTrend = null;
let chartBar = null;

function toNum(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function fmtInt(n) {
  if (n == null || !Number.isFinite(Number(n))) return '—';
  return Number(n).toLocaleString('zh-CN');
}

function fmtMoney(v) {
  if (v == null) return '—';
  const x = typeof v === 'number' ? v : Number.parseFloat(String(v));
  if (!Number.isFinite(x)) return String(v);
  return x.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatShortDate(iso) {
  if (!iso) return '';
  const p = String(iso).split('-');
  if (p.length === 3) return `${Number(p[1])}/${Number(p[2])}`;
  return iso;
}

/** 展示用：兼容后端 yyyy-MM-dd HH:mm:ss 与旧版 ISO（含纳秒） */
function fmtGeneratedAt(raw) {
  if (raw == null || raw === '') return '—';
  let s = String(raw).trim();
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(s)) {
    s = s.replace(' ', 'T');
  }
  s = s.replace(/(\.\d{3})\d+/, '$1');
  const d = new Date(s);
  if (!Number.isFinite(d.getTime())) return String(raw);
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

/** 仅统计真实任务提交（试穿 + 三类图像任务），不含用户上传记录 */
function rowTaskSum(r) {
  if (!r) return 0;
  return (
    toNum(r.tryonTasksCreated) +
    toNum(r.localEditTasksCreated) +
    toNum(r.clothingImageTasksCreated) +
    toNum(r.modelImageTasksCreated)
  );
}

const welcomeLine = computed(() => {
  const name = profile.value?.username?.trim();
  if (name) return `欢迎回来，${name}。这是平台业务与任务使用概览。`;
  return '欢迎回来。这是平台业务与任务使用概览。';
});

const heroCards = computed(() => {
  const d = data.value;
  if (!d) return [];
  const daily = d.dailyLast14Days || [];
  const todayRow = daily.length ? daily[daily.length - 1] : null;
  const todaySum = rowTaskSum(todayRow);
  const weekSum =
    toNum(d.tryonTasksCreatedLast7Days) +
    toNum(d.localEditTasksCreatedLast7Days) +
    toNum(d.clothingImageTasksCreatedLast7Days) +
    toNum(d.modelImageTasksCreatedLast7Days);
  const totalTask =
    toNum(d.totalTryonTasks) +
    toNum(d.totalLocalEditTasks) +
    toNum(d.totalClothingImageTasks) +
    toNum(d.totalModelImageTasks);
  const done = toNum(d.tryonStatusDone);
  const fail = toNum(d.tryonStatusFailed);
  const rate = done + fail > 0 ? ((done / (done + fail)) * 100).toFixed(1) : '—';

  return [
    {
      key: 'today',
      label: '今日任务提交',
      value: fmtInt(todaySum),
      hint: '当日试穿、局部改款、服装出图、模特出图新建合计（不含上传）',
      icon: 'activity',
    },
    {
      key: 'week',
      label: '近 7 日任务量',
      value: fmtInt(weekSum),
      hint: '近 7 日上述任务新建合计（不含上传）',
      icon: 'calendar',
    },
    {
      key: 'total',
      label: '累计任务量',
      value: fmtInt(totalTask),
      hint: '试穿 + 局部改款 + 服装出图 + 模特出图（历史累计）',
      icon: 'layers',
    },
    {
      key: 'rate',
      label: '试穿完成率',
      value: rate === '—' ? '—' : `${rate}%`,
      hint: '完成 /（完成 + 失败），按试穿任务统计',
      icon: 'chart',
      valueSm: true,
    },
  ];
});

const extraKpis = computed(() => {
  const d = data.value;
  if (!d) return [];
  return [
    { label: '注册用户', value: fmtInt(d.totalUsers) },
    { label: '今日新增用户', value: fmtInt(d.newUsersToday) },
    { label: '近 7 日新增用户', value: fmtInt(d.newUsersLast7Days) },
    { label: '近 30 日新增用户', value: fmtInt(d.newUsersLast30Days) },
    { label: '品牌方', value: fmtInt(d.totalBrands) },
    { label: '已支付订单（笔）', value: fmtInt(d.paidOrdersTotal) },
    { label: '已支付金额合计', value: fmtMoney(d.paidOrdersAmountTotal) },
    { label: '近 7 日支付订单', value: fmtInt(d.paidOrdersLast7Days) },
    { label: '累计充值点数', value: fmtInt(d.walletTotalRechargedCredits) },
    { label: '累计充值金额（元）', value: fmtMoney(d.walletTotalRechargedAmount) },
    { label: '用户上传记录（累计）', value: fmtInt(d.totalUserUploads) },
    { label: '近 7 日上传次数', value: fmtInt(d.userUploadsLast7Days) },
    { label: '试穿排队 / 进行中', value: `${fmtInt(d.tryonStatusQueued)} / ${fmtInt(d.tryonStatusProcessing)}` },
    { label: '试穿完成消耗点数', value: fmtInt(d.tryonCreditsConsumedDone) },
    { label: '局部改款成功消耗点数', value: fmtInt(d.localEditCreditsConsumedSuccess) },
    { label: '服装出图成功消耗点数', value: fmtInt(d.clothingImageCreditsConsumedSuccess) },
    { label: '模特出图成功消耗点数', value: fmtInt(d.modelImageCreditsConsumedSuccess) },
  ];
});

function disposeCharts() {
  chartTrend?.dispose();
  chartBar?.dispose();
  chartTrend = null;
  chartBar = null;
}

function buildTrendOption(d, ec) {
  const daily = d.dailyLast14Days || [];
  const last7 = daily.slice(-7);
  const labels = last7.map((r) => formatShortDate(r.date));
  const totals = last7.map((r) => rowTaskSum(r));
  return {
    grid: { left: '10%', right: '4%', bottom: '14%', top: '12%' },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(22, 27, 34, 0.95)',
      borderColor: 'rgba(255,255,255,0.1)',
      textStyle: { color: '#e6edf3', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#30363d' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)', type: 'dashed' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    series: [
      {
        type: 'line',
        data: totals,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { color: '#2dd4bf', width: 2 },
        itemStyle: { color: '#2dd4bf', borderColor: '#0d1117', borderWidth: 2 },
        areaStyle: {
          color: new ec.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0, 149, 255, 0.28)' },
            { offset: 1, color: 'rgba(0, 149, 255, 0)' },
          ]),
        },
      },
    ],
  };
}

function buildBarOption(d) {
  const daily = d.dailyLast14Days || [];
  const sums = daily.reduce(
    (acc, r) => ({
      tryon: acc.tryon + toNum(r.tryonTasksCreated),
      localEdit: acc.localEdit + toNum(r.localEditTasksCreated),
      clothing: acc.clothing + toNum(r.clothingImageTasksCreated),
      modelImg: acc.modelImg + toNum(r.modelImageTasksCreated),
    }),
    { tryon: 0, localEdit: 0, clothing: 0, modelImg: 0 }
  );
  const pairs = [
    { name: '试穿', value: sums.tryon },
    { name: '局部改款', value: sums.localEdit },
    { name: '服装出图', value: sums.clothing },
    { name: '模特出图', value: sums.modelImg },
  ].sort((a, b) => a.value - b.value);
  const names = pairs.map((p) => p.name);
  const values = pairs.map((p) => p.value);
  return {
    grid: { left: '30%', right: '10%', bottom: '6%', top: '4%' },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(22, 27, 34, 0.95)',
      borderColor: 'rgba(255,255,255,0.1)',
      textStyle: { color: '#e6edf3', fontSize: 12 },
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)', type: 'dashed' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: names,
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: values,
        barMaxWidth: 22,
        itemStyle: {
          color: '#2dd4bf',
          borderRadius: [0, 6, 6, 0],
        },
      },
    ],
  };
}

function resizeCharts() {
  chartTrend?.resize();
  chartBar?.resize();
}

async function renderCharts() {
  const d = data.value;
  await nextTick();
  disposeCharts();
  if (!d || !trendChartRef.value || !barChartRef.value) return;
  const ec = await ensureEcharts();
  if (typeof ec?.init !== 'function') {
    console.error('[dashboard] echarts 未正确加载', ec);
    return;
  }
  chartTrend = ec.init(trendChartRef.value, null, { renderer: 'canvas' });
  chartBar = ec.init(barChartRef.value, null, { renderer: 'canvas' });
  chartTrend.setOption(buildTrendOption(d, ec));
  chartBar.setOption(buildBarOption(d));
  requestAnimationFrame(() => {
    chartTrend?.resize();
    chartBar?.resize();
  });
}

async function load() {
  err.value = '';
  loading.value = true;
  try {
    data.value = await adminStatsOverview();
  } catch (e) {
    err.value = e.message || '加载失败';
    data.value = null;
  } finally {
    loading.value = false;
  }
}

watch(
  () => data.value,
  async (d) => {
    if (!d) {
      disposeCharts();
      return;
    }
    await renderCharts();
  },
  { flush: 'post' }
);

onMounted(async () => {
  try {
    profile.value = await adminMe();
  } catch {
    profile.value = null;
  }
  window.addEventListener('resize', resizeCharts);
  await load();
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts);
  disposeCharts();
});
</script>

<template>
  <div class="admin-page dash-console-page">
    <div class="dash-console">
      <header class="dash-console-head">
        <div>
          <h1 class="dash-console-title">数据概览</h1>
          <p class="dash-console-sub">{{ welcomeLine }}</p>
        </div>
        <button type="button" class="dash-console-cta" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新数据' }}
        </button>
      </header>

      <p v-if="err" class="dash-console-err">{{ err }}</p>

      <template v-else-if="data">
        <p class="dash-console-meta">
          生成时间 {{ fmtGeneratedAt(data.generatedAt) }} · {{ data.dayCutTimezone }}
        </p>
        <p class="dash-console-note">{{ data.trafficNote }}</p>

        <div class="dash-console-cards">
          <div v-for="card in heroCards" :key="card.key" class="dash-console-card">
            <div class="dash-console-card-top">
              <div>
                <p class="dash-console-card-label">{{ card.label }}</p>
                <p class="dash-console-card-value" :class="{ 'is-sm': card.valueSm }">{{ card.value }}</p>
                <p class="dash-console-card-hint">{{ card.hint }}</p>
              </div>
              <div class="dash-console-card-icon" aria-hidden="true">
                <svg v-if="card.icon === 'activity'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                </svg>
                <svg v-else-if="card.icon === 'calendar'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                  <path d="M16 2v4M8 2v4M3 10h18" />
                </svg>
                <svg v-else-if="card.icon === 'layers'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2L2 7l10 5 10-5-10-5z" />
                  <path d="M2 17l10 5 10-5M2 12l10 5 10-5" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 3v18h18" />
                  <path d="M7 16l4-4 4 4 6-6" />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <div class="dash-console-charts">
          <div class="dash-console-panel">
            <h3 class="dash-console-panel-title">近 7 日任务趋势（不含上传）</h3>
            <div ref="trendChartRef" class="dash-console-chart" />
          </div>
          <div class="dash-console-panel">
            <h3 class="dash-console-panel-title">近 14 日任务分布（按类型，不含上传）</h3>
            <div ref="barChartRef" class="dash-console-chart" />
          </div>
        </div>

        <h3 class="dash-console-section-title">更多指标</h3>
        <div class="dash-console-grid">
          <div v-for="(k, i) in extraKpis" :key="i" class="dash-console-kpi">
            <div class="dash-console-kpi-label">{{ k.label }}</div>
            <div class="dash-console-kpi-value">{{ k.value }}</div>
          </div>
        </div>

        <h3 class="dash-console-section-title">近 14 日按日明细</h3>
        <div class="dash-console-table-wrap">
          <table class="dash-console-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>新用户</th>
                <th>试穿</th>
                <th>局部改款</th>
                <th>服装出图</th>
                <th>模特出图</th>
                <th>上传</th>
                <th>支付笔数</th>
                <th>支付金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.dailyLast14Days" :key="row.date">
                <td>{{ row.date }}</td>
                <td>{{ fmtInt(row.newUsers) }}</td>
                <td>{{ fmtInt(row.tryonTasksCreated) }}</td>
                <td>{{ fmtInt(row.localEditTasksCreated) }}</td>
                <td>{{ fmtInt(row.clothingImageTasksCreated) }}</td>
                <td>{{ fmtInt(row.modelImageTasksCreated) }}</td>
                <td>{{ fmtInt(row.userUploads) }}</td>
                <td>{{ fmtInt(row.paidOrders) }}</td>
                <td>{{ fmtMoney(row.paidAmount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <p v-else-if="!loading" class="dash-console-empty">暂无数据</p>
    </div>
  </div>
</template>
