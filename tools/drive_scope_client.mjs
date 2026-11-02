// Experiment-only transport. Tokens stay in memory; no server-side client secret.
export const SCOPE = 'https://www.googleapis.com/auth/drive.file';
export class DriveScopeClient {
  constructor(token, budget, fetcher = fetch) {
    this.token = token;
    this.budget = budget;
    this.fetcher = fetcher;
  }
  async request(path, init = {}) {
    if (!/^\/(?:upload\/)?drive\/v3\/files(?:\/[-\w]+)?(?:\?|$)/.test(path) || path.includes('..')) {
      throw new Error('Endpoint outside the synthetic Drive experiment');
    }
    const used = Number(this.budget.getItem('gym-m1-drive-requests') ?? '0');
    if (!Number.isSafeInteger(used) || used < 0 || used >= 500) throw new Error('Request budget exhausted or invalid');
    // Persist before transmission. A failed write must prevent the API request.
    this.budget.setItem('gym-m1-drive-requests', String(used + 1));
    return this.fetcher('https://www.googleapis.com' + path, {
      ...init, redirect: 'error', credentials: 'omit', referrerPolicy: 'no-referrer',
      headers: {...init.headers, Authorization: 'Bearer ' + this.token},
    });
  }
}
