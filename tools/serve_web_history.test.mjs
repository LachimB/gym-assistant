import {test} from 'node:test';
import {request as httpRequest} from 'node:http';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {checkProjectLayout} from '../apps/web/src/ProjectDriveLayout.mjs';
import {historyServer,consumeBudget} from './serve_web_history.mjs';
const fresh=()=>({version:1,allocationId:'synthetic-allocation',reservationVerified:true,globalAllocated:162,used:{request:0,authorization:0,picker:0}});
test('durable allowance fails closed and preserves other counters',()=>{
 const initial=fresh();const next=consumeBudget(initial,initial.allocationId,'request');assert.equal(next.used.request,1);assert.equal(initial.used.request,0);
 for(const changed of [{...initial,reservationVerified:false},{...initial,globalAllocated:501},{...initial,used:{request:-1,authorization:0,picker:0}},
   {...initial,used:{request:30,authorization:0,picker:0}}]) assert.throws(()=>consumeBudget(changed,initial.allocationId,'request'));
 assert.throws(()=>consumeBudget(initial,'different','request'));assert.throws(()=>consumeBudget(initial,initial.allocationId,'other'));
});
test('a separately reserved verification batch can lower but never raise existing maximum caps',()=>{
 const old={...fresh(),used:{request:7,authorization:2,picker:2}};
 const batch={...fresh(),allocationId:'synthetic-new-bounded-batch',caps:{request:6,authorization:1,picker:1},globalAllocated:420};
 const once=consumeBudget(batch,batch.allocationId,'picker');assert.equal(once.used.picker,1);assert.equal(batch.used.picker,0);
 assert.throws(()=>consumeBudget(once,batch.allocationId,'picker'));
 assert.throws(()=>consumeBudget({...batch,used:{request:6,authorization:0,picker:0}},batch.allocationId,'request'));
 for(const caps of [null,[],{request:31,authorization:1,picker:1},{request:6,authorization:5,picker:1},{request:6,authorization:1,picker:3},
  {request:6,authorization:1},{request:6,authorization:1,picker:0},{request:6,authorization:1,picker:1,extra:1}])
  assert.throws(()=>consumeBudget({...batch,caps},batch.allocationId,'request'));
 assert.throws(()=>consumeBudget(batch,old.allocationId,'request'));
 assert.throws(()=>consumeBudget(old,old.allocationId,'picker'));assert.equal(old.used.picker,2);
});
test('layout cutover invalidates stale browser budget grants without resetting counters',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'gym-synthetic-layout-'));const root=join(dir,'public');mkdirSync(root);
 writeFileSync(join(root,'index.html'),'<h1>Synthetic reader</h1>');
 const layout=checkProjectLayout(JSON.parse(readFileSync(new URL('../examples/project-layout/synthetic-layout.json',import.meta.url),'utf8')));
 const area=layout.areas.find(a=>a.kind==='personal');
 const config={allocationId:'synthetic-allocation',apiKey:'synthetic-placeholder',appId:'900',clientId:'synthetic-client',email:'owner@example.test',folderId:area.id,marker:area.marker,permissionId:layout.permissionId,projectLayout:layout};
 const configPath=join(dir,'connection.json'),ledgerPath=join(dir,'ledger.json');writeFileSync(configPath,JSON.stringify(config));writeFileSync(ledgerPath,JSON.stringify(fresh()));
 const server=historyServer({root,configPath,ledgerPath,port:9000});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const actual=server.address().port;
 const call=body=>new Promise((resolve,reject)=>{
  const req=httpRequest({hostname:'127.0.0.1',port:actual,path:'/__private/drive-reader/budget',method:'POST',headers:{Host:'127.0.0.1:9000',Origin:'http://127.0.0.1:9000','Content-Type':'application/json','X-Gym-Local-Reader':'1'}},res=>{res.resume();res.on('end',()=>resolve(res.statusCode));});
  req.on('error',reject);req.end(JSON.stringify(body));
 });
 const hash=l=>createHash('sha256').update(JSON.stringify(checkProjectLayout(l))).digest('hex');
 const payload={allocationId:config.allocationId,kind:'request',layoutHash:hash(layout)};
 try {
  assert.equal(await call(payload),204);assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,1);
  writeFileSync(configPath,JSON.stringify({...config,projectLayout:{...layout,phase:'active'}}));
  assert.equal(await call(payload),403);assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,1);
  assert.equal(await call({...payload,layoutHash:hash({...layout,phase:'active'})}),204);
  writeFileSync(configPath,JSON.stringify({...config,folderId:'unrelated-folder'}));
  assert.equal(await call(payload),403);assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,2);
  writeFileSync(configPath,JSON.stringify(config).replace('{','{"projectLayout":null,'));
  assert.equal(await call(payload),403);assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,2);
 }finally {await new Promise(r=>server.close(r));rmSync(dir,{recursive:true});}
});
test('loopback config and persisted reservation reject cross-origin, traversal and resets',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'gym-synthetic-reader-'));const root=join(dir,'public');mkdirSync(root);
 writeFileSync(join(root,'index.html'),'<h1>Synthetic reader</h1>');
 const config={allocationId:'synthetic-allocation',apiKey:'synthetic-placeholder',appId:'900',clientId:'synthetic-client',email:'owner@example.test',folderId:'synthetic-folder',marker:'synthetic-marker',permissionId:'synthetic-account'};
 const configPath=join(dir,'connection.json'),ledgerPath=join(dir,'ledger.json');writeFileSync(configPath,JSON.stringify(config));writeFileSync(ledgerPath,JSON.stringify(fresh()));
 const server=historyServer({root,configPath,ledgerPath,port:9000});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const actual=server.address().port;const headers={Host:'127.0.0.1:9000'};
 const call=(path,init={})=>new Promise((resolve,reject)=>{
  const req=httpRequest({hostname:'127.0.0.1',port:actual,path,method:init.method??'GET',headers:{...headers,...init.headers}},res=>{res.resume();res.on('end',()=>resolve({status:res.statusCode}));});
  req.on('error',reject);if(init.body)req.write(init.body);req.end();
 });
 try {
  assert.equal((await call('/')).status,200);
  assert.equal((await call('/__private/drive-reader/config')).status,200);
  assert.equal((await call('/__private/drive-reader/config',{headers:{Origin:'https://outside.invalid'}})).status,403);
  assert.equal((await call('/__private/drive-reader/config',{headers:{Host:'outside.invalid'}})).status,403);
  const payload={method:'POST',headers:{Origin:'http://127.0.0.1:9000','Content-Type':'application/json','X-Gym-Local-Reader':'1'},body:JSON.stringify({allocationId:config.allocationId,kind:'request'})};
  assert.equal((await call('/__private/drive-reader/budget',payload)).status,204);
  assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,1);
  assert.equal((await call('/__private/drive-reader/budget',{...payload,headers:{'Content-Type':'application/json'}})).status,403);
  assert.equal((await call('/%2e%2e%2fconnection.json')).status,403);
  assert.equal((await call('/connection.json')).status,403);
  writeFileSync(ledgerPath,JSON.stringify({...fresh(),used:{request:30,authorization:0,picker:0}}));
  assert.equal((await call('/__private/drive-reader/budget',payload)).status,403);
  assert.equal(JSON.parse(readFileSync(ledgerPath)).used.request,30);
 } finally {await new Promise(r=>server.close(r));rmSync(dir,{recursive:true});}
});
