import { DRIVE_SCOPE, DriveReadError, stopped, type DriveConfig, type DriveDialogs, type Grant } from './PersonalDrive';

type TokenReply = {access_token?: string; scope?: string; expires_in?: number|string; error?: string};
interface TokenClient { requestAccessToken(options: {prompt: string}): void }
interface Picker { setVisible(visible: boolean): void }
interface Builder {
  setAppId(value: string): Builder; setDeveloperKey(value: string): Builder; setOAuthToken(value: string): Builder;
  setOrigin(value: string): Builder; addView(view: DocsView): Builder;
  setCallback(callback: (data: {action?: string; docs?: {id?: string}[]}) => void): Builder; build(): Picker;
}
interface DocsView { setMimeTypes(value: string): DocsView; setIncludeFolders(value: boolean): DocsView; setSelectFolderEnabled(value: boolean): DocsView }
interface GoogleSdk {
  accounts: {oauth2: {initTokenClient(options: {client_id: string; scope: string; include_granted_scopes: boolean; login_hint: string;
    callback: (reply: TokenReply) => void; error_callback: () => void}): TokenClient}};
  picker: {PickerBuilder: new() => Builder; DocsView: new() => DocsView; Action: {PICKED: string; CANCEL: string}};
}
interface Gapi { load(name: string, options: {callback: () => void; onerror: () => void; timeout: number; ontimeout: () => void}): void }
const globals = () => window as unknown as {google?: GoogleSdk; gapi?: Gapi};
let loading: Promise<void> | undefined;
function script(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const element = document.createElement('script');
    const timer = setTimeout(() => finish(false), 15000);
    function finish(ok: boolean) { clearTimeout(timer); element.onload = null; element.onerror = null;
      if (ok) resolve(); else { element.remove(); reject(new DriveReadError('Google’s connection library could not load. Try again.')); } }
    element.src = src; element.async = true; element.referrerPolicy = 'strict-origin-when-cross-origin';
    element.onload = () => finish(true); element.onerror = () => finish(false); document.head.append(element);
  });
}
export async function loadDriveDialogs(signal: AbortSignal): Promise<DriveDialogs> {
  if (!loading) loading = (async () => {
    if (!globals().google?.accounts?.oauth2) await script('https://accounts.google.com/gsi/client');
    if (!globals().gapi) await script('https://apis.google.com/js/api.js');
    if (!globals().google?.picker) await new Promise<void>((resolve, reject) => {
      globals().gapi!.load('picker', {callback:resolve, onerror:() => reject(new DriveReadError('Google’s file chooser could not load.')),
        timeout:15000, ontimeout:() => reject(new DriveReadError('Google’s file chooser timed out.'))});
    });
    if (!globals().google?.accounts?.oauth2 || !globals().google?.picker) throw new DriveReadError('Google’s file chooser is unavailable.');
  })().catch(error => { loading = undefined; throw error; });
  await loading;
  if (signal.aborted) throw stopped();
  const sdk = globals().google!;
  return {
    authorize(config: DriveConfig, abort: AbortSignal): Promise<Grant> {
      return new Promise((resolve, reject) => {
        if (abort.aborted) { reject(stopped()); return; }
        let settled = false;
        const cancelled = () => { if (!settled) { settled = true; reject(stopped()); } };
        abort.addEventListener('abort', cancelled, {once:true});
        const finish = (grant?: Grant) => {
          abort.removeEventListener('abort', cancelled);
          if (settled) return; settled = true;
          if (abort.aborted || !grant) reject(new DriveReadError('Google authorization did not complete.')); else resolve(grant);
        };
        try {
          const client = sdk.accounts.oauth2.initTokenClient({client_id:config.clientId, scope:DRIVE_SCOPE, include_granted_scopes:false,
            login_hint:config.email, callback:reply => {
              const expires = Number(reply.expires_in);
              if (reply.error || !reply.access_token || !reply.scope || !Number.isFinite(expires) || expires <= 0 || expires > 86400) finish();
              else finish({token:reply.access_token, scope:reply.scope, expiresAt:Date.now() + expires * 1000});
            }, error_callback:() => finish()});
          client.requestAccessToken({prompt:''});
        } catch { finish(); }
      });
    },
    choose(config: DriveConfig, token: string, abort: AbortSignal): Promise<string> {
      return new Promise((resolve, reject) => {
        if (abort.aborted) { reject(stopped()); return; }
        let picker: Picker | undefined; let settled = false;
        const cancel = () => finish();
        function finish(id?: string) {
          if (settled) return; settled = true; abort.removeEventListener('abort', cancel);
          picker?.setVisible(false); picker = undefined;
          if (abort.aborted || !id) reject(new DriveReadError('File selection cancelled.')); else resolve(id);
        }
        abort.addEventListener('abort', cancel, {once:true});
        try {
          const view = new sdk.picker.DocsView().setMimeTypes('application/json').setIncludeFolders(false).setSelectFolderEnabled(false);
          picker = new sdk.picker.PickerBuilder().setAppId(config.appId).setDeveloperKey(config.apiKey).setOAuthToken(token)
            .setOrigin(window.location.origin).addView(view).setCallback(data => {
              if (data.action === sdk.picker.Action.CANCEL) finish();
              else if (data.action === sdk.picker.Action.PICKED) finish(data.docs?.length === 1 ? data.docs[0].id : undefined);
            }).build();
          picker.setVisible(true);
        } catch { finish(); }
      });
    },
  };
}
