const DOMAINS = [
  'gmail.com',
  'outlook.com',
  'hotmail.com',
  'yahoo.com',
  'icloud.com',
  'qq.com',
  '163.com',
  '126.com',
  'sina.com',
  'foxmail.com',
];

/**
 * @param {string} input
 * @returns {string[]}
 */
export function suggestEmails(input) {
  if (!input || typeof input !== 'string') return [];
  const trimmed = input.trim();
  const at = trimmed.indexOf('@');
  if (at < 0) return [];
  const local = trimmed.slice(0, at);
  if (!local) return [];
  const partial = trimmed.slice(at + 1).toLowerCase();
  const list = partial
    ? DOMAINS.filter((d) => d.startsWith(partial))
    : DOMAINS;
  return list.slice(0, 6).map((d) => `${local}@${d}`);
}
