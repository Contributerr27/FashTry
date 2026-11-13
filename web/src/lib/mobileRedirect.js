const STAY_DESKTOP_KEY = 'fashtry_stay_desktop';

export function shouldRedirectToMobile() {
  if (typeof window === 'undefined') return false;
  if (localStorage.getItem(STAY_DESKTOP_KEY) === '1') return false;
  const ua = navigator.userAgent || '';
  return /Android|iPhone|iPad|iPod|Mobile|webOS|BlackBerry|IEMobile|Opera Mini/i.test(ua);
}

export function markStayOnDesktop() {
  localStorage.setItem(STAY_DESKTOP_KEY, '1');
}
