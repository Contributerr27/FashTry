<script setup>
import { ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  adminPageBrands,
  adminCreateBrand,
  adminUpdateBrand,
  adminDeleteBrand,
  adminAuditBrand,
  adminUploadBrandLogo,
  adminUploadBrandBusinessLicense,
  adminUploadBrandLegalIdFront,
  adminUploadBrandLegalIdBack,
  adminGetBrandEntry,
} from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';

const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const auditFilter = ref('');
const statusFilter = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');

const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);

const qrModal = ref(false);
const qrLoading = ref(false);
const qrErr = ref('');
const qrData = ref(null);

const auditModal = ref(false);
const auditTarget = ref(null);
const auditForm = ref({ auditStatus: 2, remark: '' });
const auditErr = ref('');
const auditing = ref(false);

const logoUploading = ref(false);
const logoInputRef = ref(null);

const bizLicenseUploading = ref(false);
const bizLicenseInputRef = ref(null);
const legalIdFrontUploading = ref(false);
const legalIdFrontInputRef = ref(null);
const legalIdBackUploading = ref(false);
const legalIdBackInputRef = ref(null);

function emptyForm() {
  return {
    code: '',
    brandName: '',
    brandNameEn: '',
    companyName: '',
    companyNameEn: '',
    logoUrl: '',
    unifiedSocialCreditCode: '',
    businessLicenseUrl: '',
    legalName: '',
    legalIdFrontUrl: '',
    legalIdBackUrl: '',
    legalIdNoMasked: '',
    legalMobile: '',
    contactEmail: '',
    companyAddress: '',
    longitude: '',
    latitude: '',
    locationPrecision: '',
    auditStatus: '1',
    status: '1',
    remark: '',
  };
}

function brandAuditLabel(v) {
  const m = { 0: '待完善', 1: '待审核', 2: '已通过', 3: '已驳回' };
  return m[v] ?? v;
}

function openCreate() {
  editingId.value = null;
  form.value = emptyForm();
  formErr.value = '';
  modalOpen.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  form.value = {
    code: row.code ?? '',
    brandName: row.brandName ?? '',
    brandNameEn: row.brandNameEn ?? '',
    companyName: row.companyName ?? '',
    companyNameEn: row.companyNameEn ?? '',
    logoUrl: row.logoUrl ?? '',
    unifiedSocialCreditCode: row.unifiedSocialCreditCode ?? '',
    businessLicenseUrl: row.businessLicenseUrl ?? '',
    legalName: row.legalName ?? '',
    legalIdFrontUrl: row.legalIdFrontUrl ?? '',
    legalIdBackUrl: row.legalIdBackUrl ?? '',
    legalIdNoMasked: row.legalIdNoMasked ?? '',
    legalMobile: row.legalMobile ?? '',
    contactEmail: row.contactEmail ?? '',
    companyAddress: row.companyAddress ?? '',
    longitude: row.longitude != null ? String(row.longitude) : '',
    latitude: row.latitude != null ? String(row.latitude) : '',
    locationPrecision: row.locationPrecision ?? '',
    auditStatus: String(row.auditStatus ?? 1),
    status: String(row.status ?? 1),
    remark: row.remark ?? '',
  };
  formErr.value = '';
  modalOpen.value = true;
}

function closeModal() {
  modalOpen.value = false;
}

function triggerLogoPick() {
  logoInputRef.value?.click();
}

async function onLogoFileChange(e) {
  const input = e.target;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  formErr.value = '';
  logoUploading.value = true;
  try {
    const res = await adminUploadBrandLogo(file);
    form.value.logoUrl = res.imageUrl || '';
  } catch (uploadErr) {
    formErr.value = uploadErr.message || 'Logo 上传失败';
  } finally {
    logoUploading.value = false;
  }
}

async function handleBrandDocUpload(e, apiFn, field, uploading) {
  const input = e.target;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  formErr.value = '';
  uploading.value = true;
  try {
    const res = await apiFn(file);
    form.value[field] = res.imageUrl || '';
  } catch (uploadErr) {
    formErr.value = uploadErr.message || '上传失败';
  } finally {
    uploading.value = false;
  }
}

function triggerBizLicensePick() {
  bizLicenseInputRef.value?.click();
}
function onBizLicenseFileChange(e) {
  return handleBrandDocUpload(e, adminUploadBrandBusinessLicense, 'businessLicenseUrl', bizLicenseUploading);
}
function triggerLegalIdFrontPick() {
  legalIdFrontInputRef.value?.click();
}
function onLegalIdFrontFileChange(e) {
  return handleBrandDocUpload(e, adminUploadBrandLegalIdFront, 'legalIdFrontUrl', legalIdFrontUploading);
}
function triggerLegalIdBackPick() {
  legalIdBackInputRef.value?.click();
}
function onLegalIdBackFileChange(e) {
  return handleBrandDocUpload(e, adminUploadBrandLegalIdBack, 'legalIdBackUrl', legalIdBackUploading);
}

function numOrNull(s) {
  if (s === '' || s == null) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function toPayload() {
  const f = form.value;
  return {
    code: f.code.trim(),
    brandName: f.brandName.trim(),
    brandNameEn: f.brandNameEn.trim() || undefined,
    companyName: f.companyName.trim() || undefined,
    companyNameEn: f.companyNameEn.trim() || undefined,
    logoUrl: f.logoUrl.trim() || undefined,
    unifiedSocialCreditCode: f.unifiedSocialCreditCode.trim() || undefined,
    businessLicenseUrl: f.businessLicenseUrl.trim() || undefined,
    legalName: f.legalName.trim() || undefined,
    legalIdFrontUrl: f.legalIdFrontUrl.trim() || undefined,
    legalIdBackUrl: f.legalIdBackUrl.trim() || undefined,
    legalIdNoMasked: f.legalIdNoMasked.trim() || undefined,
    legalMobile: f.legalMobile.trim() || undefined,
    contactEmail: f.contactEmail.trim() || undefined,
    companyAddress: f.companyAddress.trim() || undefined,
    longitude: numOrNull(f.longitude),
    latitude: numOrNull(f.latitude),
    locationPrecision: f.locationPrecision.trim() || undefined,
    auditStatus: parseInt(f.auditStatus, 10),
    status: parseInt(f.status, 10),
    remark: f.remark.trim() || undefined,
  };
}

async function save() {
  formErr.value = '';
  if (!form.value.code?.trim() || !form.value.brandName?.trim()) {
    formErr.value = '请填写品牌编码与品牌名称';
    return;
  }
  saving.value = true;
  try {
    const body = toPayload();
    if (editingId.value == null) {
      await adminCreateBrand(body);
    } else {
      await adminUpdateBrand(editingId.value, body);
    }
    closeModal();
    await load();
  } catch (e) {
    formErr.value = e.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  if (!confirm(`删除品牌「${row.brandName}」及下属账号、服装数据？`)) return;
  try {
    await adminDeleteBrand(row.id);
    await load();
  } catch (e) {
    err.value = e.message || '删除失败';
  }
}

function openAudit(row) {
  auditTarget.value = row;
  auditForm.value = { auditStatus: 2, remark: '' };
  auditErr.value = '';
  auditModal.value = true;
}

function closeAudit() {
  auditModal.value = false;
  auditTarget.value = null;
}

function closeQrModal() {
  qrModal.value = false;
  qrData.value = null;
  qrErr.value = '';
}

async function openQrEntry(row) {
  qrErr.value = '';
  qrData.value = null;
  qrModal.value = true;
  qrLoading.value = true;
  try {
    qrData.value = await adminGetBrandEntry(row.id);
  } catch (e) {
    qrErr.value = e.message || '加载失败';
  } finally {
    qrLoading.value = false;
  }
}

function qrDataUrl(b64) {
  if (!b64) return '';
  return `data:image/png;base64,${b64}`;
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    } catch {
      /* ignore */
    }
  }
}

function downloadQrPng(base64, filename) {
  if (!base64) return;
  const bin = atob(base64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  const blob = new Blob([bytes], { type: 'image/png' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function submitAudit() {
  auditErr.value = '';
  auditing.value = true;
  try {
    await adminAuditBrand(auditTarget.value.id, {
      auditStatus: auditForm.value.auditStatus,
      remark: auditForm.value.remark.trim() || undefined,
    });
    closeAudit();
    await load();
  } catch (e) {
    auditErr.value = e.message || '操作失败';
  } finally {
    auditing.value = false;
  }
}

async function load() {
  err.value = '';
  try {
    data.value = await adminPageBrands(
      page.value,
      pageSize.value,
      keyword.value.trim() || undefined,
      auditFilter.value === '' ? undefined : parseInt(auditFilter.value, 10),
      statusFilter.value === '' ? undefined : parseInt(statusFilter.value, 10),
    );
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize], load, { immediate: true });

function search() {
  page.value = 1;
  load();
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <h2 class="admin-page-title">品牌方</h2>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">新建品牌方</button>
    </div>
    <div class="admin-toolbar">
      <select v-model="auditFilter" class="admin-input admin-input-inline" @change="search">
        <option value="">全部审核状态</option>
        <option value="0">待完善</option>
        <option value="1">待审核</option>
        <option value="2">已通过</option>
        <option value="3">已驳回</option>
      </select>
      <select v-model="statusFilter" class="admin-input admin-input-inline" @change="search">
        <option value="">全部启用状态</option>
        <option value="1">启用</option>
        <option value="0">停用</option>
      </select>
      <input v-model="keyword" class="admin-input admin-input-inline" placeholder="编码 / 品牌名 / 公司名 / 手机" @keyup.enter="search" />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th class="brand-col-id">品牌 ID</th>
            <th>编码</th>
            <th>品牌名称</th>
            <th>公司名称</th>
            <th>审核</th>
            <th>启用</th>
            <th>更新</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td class="mono brand-id-cell" :title="r.id">{{ r.id }}</td>
            <td class="mono">{{ r.code }}</td>
            <td>{{ r.brandName }}</td>
            <td>{{ r.companyName || '—' }}</td>
            <td>{{ brandAuditLabel(r.auditStatus) }}</td>
            <td>{{ r.status === 1 ? '是' : '否' }}</td>
            <td>{{ fmtDt(r.updatedAt) }}</td>
            <td class="admin-actions admin-actions-wrap">
              <button type="button" class="admin-link-btn" @click="openEdit(r)">编辑</button>
              <button type="button" class="admin-link-btn" @click="openQrEntry(r)">进店码</button>
              <button type="button" class="admin-link-btn" @click="openAudit(r)">审核</button>
              <RouterLink class="admin-link-btn" :to="`/brands/${r.id}/garments`">服装库</RouterLink>
              <RouterLink class="admin-link-btn" :to="`/brands/${r.id}/users`">账号</RouterLink>
              <button type="button" class="admin-link-btn danger" @click="remove(r)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />

    <div v-if="modalOpen" class="admin-modal-overlay" @click.self="closeModal">
      <div class="admin-modal admin-modal-wide brand-modal" role="dialog" aria-modal="true" @click.stop>
        <header class="brand-modal-header">
          <div class="brand-modal-header-text">
            <h3 class="admin-modal-title brand-modal-title">
              {{ editingId == null ? '新建品牌方' : `编辑品牌方 · ${form.brandName || '未命名'}` }}
            </h3>
            <p class="brand-modal-sub">
              {{
                editingId == null
                  ? '先填写必填项，图片支持 JPG / PNG / GIF / WebP，单张不超过 5MB'
                  : '修改后点击保存生效'
              }}
            </p>
          </div>
          <button type="button" class="brand-modal-close" aria-label="关闭" @click="closeModal">×</button>
        </header>

        <div class="brand-modal-body">
          <p v-if="formErr" class="admin-error">{{ formErr }}</p>

          <section class="brand-form-section">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">基础信息</h4>
              <p class="brand-form-section-desc">品牌对外展示名称与主体公司信息</p>
            </div>
            <div class="admin-form-grid brand-form-grid-tight">
              <label>
                <span class="brand-label-line">品牌编码 <abbr class="brand-req" title="必填">*</abbr></span>
                <input v-model="form.code" class="admin-input" placeholder="唯一 slug，如 acme-style" />
              </label>
              <label>
                <span class="brand-label-line">品牌名称 <abbr class="brand-req" title="必填">*</abbr></span>
                <input v-model="form.brandName" class="admin-input" placeholder="中文名称" />
              </label>
              <label>品牌英文名 <input v-model="form.brandNameEn" class="admin-input" placeholder="可选" /></label>
              <label>公司全称 <input v-model="form.companyName" class="admin-input" /></label>
              <label>公司英文名 <input v-model="form.companyNameEn" class="admin-input" placeholder="可选" /></label>
              <label>统一社会信用代码 <input v-model="form.unifiedSocialCreditCode" class="admin-input" placeholder="可选" /></label>
            </div>
          </section>

          <section class="brand-form-section">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">品牌 Logo</h4>
              <p class="brand-form-section-desc">用于列表与对外展示，点击区域上传或替换</p>
            </div>
            <div class="brand-logo-row-ui">
              <input
                ref="logoInputRef"
                type="file"
                class="admin-file-input"
                accept="image/jpeg,image/png,image/gif,image/webp"
                tabindex="-1"
                aria-hidden="true"
                @change="onLogoFileChange"
              />
              <button
                type="button"
                class="brand-upload-tile brand-upload-tile--logo"
                :disabled="logoUploading"
                @click="triggerLogoPick"
              >
                <template v-if="form.logoUrl">
                  <img :src="form.logoUrl" alt="Logo" class="brand-upload-tile-img brand-upload-tile-img--logo" />
                </template>
                <span v-else class="brand-upload-tile-placeholder">
                  <span class="brand-upload-tile-plus" aria-hidden="true">+</span>
                  <span>点击上传 Logo</span>
                </span>
                <span v-if="logoUploading" class="brand-upload-tile-mask">上传中…</span>
              </button>
              <div class="brand-upload-side">
                <div class="brand-upload-actions">
                  <button
                    v-if="form.logoUrl"
                    type="button"
                    class="brand-text-btn"
                    :disabled="logoUploading"
                    @click="triggerLogoPick"
                  >
                    更换
                  </button>
                  <button
                    v-if="form.logoUrl"
                    type="button"
                    class="brand-text-btn brand-text-btn--danger"
                    @click="form.logoUrl = ''"
                  >
                    清除
                  </button>
                </div>
                <p class="brand-upload-tip">建议透明底 PNG 或白底，显示更清晰</p>
              </div>
            </div>
          </section>

          <section class="brand-form-section">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">资质证件</h4>
              <p class="brand-form-section-desc">营业执照与身份证正反面需信息清晰、四角完整</p>
            </div>
            <div class="admin-form-grid brand-form-grid-tight">
              <label class="full">法人姓名 <input v-model="form.legalName" class="admin-input" /></label>
            </div>
            <div class="brand-doc-grid">
              <div class="brand-doc-cell">
                <span class="brand-doc-label">营业执照</span>
                <input
                  ref="bizLicenseInputRef"
                  type="file"
                  class="admin-file-input"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  tabindex="-1"
                  aria-hidden="true"
                  @change="onBizLicenseFileChange"
                />
                <button
                  type="button"
                  class="brand-upload-tile brand-upload-tile--doc"
                  :disabled="bizLicenseUploading"
                  @click="triggerBizLicensePick"
                >
                  <template v-if="form.businessLicenseUrl">
                    <img :src="form.businessLicenseUrl" alt="" class="brand-upload-tile-img" />
                  </template>
                  <span v-else class="brand-upload-tile-placeholder">
                    <span class="brand-upload-tile-plus" aria-hidden="true">+</span>
                    <span>点击上传</span>
                  </span>
                  <span v-if="bizLicenseUploading" class="brand-upload-tile-mask">上传中…</span>
                </button>
                <div v-if="form.businessLicenseUrl" class="brand-doc-actions">
                  <button type="button" class="brand-text-btn" @click="triggerBizLicensePick">更换</button>
                  <button type="button" class="brand-text-btn brand-text-btn--danger" @click="form.businessLicenseUrl = ''">
                    清除
                  </button>
                </div>
              </div>
              <div class="brand-doc-cell">
                <span class="brand-doc-label">身份证人像面</span>
                <input
                  ref="legalIdFrontInputRef"
                  type="file"
                  class="admin-file-input"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  tabindex="-1"
                  aria-hidden="true"
                  @change="onLegalIdFrontFileChange"
                />
                <button
                  type="button"
                  class="brand-upload-tile brand-upload-tile--doc"
                  :disabled="legalIdFrontUploading"
                  @click="triggerLegalIdFrontPick"
                >
                  <template v-if="form.legalIdFrontUrl">
                    <img :src="form.legalIdFrontUrl" alt="" class="brand-upload-tile-img" />
                  </template>
                  <span v-else class="brand-upload-tile-placeholder">
                    <span class="brand-upload-tile-plus" aria-hidden="true">+</span>
                    <span>点击上传</span>
                  </span>
                  <span v-if="legalIdFrontUploading" class="brand-upload-tile-mask">上传中…</span>
                </button>
                <div v-if="form.legalIdFrontUrl" class="brand-doc-actions">
                  <button type="button" class="brand-text-btn" @click="triggerLegalIdFrontPick">更换</button>
                  <button type="button" class="brand-text-btn brand-text-btn--danger" @click="form.legalIdFrontUrl = ''">
                    清除
                  </button>
                </div>
              </div>
              <div class="brand-doc-cell">
                <span class="brand-doc-label">身份证国徽面</span>
                <input
                  ref="legalIdBackInputRef"
                  type="file"
                  class="admin-file-input"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  tabindex="-1"
                  aria-hidden="true"
                  @change="onLegalIdBackFileChange"
                />
                <button
                  type="button"
                  class="brand-upload-tile brand-upload-tile--doc"
                  :disabled="legalIdBackUploading"
                  @click="triggerLegalIdBackPick"
                >
                  <template v-if="form.legalIdBackUrl">
                    <img :src="form.legalIdBackUrl" alt="" class="brand-upload-tile-img" />
                  </template>
                  <span v-else class="brand-upload-tile-placeholder">
                    <span class="brand-upload-tile-plus" aria-hidden="true">+</span>
                    <span>点击上传</span>
                  </span>
                  <span v-if="legalIdBackUploading" class="brand-upload-tile-mask">上传中…</span>
                </button>
                <div v-if="form.legalIdBackUrl" class="brand-doc-actions">
                  <button type="button" class="brand-text-btn" @click="triggerLegalIdBackPick">更换</button>
                  <button type="button" class="brand-text-btn brand-text-btn--danger" @click="form.legalIdBackUrl = ''">
                    清除
                  </button>
                </div>
              </div>
            </div>
            <div class="admin-form-grid brand-form-grid-tight brand-form-grid-mt">
              <label class="full"
                >证件号（脱敏） <input v-model="form.legalIdNoMasked" class="admin-input" placeholder="可选，如 110***********1234"
              /></label>
            </div>
          </section>

          <section class="brand-form-section">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">联系与地址</h4>
            </div>
            <div class="admin-form-grid brand-form-grid-tight">
              <label>法人手机 <input v-model="form.legalMobile" class="admin-input" type="tel" autocomplete="tel" /></label>
              <label>对接邮箱 <input v-model="form.contactEmail" class="admin-input" type="email" autocomplete="email" /></label>
              <label class="full">公司地址 <input v-model="form.companyAddress" class="admin-input" placeholder="省市区 + 详细地址" /></label>
            </div>
          </section>

          <section class="brand-form-section brand-form-section--muted">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">地图坐标（可选）</h4>
              <p class="brand-form-section-desc">一般由地图选点自动写入，可留空</p>
            </div>
            <div class="admin-form-grid brand-form-grid-tight">
              <label>经度 <input v-model="form.longitude" class="admin-input" placeholder="WGS84" inputmode="decimal" /></label>
              <label>纬度 <input v-model="form.latitude" class="admin-input" inputmode="decimal" /></label>
              <label class="full"
                >坐标说明
                <input v-model="form.locationPrecision" class="admin-input" placeholder="例如 map_pick / manual" />
              </label>
            </div>
          </section>

          <section class="brand-form-section">
            <div class="brand-form-section-head">
              <h4 class="brand-form-section-title">审核与状态</h4>
            </div>
            <div class="admin-form-grid brand-form-grid-tight">
              <label
                >入驻状态
                <select v-model="form.auditStatus" class="admin-input">
                  <option value="0">待完善</option>
                  <option value="1">待审核</option>
                  <option value="2">已通过</option>
                  <option value="3">已驳回</option>
                </select>
              </label>
              <label
                >是否启用
                <select v-model="form.status" class="admin-input">
                  <option value="1">启用</option>
                  <option value="0">停用</option>
                </select>
              </label>
              <label class="full">备注 <input v-model="form.remark" class="admin-input" placeholder="内部备注" /></label>
            </div>
          </section>
        </div>

        <footer class="brand-modal-footer admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeModal">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="qrModal" class="admin-modal-overlay" @click.self="closeQrModal">
      <div class="admin-modal admin-modal-wide brand-qr-modal" role="dialog" aria-modal="true" @click.stop>
        <h3 class="admin-modal-title">品牌进店二维码</h3>
        <p v-if="qrLoading" class="admin-muted">加载中…</p>
        <p v-else-if="qrErr" class="admin-error">{{ qrErr }}</p>
        <template v-else-if="qrData">
          <p class="brand-qr-hint">
            PC / 手机浏览器扫码共用一个链接（由 <code class="brand-qr-code">app.brand-entry.landing-url-template</code>
            生成），<code class="brand-qr-code">brand</code> 参数即品牌唯一键：
            <strong class="mono">{{ qrData.brandCode }}</strong>
          </p>
          <div class="brand-qr-single">
            <img
              v-if="qrData.qrPngBase64"
              class="brand-qr-img brand-qr-img--solo"
              :src="qrDataUrl(qrData.qrPngBase64)"
              alt="品牌进店码"
            />
            <label class="brand-qr-url-label">进店链接</label>
            <input class="admin-input brand-qr-url" readonly :value="qrData.landingUrl" />
            <div class="brand-qr-actions">
              <button type="button" class="admin-btn" @click="copyText(qrData.landingUrl)">复制链接</button>
              <button
                type="button"
                class="admin-btn"
                @click="downloadQrPng(qrData.qrPngBase64, `brand-${qrData.brandCode}-entry.png`)"
              >
                下载二维码
              </button>
            </div>
            <div v-if="qrData.miniProgramLandingUrl" class="brand-qr-mp">
              <h4 class="brand-qr-mp-title">小程序预留路径</h4>
              <p class="brand-qr-mp-desc">仅文案/复制用，太阳码由小程序端生成。</p>
              <input class="admin-input brand-qr-url" readonly :value="qrData.miniProgramLandingUrl" />
              <div class="brand-qr-actions">
                <button type="button" class="admin-btn" @click="copyText(qrData.miniProgramLandingUrl)">
                  复制路径
                </button>
              </div>
            </div>
          </div>
        </template>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeQrModal">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="auditModal" class="admin-modal-overlay">
      <div class="admin-modal">
        <h3 class="admin-modal-title">入驻审核 — {{ auditTarget?.brandName }}</h3>
        <p v-if="auditErr" class="admin-error">{{ auditErr }}</p>
        <div class="admin-form-grid">
          <label>结果
            <select v-model.number="auditForm.auditStatus" class="admin-input">
              <option :value="2">通过</option>
              <option :value="3">驳回</option>
            </select>
          </label>
          <label class="full">备注 / 驳回原因 <input v-model="auditForm.remark" class="admin-input" /></label>
        </div>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeAudit">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="auditing" @click="submitAudit">提交</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-modal-wide {
  max-width: 960px;
  width: min(96vw, 960px);
}
.admin-actions-wrap {
  white-space: normal;
  max-width: 280px;
}
.admin-actions-wrap .admin-link-btn {
  display: inline-block;
  margin-right: 0.35rem;
  margin-bottom: 0.2rem;
}

/* —— 品牌弹窗：分区 + 固定头尾 —— */
.brand-modal {
  display: flex;
  flex-direction: column;
  max-height: min(92vh, 880px);
  padding: 0;
  overflow: hidden;
}
.brand-modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.15rem 1.35rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}
.brand-modal-header-text {
  min-width: 0;
}
.brand-modal-title {
  margin: 0 0 0.35rem;
  font-size: 1.12rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.brand-modal-sub {
  margin: 0;
  font-size: 0.8rem;
  color: #8b949e;
  line-height: 1.45;
  max-width: 42rem;
}
.brand-modal-close {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  margin: -0.25rem -0.35rem 0 0;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: #8b949e;
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.brand-modal-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #e6edf3;
}
.brand-modal-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0.65rem 1.35rem 1rem;
  -webkit-overflow-scrolling: touch;
}
.brand-modal-body .admin-error {
  margin: 0 0 0.75rem;
}
.brand-modal-footer {
  margin-top: 0 !important;
  padding: 1rem 1.35rem 1.15rem !important;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 0 0 12px 12px;
  background: rgba(10, 14, 20, 0.55);
  flex-shrink: 0;
}

.brand-form-section {
  margin-bottom: 1.35rem;
  padding: 1rem 1rem 1.05rem;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  background: rgba(0, 0, 0, 0.18);
}
.brand-form-section:last-child {
  margin-bottom: 0;
}
.brand-form-section--muted {
  background: rgba(0, 0, 0, 0.12);
  border-style: dashed;
}
.brand-form-section-head {
  margin-bottom: 0.85rem;
}
.brand-form-section-title {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
  color: #e6edf3;
}
.brand-form-section-desc {
  margin: 0.35rem 0 0;
  font-size: 0.75rem;
  color: #6e7681;
  line-height: 1.45;
}
.brand-form-grid-tight {
  gap: 0.65rem 1rem;
}
.brand-form-grid-mt {
  margin-top: 0.75rem;
}
.brand-label-line {
  font-size: 0.8rem;
  color: #8b949e;
}
.brand-req {
  color: #f85149;
  text-decoration: none;
  font-weight: 700;
  cursor: help;
}

.admin-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* Logo 行 */
.brand-logo-row-ui {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 1rem;
}
.brand-upload-side {
  flex: 1;
  min-width: 180px;
  padding-top: 0.15rem;
}
.brand-upload-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.35rem;
}
.brand-upload-tip {
  margin: 0;
  font-size: 0.75rem;
  color: #6e7681;
  line-height: 1.45;
}

/* 上传磁贴 */
.brand-upload-tile {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 10px;
  border: 1px dashed rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.28);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.15s, background 0.15s;
}
.brand-upload-tile:hover:not(:disabled) {
  border-color: rgba(63, 184, 212, 0.55);
  background: rgba(63, 184, 212, 0.06);
}
.brand-upload-tile:disabled {
  opacity: 0.65;
  cursor: wait;
}
.brand-upload-tile--logo {
  width: 168px;
  height: 96px;
  flex-shrink: 0;
}
.brand-upload-tile--doc {
  width: 100%;
  aspect-ratio: 4 / 3;
  min-height: 120px;
  max-height: 200px;
}
.brand-upload-tile-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 0.75rem;
  font-size: 0.8rem;
  color: #8b949e;
  text-align: center;
}
.brand-upload-tile-plus {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: #3fb8d4;
  font-size: 1.35rem;
  font-weight: 300;
  line-height: 1;
}
.brand-upload-tile-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 0.35rem;
}
.brand-upload-tile-img--logo {
  max-height: 100%;
  padding: 0.5rem;
}
.brand-upload-tile-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: #e6edf3;
  font-size: 0.85rem;
  pointer-events: none;
}
.brand-text-btn {
  padding: 0.35rem 0.65rem;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.08);
  color: #58a6ff;
  font-size: 0.8rem;
  cursor: pointer;
}
.brand-text-btn:hover {
  background: rgba(255, 255, 255, 0.12);
}
.brand-text-btn--danger {
  color: #f85149;
}

/* 证件三列 */
.brand-doc-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.85rem;
  margin-top: 0.25rem;
}
@media (max-width: 720px) {
  .brand-doc-grid {
    grid-template-columns: 1fr;
  }
}
.brand-doc-cell {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}
.brand-doc-label {
  font-size: 0.78rem;
  font-weight: 500;
  color: #c9d1d9;
}
.brand-doc-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.brand-id-cell {
  max-width: 8.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.brand-qr-modal .admin-modal-title {
  margin-bottom: 0.5rem;
}
.brand-qr-hint {
  margin: 0 0 1rem;
  font-size: 0.85rem;
  color: #8b949e;
  line-height: 1.5;
}
.brand-qr-code {
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  font-size: 0.8rem;
}
.brand-qr-single {
  max-width: 420px;
}
.brand-qr-img {
  display: block;
  width: min(100%, 220px);
  height: auto;
  margin: 0 auto 0.75rem;
  border-radius: 8px;
  background: #fff;
  padding: 0.35rem;
}
.brand-qr-img--solo {
  width: min(100%, 260px);
}
.brand-qr-mp {
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.brand-qr-mp-title {
  margin: 0 0 0.35rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: #e6edf3;
}
.brand-qr-mp-desc {
  margin: 0 0 0.65rem;
  font-size: 0.75rem;
  color: #6e7681;
  line-height: 1.45;
}
.brand-qr-url-label {
  display: block;
  margin: 0.35rem 0 0.25rem;
  font-size: 0.75rem;
  color: #8b949e;
}
.brand-qr-url {
  font-size: 0.78rem;
  width: 100%;
  box-sizing: border-box;
}
.brand-qr-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.65rem;
}
.admin-muted {
  color: #8b949e;
  font-size: 0.9rem;
}
</style>
