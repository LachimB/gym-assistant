import {readFileSync} from 'node:fs';
import {describe,it,expect} from 'vitest';
import {checkProjectLayout,readStrictProjectJSON,verifyProjectArea,projectArea,projectAreaPath} from './ProjectDriveLayout.mjs';
const raw=readFileSync(new URL('../../../examples/project-layout/synthetic-layout.json',import.meta.url),'utf8');
const config=checkProjectLayout(readStrictProjectJSON(raw));
const area=projectArea(config,'personal');
const folder=(id:string,name:string,parent:string,key:string,marker:string)=>({id,name,parents:[parent],mimeType:'application/vnd.google-apps.folder',
  ownedByMe:true,trashed:false,shared:false,appProperties:{[key]:marker},permissions:[{id:config.permissionId,type:'user',role:'owner'}]});
const leaf=(final:boolean)=>folder(area.id,final?area.name:area.sourceName,final?config.dev.id:area.sourceParentId,'gymPersonalM1',area.marker);
const ancestors=Object.fromEntries([config.root,config.dev,config.test,config.prod].map(p=>[p.id,folder(p.id,p.name,p.parentId,p.propertyName,p.marker)]));
describe('pinned project layout',()=>{
  it('uses the shared synthetic fixture and freezes every nested binding',()=>{
    expect(projectAreaPath(config,'personal')).toBe('Gym Assistant/dev/PERSONAL M1');
    expect(projectAreaPath(config,'synthetic')).toBe('Gym Assistant/test/SYNTHETIC M1');
    expect(Object.isFrozen(config.areas[0])).toBe(true);expect(Object.isFrozen(config.root)).toBe(true);
    expect(()=>checkProjectLayout({...config,dev:{...config.dev,id:area.id}})).toThrow();
    expect(()=>checkProjectLayout({...config,areas:[{...area,environment:'prod'},config.areas[1]]})).toThrow();
  });
  it('rejects decoded duplicate keys, oversized input, nesting and unknown fields without echoing values',()=>{
    for(const text of ['{"phase":"a","ph\\u0061se":"b"}', '{"x":{"a":1,"a":2}}', ' '.repeat(32769),'{"x":'+ '['.repeat(18)+'0'+']'.repeat(18)+'}'])
      expect(()=>readStrictProjectJSON(text)).toThrow('Invalid project layout');
    expect(()=>checkProjectLayout({...config,unknown:true})).toThrow();
  });
  it('accepts only complete source/final pairs in the right transition phase',()=>{
    expect(verifyProjectArea(config,'personal',config.permissionId,leaf(false))).toBe(area.sourceName);
    expect(()=>verifyProjectArea(config,'personal',config.permissionId,leaf(true))).toThrow();
    for(const final of [false,true])expect(verifyProjectArea({...config,phase:'adopting'},'personal',config.permissionId,leaf(final),ancestors)).toBe(final?area.name:area.sourceName);
    expect(verifyProjectArea({...config,phase:'active'},'personal',config.permissionId,leaf(true),ancestors)).toBe(area.name);
    for(const changed of [leaf(false),{...leaf(true),name:area.sourceName},{...leaf(false),name:area.name},{...leaf(true),parents:[config.test.id]}])
      expect(()=>verifyProjectArea({...config,phase:'active'},'personal',config.permissionId,changed,ancestors)).toThrow();
  });
  it('rejects absent ancestors, changed root, sharing, wrong account and blocked configuration',()=>{
    const active={...config,phase:'active' as const};
    expect(()=>verifyProjectArea(active,'personal',config.permissionId,leaf(true))).toThrow();
    for(const patch of [{shared:true},{ownedByMe:false},{ownedByMe:'true'},{parents:['outside']},{appProperties:{gymProjectM1:'other'}}])
      expect(()=>verifyProjectArea(active,'personal',config.permissionId,leaf(true),{...ancestors,[config.root.id]:{...ancestors[config.root.id],...patch}})).toThrow();
    expect(()=>verifyProjectArea(active,'personal','another-account',leaf(true),ancestors)).toThrow();
    expect(()=>verifyProjectArea({...config,phase:'blocked'},'personal',config.permissionId,leaf(false))).toThrow();
  });
});
