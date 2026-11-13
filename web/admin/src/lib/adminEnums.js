/** 后台表单下拉选项与展示文案（中文） */

export const PUBLIC_MODEL_GENDER_OPTIONS = [
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
  { value: 'unisex', label: '不限' },
];

export const PUBLIC_MODEL_REGION_OPTIONS = [
  { value: '', label: '（不填）' },
  { value: 'Asian', label: '亚洲' },
  { value: 'Caucasian', label: '欧美' },
  { value: 'African', label: '非洲' },
  { value: 'Latino', label: '拉美' },
];

export const PUBLIC_MODEL_AGE_OPTIONS = [
  { value: '', label: '（不填）' },
  { value: 'Teen', label: '青少年' },
  { value: 'Adult', label: '成年' },
  { value: 'Senior', label: '中老年' },
];

export const PUBLIC_MODEL_STYLE_OPTIONS = [
  { value: '', label: '（不填）' },
  { value: 'Casual', label: '休闲' },
  { value: 'Formal', label: '正装' },
  { value: 'Sport', label: '运动' },
  { value: 'Streetwear', label: '街头' },
];

export function publicModelGenderLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_MODEL_GENDER_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

export function publicModelRegionLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_MODEL_REGION_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

export function publicModelAgeLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_MODEL_AGE_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

export function publicModelStyleLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_MODEL_STYLE_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

/** public_garments.scene_category */
export const PUBLIC_GARMENT_SCENE_OPTIONS = [
  { value: 'wedding', label: '婚礼' },
  { value: 'workplace', label: '职场' },
  { value: 'campus', label: '校园' },
  { value: 'gu_feng', label: '古风' },
  { value: 'trendy', label: '潮流' },
  { value: 'western', label: '欧美' },
];

/** 与 tryon_tasks.category 一致 */
export const PUBLIC_GARMENT_TYPE_OPTIONS = [
  { value: '', label: '（不填）' },
  { value: 'tops', label: '上装' },
  { value: 'bottoms', label: '下装' },
  { value: 'one-pieces', label: '连体/连衣裙' },
];

export function publicGarmentSceneLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_GARMENT_SCENE_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

export function publicGarmentTypeLabel(v) {
  if (v == null || v === '') return '—';
  return PUBLIC_GARMENT_TYPE_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

/** 与库表 recharge_packages.code 常见取值一致 */
export const PACKAGE_CODE_OPTIONS = [
  { value: 'trial', label: '试用' },
  { value: 'standard', label: '标准' },
  { value: 'pro', label: '进阶' },
  { value: 'master', label: '大师' },
  { value: 'enterprise', label: '企业' },
];

export function packageCodeOptionsForForm(currentCode) {
  const c = (currentCode || '').trim();
  const list = [...PACKAGE_CODE_OPTIONS];
  if (c && !list.some((o) => o.value === c)) {
    list.unshift({ value: c, label: `${c}（当前）` });
  }
  return list;
}
