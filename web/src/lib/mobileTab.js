/** @returns {'home' | 'create' | 'works' | 'me' | null} */
export function mobileTabKeyForPath(path) {
  const p = (path || '/').replace(/\/index\.html$/, '/') || '/';
  if (p === '/') return 'home';
  if (p === '/create' || p.startsWith('/create/')) return 'create';
  if (p === '/works') return 'works';
  if (
    p === '/profile' ||
    p === '/wallet' ||
    p === '/recharge' ||
    p === '/invite'
  ) {
    return 'me';
  }
  return null;
}
