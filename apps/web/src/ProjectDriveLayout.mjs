const fail=()=>{throw new Error('Invalid project layout or namespace.');};
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v) ? v : fail();
const keys=(v,names)=>{object(v);if(Object.keys(v).sort().join(',')!==names.split(',').sort().join(','))fail();};
const id=v=>{if(typeof v!=='string'||! /^[A-Za-z0-9_-]{1,128}$/.test(v))fail();return v;};
const name=v=>{if(typeof v!=='string'||!v.trim()||new TextEncoder().encode(v).length>4096||/[\u0000-\u001f\u007f-\u009f]/.test(v))fail();return v;};
const marker=v=>{if(typeof v!=='string'||! /^[A-Za-z0-9_-]{3,160}$/.test(v))fail();return v;};
const phases=['reserved','verified','adopting','active','blocked'];

/** Bounded JSON-only parsing with decoded duplicate-key checks; no values enter error messages. */
export function readStrictProjectJSON(text) {
  if(typeof text!=='string'||new TextEncoder().encode(text).length>32768)fail();
  let p=0,nodes=0;const space=()=>{while(/[\x20\t\r\n]/.test(text[p]??'\0'))p++;};
  function string(){const start=p;if(text[p++]!=='"')fail();while(p<text.length){const c=text[p++];if(c==='\\'){p++;continue;}if(c==='"'){try{return JSON.parse(text.slice(start,p));}catch{fail();}}}fail();}
  function value(depth){if(depth>16||++nodes>512)fail();space();const c=text[p];
    if(c==='{'){p++;space();const seen=new Set();if(text[p]==='}'){p++;return;}while(true){space();const k=string();if(seen.has(k)||seen.size>=64)fail();seen.add(k);space();if(text[p++]!==':')fail();value(depth+1);space();const end=text[p++];if(end==='}')return;if(end!==',')fail();}}
    if(c==='['){p++;space();let count=0;if(text[p]===']'){p++;return;}while(true){if(++count>100)fail();value(depth+1);space();const end=text[p++];if(end===']')return;if(end!==',')fail();}}
    if(c==='"'){string();return;}
    const literal=/^(?:true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/.exec(text.slice(p));if(!literal)fail();p+=literal[0].length;
  }
  value(0);space();if(p!==text.length)fail();try{return object(JSON.parse(text));}catch{fail();}
}
export function checkProjectLayout(value) {
  const v=object(value);keys(v,'schemaVersion,bindingId,permissionId,phase,root,dev,test,prod,areas');
  if(v.schemaVersion!=='1.0.0'||!phases.includes(v.phase)||typeof v.bindingId!=='string'||! /^[A-Za-z0-9_-]{3,128}$/.test(v.bindingId))fail();id(v.permissionId);
  const pin=(x,expected,key)=>{keys(x,'id,parentId,name,propertyName,marker');id(x.id);id(x.parentId);name(x.name);marker(x.marker);if(x.id===x.parentId||x.name!==expected||x.propertyName!==key)fail();return Object.freeze({...x});};
  const root=pin(v.root,'Gym Assistant','gymProjectM1');if(! /^gym-project-[A-Za-z0-9_-]{3,128}$/.test(root.marker))fail();
  const dev=pin(v.dev,'dev','gymEnvironmentM1'),test=pin(v.test,'test','gymEnvironmentM1'),prod=pin(v.prod,'prod','gymEnvironmentM1');
  if([dev,test,prod].some(x=>x.parentId!==root.id)||!Array.isArray(v.areas)||v.areas.length!==2)fail();
  const areas=v.areas.map(a=>{keys(a,'kind,id,sourceName,sourceParentId,name,environment,marker');id(a.id);id(a.sourceParentId);name(a.sourceName);marker(a.marker);
    if(!['personal','synthetic'].includes(a.kind)||a.id===a.sourceParentId||a.environment!==(a.kind==='personal'?'dev':'test')||a.name!==(a.kind==='personal'?'PERSONAL M1':'SYNTHETIC M1'))fail();
    if(a.kind==='personal'&&! /^personal-m1-[A-Za-z0-9_-]{1,128}$/.test(a.marker))fail();return Object.freeze({...a});});
  const ids=[root.id,dev.id,test.id,prod.id,...areas.map(a=>a.id)];
  if(new Set(ids).size!==ids.length||new Set(areas.map(a=>a.kind)).size!==2||ids.includes(root.parentId)||areas.some(a=>ids.includes(a.sourceParentId)))fail();
  return Object.freeze({schemaVersion:v.schemaVersion,bindingId:v.bindingId,permissionId:v.permissionId,phase:v.phase,root,dev,test,prod,areas:Object.freeze(areas)});
}
export function projectArea(layout,kind){return layout.areas.find(a=>a.kind===kind)??fail();}
export const needsProjectAncestors=phase=>['verified','adopting','active'].includes(phase);
export function projectAreaPath(layout,kind){const area=projectArea(layout,kind);return 'Gym Assistant/'+area.environment+'/'+area.name;}
export const PROJECT_FOLDER_FIELDS='id,name,mimeType,parents,appProperties,trashed,ownedByMe,shared,permissions(id,type,role,deleted)';
function folder(data,expectedId,key,expectedMarker,permission) {
  const f=object(data);if(f.id!==expectedId||f.mimeType!=='application/vnd.google-apps.folder'||f.ownedByMe!==true||f.trashed!==false||f.shared!==false||object(f.appProperties)[key]!==expectedMarker)fail();
  if(!Array.isArray(f.parents)||f.parents.length!==1||typeof f.parents[0]!=='string'||!Array.isArray(f.permissions)||f.permissions.length!==1)fail();
  const owner=object(f.permissions[0]);if(owner.id!==permission||owner.type!=='user'||owner.role!=='owner'||owner.deleted!==undefined&&owner.deleted!==false)fail();return f;
}
export function verifyProjectArea(layout,kind,permissionId,metadata,ancestors={}) {
  if(permissionId!==layout.permissionId||layout.phase==='blocked')fail();
  const area=projectArea(layout,kind),env=layout[area.environment],key=kind==='personal'?'gymPersonalM1':'gymSyntheticM1';
  const f=folder(metadata,area.id,key,area.marker,permissionId);
  const source=f.name===area.sourceName&&f.parents[0]===area.sourceParentId,target=f.name===area.name&&f.parents[0]===env.id;
  if(['reserved','verified'].includes(layout.phase)?!source:layout.phase==='adopting'?!source&&!target:!target)fail();
  if(needsProjectAncestors(layout.phase))for(const pin of [layout.root,env]) {
    const actual=folder(ancestors[pin.id],pin.id,pin.propertyName,pin.marker,permissionId);
    if(actual.name!==pin.name||actual.parents[0]!==pin.parentId)fail();
  }
  return f.name;
}
