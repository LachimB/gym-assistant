import {describe,it,expect,vi} from 'vitest';
import {readFileSync} from 'node:fs';
import {PersonalDriveReader,DRIVE_SCOPE,PERSONAL_FOLDER,checkConfig,type DriveConfig,type BudgetKind,type Grant} from './PersonalDrive';
const config: DriveConfig = {clientId:'900-'+ 'a'.repeat(25)+'.apps.googleusercontent.com',apiKey:'AIza'+'x'.repeat(35),appId:'900',
  email:'owner@example.test',permissionId:'synthetic-account',folderId:'synthetic-folder',marker:'personal-m1-synthetic',allocationId:'synthetic-allocation'};
const raw=readFileSync(new URL('../../../examples/sessions/synthetic-runtime-v2-session.json',import.meta.url),'utf8');
const session=JSON.parse(raw);
function harness(options: {status?:number; edit?: (meta: Record<string,unknown>,kind:string) => void; media?: string; grant?: Grant; choose?: () => Promise<string>; consume?: (kind: BudgetKind) => void} = {}) {
  const calls: {url:string;init?:RequestInit}[]=[]; const budgets:BudgetKind[]=[];
  const dialogs={authorize:vi.fn(async()=>options.grant??{token:'synthetic-access',expiresAt:2000,scope:DRIVE_SCOPE}),
    choose:vi.fn(options.choose??(async()=> 'synthetic-file'))};
  const fetcher=vi.fn(async(input: string|URL|Request,init?:RequestInit)=>{
    const url=String(input); calls.push({url,init});
    if (options.status) return new Response('private provider detail',{status:options.status});
    let kind: string;let meta: Record<string,unknown>;
    if(url.includes('/about?')) {kind='about';meta={user:{permissionId:config.permissionId,emailAddress:config.email}};}
    else if(url.includes('alt=media')) return new Response(options.media??raw);
    else if(url.includes('/'+config.folderId+'?')) {kind='folder';meta={id:config.folderId,name:PERSONAL_FOLDER,mimeType:'application/vnd.google-apps.folder',trashed:false,appProperties:{gymPersonalM1:config.marker}};}
    else {kind='file';meta={id:'synthetic-file',name:`PERSONAL-session-${session.sessionId}.json`,mimeType:'application/json',parents:[config.folderId],trashed:false,appProperties:{gymPersonalM1:config.marker}};}
    options.edit?.(meta,kind); return new Response(JSON.stringify(meta));
  }) as typeof fetch;
  const reader=new PersonalDriveReader(checkConfig(config),async(kind,signal)=>{if(signal.aborted)throw new Error();budgets.push(kind);options.consume?.(kind);},dialogs,fetcher,()=>1000);
  return {reader,calls,budgets,dialogs,fetcher};
}
describe('selected personal Drive reader',()=>{
  it('verifies the selected namespace and canonical recording with only bounded fixed-origin GETs',async()=>{
    const h=harness(); const result=await h.reader.open(new AbortController().signal);
    expect(result.record.sessionId).toBe(session.sessionId);expect(h.calls).toHaveLength(4);
    expect(h.budgets).toEqual(['authorization','request','picker','request','request','request']);
    for(const {url,init} of h.calls){expect(url.startsWith('https://www.googleapis.com/drive/v3/')).toBe(true);expect(init).toMatchObject({method:'GET',credentials:'omit',redirect:'error',cache:'no-store',referrerPolicy:'no-referrer'});expect(init?.body).toBeUndefined();}
  });
  it('rejects malformed private configuration and never substitutes defaults',()=>{
    for(const patch of [{clientId:'bad'},{apiKey:''},{email:'bad'},{folderId:'../other'},{allocationId:''},{marker:'other'}]) expect(()=>checkConfig({...config,...patch})).toThrow();
  });
  it('rejects another account before Picker or file calls',async()=>{
    const h=harness({edit:(m,k)=>{if(k==='about')m.user={permissionId:'other',emailAddress:config.email};}});
    await expect(h.reader.open(new AbortController().signal)).rejects.toThrow('account');expect(h.calls).toHaveLength(1);expect(h.dialogs.choose).not.toHaveBeenCalled();
  });
  it('requires exactly the granted scope and a fresh token',async()=>{
    for(const grant of [{token:'synthetic',expiresAt:2000,scope:DRIVE_SCOPE+' another-scope'},{token:'synthetic',expiresAt:999,scope:DRIVE_SCOPE}]){
      const h=harness({grant});await expect(h.reader.open(new AbortController().signal)).rejects.toThrow();expect(h.calls).toHaveLength(0);
    }
  });
  it('rejects missing/changed folder and unrelated, trashed, shortcut, misnamed or oversized files',async()=>{
    const changes: [string,Record<string,unknown>][]=[['folder',{name:'Other'}],['folder',{appProperties:{}}],['file',{parents:['other']}],['file',{trashed:true}],
      ['file',{mimeType:'application/vnd.google-apps.shortcut'}],['file',{id:'other'}],['file',{appProperties:{}}],['file',{name:'PERSONAL-session-other.json'}],['file',{size:'8388609'}]];
    for(const [kind,patch] of changes){const h=harness({edit:(m,k)=>{if(k===kind)Object.assign(m,patch);}});await expect(h.reader.open(new AbortController().signal)).rejects.toThrow();}
  });
  it('rejects Picker URLs, folders and cancelled selection before reading content',async()=>{
    for(const id of ['https://outside.invalid/file','../file',config.folderId,'']){
      const h=harness({choose:async()=>id});await expect(h.reader.open(new AbortController().signal)).rejects.toThrow();expect(h.calls).toHaveLength(1);
    }
  });
  it('redacts provider bodies for authorization, permission, missing, quota and service failures',async()=>{
    for(const status of [401,403,404,429,500]){const h=harness({status});await expect(h.reader.open(new AbortController().signal)).rejects.not.toThrow('private provider detail');expect(h.calls).toHaveLength(1);}
  });
  it('cannot make a request if the persisted reservation fails',async()=>{
    const h=harness({consume:kind=>{if(kind==='request')throw new Error('private budget detail');}});
    await expect(h.reader.open(new AbortController().signal)).rejects.not.toThrow('private budget detail');expect(h.calls).toHaveLength(0);
  });
  it('invalidates an in-flight selection before any file fetch',async()=>{
    const controller=new AbortController();let choose!: (id:string)=>void;
    const h=harness({choose:()=>new Promise(resolve=>{choose=resolve;})});const pending=h.reader.open(controller.signal);
    await vi.waitFor(()=>expect(h.dialogs.choose).toHaveBeenCalled());controller.abort();choose('synthetic-file');
    await expect(pending).rejects.toThrow('cancelled');expect(h.calls).toHaveLength(1);
  });
  it('rejects malformed, oversized and altered-plan data without echoing document contents',async()=>{
    const altered=JSON.parse(raw);altered.basePlanSha256='f'.repeat(64);
    for(const media of ['{"private_source":"hidden"}', ' '.repeat(8388609),JSON.stringify(altered)]) {
      const h=harness({media});await expect(h.reader.open(new AbortController().signal)).rejects.not.toThrow('hidden');
    }
  });
  it('never reuses an authorization grant across opens',async()=>{
    const h=harness();await h.reader.open(new AbortController().signal);await h.reader.open(new AbortController().signal);
    expect(h.dialogs.authorize).toHaveBeenCalledTimes(2);
  });
});
