export const TOKEN_KEY = 'adminAccessToken';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}
