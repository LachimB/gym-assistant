import {describe,it,expect,vi} from 'vitest';
import {readFileSync} from 'node:fs';
import {PersonalDriveReader,DRIVE_SCOPE,PERSONAL_FOLDER,checkConfig,privateConnection,type DriveConfig,type BudgetKind,type Grant} from './PersonalDrive';
import {checkProjectLayout,projectArea,type ProjectDriveLayout} from './ProjectDriveLayout.mjs';
const layout=checkProjectLayout(JSON.parse(readFileSync(new URL('../../../examples/project-layout/synthetic-layout.json',import.meta.url),'utf8')));
const config: DriveConfig = {clientId:'900-'+ 'a'.repeat(25)+'.apps.googleusercontent.com',apiKey:'AIza'+'x'.repeat(35),appId:'900',
  email:'owner@example.test',permissionId:'synthetic-account',folderId:'synthetic-folder',marker:'personal-m1-synthetic',allocationId:'synthetic-allocation'};
const raw=readFileSync(new URL('../../../examples/sessions/synthetic-runtime-v2-session.json',import.meta.url),'utf8');
const session=JSON.parse(raw);
function harness(options: {project?:ProjectDriveLayout;status?:number; failure?:boolean; metadataBody?:string; edit?: (meta: Record<string,unknown>,kind:string) => void; media?: string; grant?: Grant; choose?: () => Promise<string>; consume?: (kind: BudgetKind) => void} = {}) {
  const area=options.project?projectArea(options.project,'personal'):undefined;
  const c=checkConfig({...config,...(area?{folderId:area.id,marker:area.marker,projectLayout:options.project}:{})});
  const calls: {url:string;init?:RequestInit}[]=[]; const budgets:BudgetKind[]=[];
  const dialogs={authorize:vi.fn(async()=>options.grant??{token:'synthetic-access',expiresAt:2000,scope:DRIVE_SCOPE}),
    choose:vi.fn(options.choose??(async()=> 'synthetic-file'))};
  const fetcher=vi.fn(async(input: string|URL|Request,init?:RequestInit)=>{
    const url=String(input); calls.push({url,init});
    if (options.failure) throw new TypeError('private provider failure');
    if (options.metadataBody) return new Response(options.metadataBody);
    if (options.status) return new Response('private provider detail',{status:options.status});
    let kind: string;let meta: Record<string,unknown>;
    if(url.includes('/about?')) {kind='about';meta={user:{permissionId:c.permissionId,emailAddress:c.email}};}
    else if(url.includes('alt=media')) return new Response(options.media??raw);
    else if(url.includes('/'+c.folderId+'?')) {kind='folder';meta={id:c.folderId,name:area?.name??PERSONAL_FOLDER,mimeType:'application/vnd.google-apps.folder',trashed:false,appProperties:{gymPersonalM1:c.marker},
      ...(area?{parents:[options.project!.dev.id],ownedByMe:true,shared:false,permissions:[{id:c.permissionId,type:'user',role:'owner'}]}:{})};}
    else if(options.project&&[options.project.root,options.project.dev].some(pin=>url.includes('/'+pin.id+'?'))) {
      const pin=[options.project.root,options.project.dev].find(pin=>url.includes('/'+pin.id+'?'))!;
      kind='project';meta={id:pin.id,name:pin.name,parents:[pin.parentId],mimeType:'application/vnd.google-apps.folder',trashed:false,ownedByMe:true,shared:false,
        appProperties:{[pin.propertyName]:pin.marker},permissions:[{id:c.permissionId,type:'user',role:'owner'}]};
    }
    else {kind='file';meta={id:'synthetic-file',name:`PERSONAL-session-${session.sessionId}.json`,mimeType:'application/json',parents:[c.folderId],trashed:false,appProperties:{gymPersonalM1:c.marker}};}
    options.edit?.(meta,kind); return new Response(JSON.stringify(meta));
  }) as typeof fetch;
  const reader=new PersonalDriveReader(c,async(kind,signal)=>{if(signal.aborted)throw new Error();budgets.push(kind);options.consume?.(kind);},dialogs,fetcher,()=>1000);
  return {reader,calls,budgets,dialogs,fetcher};
}
describe('selected personal Drive reader',()=>{
  it('reads the same recording after relocation only through pinned owner-only ancestors',async()=>{
    const h=harness({project:{...layout,phase:'active'}});
    expect((await h.reader.open(new AbortController().signal)).record.sessionId).toBe(session.sessionId);
    expect(h.calls).toHaveLength(6);expect(h.calls[2].url).toContain('/'+layout.root.id+'?');expect(h.calls[3].url).toContain('/'+layout.dev.id+'?');
    expect(h.calls.filter(c=>c.url.includes('alt=media'))).toHaveLength(1);
  });
  it('rejects changed hierarchy before recording metadata or content reads',async()=>{
    for(const [kind,patch] of [['project',{shared:true}],['project',{parents:['outside']}],['folder',{parents:[layout.test.id]}],['folder',{name:projectArea(layout,'personal').sourceName}]] as const) {
      const h=harness({project:{...layout,phase:'active'},edit:(m,k)=>{if(k===kind)Object.assign(m,patch);}});
      await expect(h.reader.open(new AbortController().signal)).rejects.toThrow('hierarchy');
      expect(h.calls.some(c=>c.url.includes('/synthetic-file?'))).toBe(false);
    }
  });
  it('pins browser budget requests to the reviewed layout revision',async()=>{
    const area=projectArea(layout,'personal');const c={...config,folderId:area.id,marker:area.marker,projectLayout:layout};
    const fake=vi.fn(async(input:unknown)=>String(input).endsWith('/config')?new Response(JSON.stringify(c)):new Response(null,{status:204}));
    vi.stubGlobal('fetch',fake);try {
      const signal=new AbortController().signal;const connection=await privateConnection(signal);await connection.consume('request',signal);
      expect(JSON.parse((fake.mock.calls[1] as unknown as [unknown,RequestInit])[1].body as string)).toMatchObject({allocationId:c.allocationId,kind:'request',layoutHash:expect.stringMatching(/^[a-f0-9]{64}$/)});
    }finally {vi.unstubAllGlobals();}
  });
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
  it('works without modern static AbortSignal helpers',async()=>{
    const savedAny=AbortSignal.any,savedTimeout=AbortSignal.timeout;
    try {Object.defineProperty(AbortSignal,'any',{value:undefined,configurable:true});Object.defineProperty(AbortSignal,'timeout',{value:undefined,configurable:true});
      const h=harness();expect((await h.reader.open(new AbortController().signal)).record.sessionId).toBe(session.sessionId);
    } finally {Object.defineProperty(AbortSignal,'any',{value:savedAny,configurable:true});Object.defineProperty(AbortSignal,'timeout',{value:savedTimeout,configurable:true});}
  });
  it('distinguishes connection failures from invalid metadata without echoing either body',async()=>{
    const failed=harness({failure:true});await expect(failed.reader.open(new AbortController().signal)).rejects.toThrow('Unable to contact');
    const malformed=harness({metadataBody:'not-json-private'});await expect(malformed.reader.open(new AbortController().signal)).rejects.toThrow('account metadata (JSON syntax)');
    const array=harness({metadataBody:'[]'});await expect(array.reader.open(new AbortController().signal)).rejects.toThrow('account metadata (object shape)');
  });
  it('loads only local private settings and durably reserves with the caller cancellation signal',async()=>{
    const fake=vi.fn(async(input:unknown,init?:RequestInit)=>String(input).endsWith('/config')?new Response(JSON.stringify(config)):new Response(null,{status:204}));
    vi.stubGlobal('fetch',fake);try {const signal=new AbortController().signal;const connection=await privateConnection(signal);await connection.consume('request',signal);
      expect(fake.mock.calls[1][1]).toMatchObject({method:'POST',signal,credentials:'same-origin'});
      expect(JSON.parse(fake.mock.calls[1][1]!.body as string)).toEqual({allocationId:config.allocationId,kind:'request'});
    } finally {vi.unstubAllGlobals();}
  });
  it('binds the default native fetch to its global receiver',async()=>{
    const h=harness();const fake=function(this:unknown,input:string|URL|Request,init?:RequestInit){expect(this).toBe(globalThis);return h.fetcher(input,init);};
    vi.stubGlobal('fetch',fake);try {const reader=new PersonalDriveReader(checkConfig(config),async()=>{},h.dialogs,undefined,()=>1000);
      expect((await reader.open(new AbortController().signal)).record.sessionId).toBe(session.sessionId);
    } finally {vi.unstubAllGlobals();}
  });
  it('never reuses an authorization grant across opens',async()=>{
    const h=harness();await h.reader.open(new AbortController().signal);await h.reader.open(new AbortController().signal);
    expect(h.dialogs.authorize).toHaveBeenCalledTimes(2);
  });
});
