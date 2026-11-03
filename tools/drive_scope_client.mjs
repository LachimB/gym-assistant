// Experiment-only transport. Tokens stay in memory; no server-side client secret.
export const SCOPE = 'https://www.googleapis.com/auth/drive.file';
export function reserveBudget(budget, count = 1) {
  const used = Number(budget.getItem('gym-m1-drive-requests') ?? '0');
  if (!Number.isSafeInteger(used) || used < 0 || !Number.isSafeInteger(count) || count < 1 || used + count > 500) {
    throw new Error('Request budget exhausted or invalid');
  }
  budget.setItem('gym-m1-drive-requests', String(used + count));
}
export function isExternalSyntheticFixture(metadata, folder) {
  return metadata.name === 'SYNTHETIC-external-child.json' && metadata.mimeType === 'application/json' &&
    Array.isArray(metadata.parents) && metadata.parents.length === 1 && metadata.parents[0] === folder;
}
export class DriveScopeClient {
  constructor(token, budget, fetcher = (url, init) => globalThis.fetch(url, init)) {
    this.token = token;
    this.budget = budget;
    this.fetcher = fetcher;
  }
  async request(path, init = {}) {
    if ((!/^\/(?:upload\/)?drive\/v[23]\/files(?:\/[-\w]+)?(?:\?|$)/.test(path) &&
         !/^\/drive\/v3\/about\?fields=user%28emailAddress%29$/.test(path)) || path.includes('..')) {
      throw new Error('Endpoint outside the synthetic Drive experiment');
    }
    // Persist before transmission. A failed write must prevent the API request.
    reserveBudget(this.budget);
    return this.fetcher('https://www.googleapis.com' + path, {
      ...init, redirect: 'error', credentials: 'omit', referrerPolicy: 'no-referrer',
      headers: {...init.headers, Authorization: 'Bearer ' + this.token},
    });
  }
}
