const PENDING_KEY = 'fashtry_onboarding_tryon_pending';
const DONE_KEY = 'fashtry_onboarding_tryon_done';
const RETURN_KEY = 'fashtry_onboarding_return';

/** 新用户引导落点：真实虚拟试穿页 */
export const ONBOARDING_TRYON_PATH = '/tryon';

export const ONBOARDING_ASSETS = {
  model: '/static/onboarding/model.png',
  garment: '/static/onboarding/garment.png',
  result: '/static/onboarding/result.jpg',
};

export function markOnboardingTryonPendingAfterRegister(returnTo) {
  localStorage.setItem(PENDING_KEY, '1');
  localStorage.removeItem(DONE_KEY);
  if (
    typeof returnTo === 'string' &&
    returnTo.startsWith('/') &&
    !returnTo.startsWith('//') &&
    returnTo !== ONBOARDING_TRYON_PATH
  ) {
    sessionStorage.setItem(RETURN_KEY, returnTo);
  } else {
    sessionStorage.removeItem(RETURN_KEY);
  }
}

export function shouldShowOnboardingTryon() {
  return localStorage.getItem(PENDING_KEY) === '1';
}

export function completeOnboardingTryon() {
  localStorage.removeItem(PENDING_KEY);
  localStorage.setItem(DONE_KEY, '1');
}

export function consumeOnboardingReturnPath() {
  const path = sessionStorage.getItem(RETURN_KEY);
  sessionStorage.removeItem(RETURN_KEY);
  if (typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')) return path;
  return '/';
}

export function isOnboardingTryonRoute(path) {
  return path === ONBOARDING_TRYON_PATH || path.startsWith(`${ONBOARDING_TRYON_PATH}?`);
}
