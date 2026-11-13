import { shouldShowOnboardingTryon } from './onboardingTryonGate.js';

const PENDING_KEY = 'fashtry_commercial_notice_pending';
const ACK_KEY = 'fashtry_commercial_notice_ack';

export function markCommercialNoticePendingAfterRegister() {
  localStorage.setItem(PENDING_KEY, '1');
}

export function shouldShowCommercialNotice() {
  // 首次试穿引导未完成前不打断
  if (shouldShowOnboardingTryon()) return false;
  return localStorage.getItem(PENDING_KEY) === '1';
}

export function acknowledgeCommercialNotice() {
  localStorage.removeItem(PENDING_KEY);
  localStorage.setItem(ACK_KEY, '1');
}

export function isCommercialNoticeAcknowledged() {
  return localStorage.getItem(ACK_KEY) === '1';
}
