import {test} from 'node:test';
import {request as httpRequest} from 'node:http';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {historyServer,consumeBudget} from './serve_web_history.mjs';
const fresh=()=>({version:1,allocationId:'synthetic-allocation',reservationVerified:true,globalAllocated:162,used:{request:0,authorization:0,picker:0}});
test('durable allowance fails closed and preserves other counters',()=>{
 const initial=fresh();const next=consumeBudget(initial,initial.allocationId,'request');assert.equal(next.used.request,1);assert.equal(initial.used.request,0);
 for(const changed of [{...initial,reservationVerified:false},{...initial,globalAllocated:501},{...initial,used:{request:-1,authorization:0,picker:0}},
   {...initial,used:{request:30,authorization:0,picker:0}}]) assert.throws(()=>consumeBudget(changed,initial.allocationId,'request'));
 assert.throws(()=>consumeBudget(initial,'different','request'));assert.throws(()=>consumeBudget(initial,initial.allocationId,'other'));
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
