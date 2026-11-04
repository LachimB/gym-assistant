import { MAX_HISTORY_BYTES, readHistory, type ReadHistory } from './SessionHistory';

export const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.file';
export const PERSONAL_FOLDER = 'Gym Assistant — PERSONAL M1 DEV';
export type DriveConfig = Readonly<{ clientId: string; apiKey: string; appId: string; email: string;
  permissionId: string; folderId: string; marker: string; allocationId: string }>;
export type BudgetKind = 'request' | 'authorization' | 'picker';
export type Consume = (kind: BudgetKind, signal: AbortSignal) => Promise<void>;
export type Grant = { token: string; expiresAt: number; scope: string };
export interface DriveDialogs {
  authorize(config: DriveConfig, signal: AbortSignal): Promise<Grant>;
  choose(config: DriveConfig, token: string, signal: AbortSignal): Promise<string>;
}
export class DriveReadError extends Error {}
export const stopped = () => new DriveReadError('Operation cancelled.');
export function checkConfig(value: unknown): DriveConfig {
  const c = value as DriveConfig;
  if (!c || typeof c !== 'object' || !/^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$/.test(c.clientId) ||
      !/^AIza[\w-]{25,100}$/.test(c.apiKey) || !/^\d+$/.test(c.appId) || !c.clientId.startsWith(c.appId + '-') || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(c.email) ||
      !/^[\w-]{1,128}$/.test(c.permissionId) || !/^[\w-]{1,128}$/.test(c.folderId) ||
      !/^personal-m1-[\w-]{1,128}$/.test(c.marker) || !/^[\w-]{1,128}$/.test(c.allocationId)) {
    throw new DriveReadError('The approved private Drive connection is unavailable.');
  }
  return Object.freeze({ clientId:c.clientId, apiKey:c.apiKey, appId:c.appId, email:c.email,
    permissionId:c.permissionId, folderId:c.folderId, marker:c.marker, allocationId:c.allocationId });
}
async function boundedText(response: Response, limit: number, signal: AbortSignal): Promise<string> {
  const declared = response.headers.get('content-length');
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > limit)) throw new DriveReadError('The selected file exceeds the reader limit.');
  const reader = response.body?.getReader();
  if (!reader) throw new DriveReadError('The selected file could not be read.');
  const chunks: Uint8Array[] = []; let count = 0;
  try {
    while (true) {
      if (signal.aborted) throw stopped();
      const next = await reader.read(); if (next.done) break;
      count += next.value.byteLength;
      if (count > limit) throw new DriveReadError('The selected file exceeds the reader limit.');
      chunks.push(next.value);
    }
    if (signal.aborted) throw stopped();
    const bytes = new Uint8Array(count); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    try { return new TextDecoder('utf-8', {fatal:true}).decode(bytes); }
    catch { throw new DriveReadError('The selected file is not valid UTF-8.'); }
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}
const messageFor = (status: number) => status === 401 ? 'Authorization expired. Connect again.' :
  status === 403 ? 'Google denied access to this selected file.' : status === 404 ? 'The approved folder or selected file is unavailable.' :
  status === 429 ? 'Google is limiting requests. Try again later.' : status >= 500 ? 'Google Drive is temporarily unavailable.' : 'Drive returned an unexpected response.';

/** This adapter cannot accept a method, URL, request body, or arbitrary endpoint. */
export class PersonalDriveReader {
  private grant: Grant | null = null;
  constructor(private config: DriveConfig, private consume: Consume, private dialogs: DriveDialogs,
    private fetcher: typeof fetch = fetch, private now: () => number = Date.now) {}
  disconnect() { this.grant = null; }
  async open(signal: AbortSignal): Promise<ReadHistory> {
    this.disconnect();
    try {
      await this.consume('authorization', signal);
      if (signal.aborted) throw stopped();
      const grant = await this.dialogs.authorize(this.config, signal);
      if (signal.aborted) throw stopped();
      if (grant.scope.trim() !== DRIVE_SCOPE || !grant.token || !Number.isFinite(grant.expiresAt) || grant.expiresAt <= this.now()) {
        throw new DriveReadError('Google did not grant exactly the approved specific-files permission.');
      }
      this.grant = grant;
      const account = await this.metadata('about', signal);
      const user = account.user as Record<string, unknown> | undefined;
      if (!user || user.permissionId !== this.config.permissionId || user.emailAddress !== this.config.email) {
        throw new DriveReadError('The Google account does not match the approved personal connection.');
      }
      await this.consume('picker', signal);
      const selectedId = await this.dialogs.choose(this.config, grant.token, signal);
      if (signal.aborted) throw stopped();
      if (!/^[\w-]{1,128}$/.test(selectedId) || selectedId === this.config.folderId) throw new DriveReadError('Choose one delivered session JSON file.');
      const folder = await this.metadata('folder', signal);
      const props = folder.appProperties as Record<string, unknown> | undefined;
      if (folder.id !== this.config.folderId || folder.name !== PERSONAL_FOLDER || folder.trashed !== false ||
          folder.mimeType !== 'application/vnd.google-apps.folder' || props?.gymPersonalM1 !== this.config.marker) {
        throw new DriveReadError('The approved personal folder could not be verified.');
      }
      const file = await this.metadata('file', signal, selectedId);
      const fileProps = file.appProperties as Record<string, unknown> | undefined;
      if (file.id !== selectedId || file.trashed !== false || file.mimeType !== 'application/json' ||
          !Array.isArray(file.parents) || file.parents.length !== 1 || file.parents[0] !== this.config.folderId ||
          fileProps?.gymPersonalM1 !== this.config.marker || typeof file.name !== 'string' || !/^PERSONAL-session-[\w-]+\.json$/.test(file.name)) {
        throw new DriveReadError('Choose a delivered session from the approved personal folder.');
      }
      if (file.size !== undefined && (typeof file.size !== 'string' || !/^\d+$/.test(file.size) || Number(file.size) > MAX_HISTORY_BYTES)) throw new DriveReadError('The selected file exceeds the reader limit.');
      const text = await this.request('media', signal, selectedId);
      const history = await readHistory(text);
      if (signal.aborted) throw stopped();
      if (history.record.schemaVersion !== '1.2.0' || file.name !== `PERSONAL-session-${history.record.sessionId}.json` || !['plannedEnd','partial'].includes(history.record.completionKind ?? '')) {
        throw new DriveReadError('Choose a finalized recording delivered by the personal-session sender.');
      }
      return history;
    } catch (error) {
      if (error instanceof DriveReadError) throw error;
      // Never echo provider bodies, token errors, document values, or parser input.
      throw new DriveReadError(signal.aborted ? 'Operation cancelled.' : 'Unable to open and validate the selected Drive recording.');
    } finally { this.disconnect(); }
  }
  private async metadata(kind: 'about'|'folder'|'file', signal: AbortSignal, id?: string): Promise<Record<string, unknown>> {
    try {
      const value: unknown = JSON.parse(await this.request(kind, signal, id));
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
      return value as Record<string, unknown>;
    } catch (error) { if (error instanceof DriveReadError) throw error; throw new DriveReadError('Drive returned invalid metadata.'); }
  }
  private async request(kind: 'about'|'folder'|'file'|'media', signal: AbortSignal, id?: string): Promise<string> {
    if (signal.aborted) throw stopped();
    if (!this.grant || this.grant.expiresAt <= this.now()) { this.disconnect(); throw new DriveReadError('Authorization expired. Connect again.'); }
    const actualId = kind === 'folder' ? this.config.folderId : id;
    if (kind !== 'about' && (!actualId || !/^[\w-]{1,128}$/.test(actualId))) throw stopped();
    const path = kind === 'about' ? 'about?fields=user(permissionId,emailAddress)' :
      `files/${encodeURIComponent(actualId!)}?` + (kind === 'media' ? 'alt=media' : 'fields=id,name,mimeType,parents,appProperties,trashed,size');
    await this.consume('request', signal);
    if (signal.aborted || !this.grant || this.grant.expiresAt <= this.now()) { this.disconnect(); throw stopped(); }
    const requestSignal = AbortSignal.any([signal, AbortSignal.timeout(20000)]);
    const response = await this.fetcher('https://www.googleapis.com/drive/v3/' + path, {
      method:'GET', headers:{Authorization:'Bearer ' + this.grant.token}, credentials:'omit', redirect:'error',
      referrerPolicy:'no-referrer', cache:'no-store', signal:requestSignal,
    });
    if (!response.ok) { if (response.status === 401) this.disconnect(); await response.body?.cancel().catch(() => {}); throw new DriveReadError(messageFor(response.status)); }
    return boundedText(response, kind === 'media' ? MAX_HISTORY_BYTES : 32768, requestSignal);
  }
}

export async function privateConnection(signal: AbortSignal): Promise<{config: DriveConfig; consume: Consume}> {
  try {
    const response = await fetch('/__private/drive-reader/config', {signal, cache:'no-store', credentials:'same-origin', redirect:'error'});
    if (!response.ok) throw new Error();
    const config = checkConfig(JSON.parse(await boundedText(response, 8192, signal)));
    const consume: Consume = async (kind, requestSignal) => {
      const reply = await fetch('/__private/drive-reader/budget', {method:'POST', signal:requestSignal, cache:'no-store', redirect:'error', credentials:'same-origin',
        headers:{'Content-Type':'application/json','X-Gym-Local-Reader':'1'}, body:JSON.stringify({allocationId:config.allocationId, kind})});
      if (!reply.ok) throw new DriveReadError('The approved test allowance is unavailable or exhausted.');
    };
    return {config, consume};
  } catch (error) { if (error instanceof DriveReadError) throw error; throw new DriveReadError('Personal Drive access is disabled in this build. Local session files still work.'); }
}
