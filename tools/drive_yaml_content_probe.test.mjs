import test from 'node:test';
import assert from 'node:assert/strict';
import {runYamlContentProof,YAML_PROOF_SOURCE,YAML_PROOF_FIELDS} from './drive_yaml_content_probe.mjs';
const pin=(id,title,parentId,propertyName='gymYamlProofM1',marker='synthetic-proof-marker')=>({id,title,parentId,propertyName,marker});
const pins={permissionId:'synthetic-owner',root:pin('root-test','Gym Assistant','my-drive-test','gymProjectM1'),
  test:pin('test-env','test','root-test','gymEnvironmentM1'),proof:pin('proof-folder','YAML CONDITIONAL PROOF','test-env'),
  subject:pin('proof-subject','SYNTHETIC-conditional-source.yaml','proof-folder')};
function setup(){
  const metadata=(p,mimeType)=>({id:p.id,title:p.title,mimeType,parents:[{id:p.parentId}],etag:'"original"',ownedByMe:true,shared:false,
    labels:{trashed:false},properties:[{key:p.propertyName,value:p.marker,visibility:'PRIVATE'}],permissions:[{id:pins.permissionId,type:'user',role:'owner'}]});
  const data=Object.fromEntries([pins.root,pins.test,pins.proof,pins.subject].map(p=>[p.id,metadata(p,p===pins.subject?'text/plain':'application/vnd.google-apps.folder')]));
  const state={saved:null,lease:true,writes:0,text:YAML_PROOF_SOURCE.before,loseAck:false,acceptStale:false,account:pins.permissionId,
    subjectReads:0,changeDuringRead:false,rawMetadata:null,afterRequest:null};
  const calls=[];
  const journal={requireCurrent:async()=>assert.ok(state.lease),load:async()=>structuredClone(state.saved),observe:async()=>assert.ok(state.lease),
    transition:async(before,next)=>{assert.ok(state.lease);assert.deepEqual(state.saved,before);state.saved=structuredClone(next);}};
  const response=(status,value)=>({status,text:async()=>typeof value==='string'?value:JSON.stringify(value)});
  const client={request:async(path,init)=>{
    calls.push({path,init});let result;
    if(path==='/drive/v3/about?fields=user(permissionId)')result=response(200,{user:{permissionId:state.account}});
    else {
      const url=new URL('https://www.googleapis.com'+path),id=url.pathname.split('/').at(-1);assert.ok(data[id]);
      if(!init){
        if(url.searchParams.get('alt')==='media')result=response(200,state.text);
        else {
          assert.equal(url.searchParams.get('fields'),YAML_PROOF_FIELDS);
          if(id===pins.subject.id){state.subjectReads++;if(state.changeDuringRead&&state.subjectReads===2)data[id].etag='"external-edit"';}
          result=response(200,state.rawMetadata??structuredClone(data[id]));
        }
      }else {
        assert.equal(id,pins.subject.id);assert.equal(init.method,'PUT');assert.equal(init.headers['Content-Type'],'text/plain');
        assert.equal(url.pathname,`/upload/drive/v2/files/${id}`);assert.equal(url.searchParams.get('uploadType'),'media');assert.equal(url.searchParams.get('fields'),'id');
        if(init.headers['If-Match']!==data[id].etag&&!state.acceptStale)result=response(412,'');
        else {state.writes++;state.text=init.body;data[id].etag='"written-'+state.writes+'"';
          if(state.loseAck){state.loseAck=false;throw new Error('Synthetic lost acknowledgement');}
          result=response(200,{id});}
      }
    }
    state.afterRequest?.(calls.at(-1));return result;
  }};
  return {data,state,calls,client,journal,run:()=>runYamlContentProof(client,pins,journal)};
}
test('exact YAML media write accepts current tag, rejects stale tag and retains bytes',async()=>{
  const f=setup();assert.deepEqual(await f.run(),{staleWriteRejected:true,mimeType:'text/plain',requests:27});
  assert.equal(f.calls.length,27);assert.equal(f.state.writes,1);assert.equal(f.state.saved.stage,'proved');assert.equal(f.state.text,YAML_PROOF_SOURCE.accepted);
  const writes=f.calls.filter(c=>c.init);assert.equal(writes.length,2);assert.notEqual(writes[0].init.body,writes[1].init.body);
  assert.ok(writes.every(c=>c.init.headers['If-Match']==='"original"'));
  await f.run();assert.equal(f.calls.filter(c=>c.init).length,2);assert.equal(f.state.writes,1);
});
test('lost accepted-write acknowledgement resumes by same ID without repeating it',async()=>{
  const f=setup();f.state.loseAck=true;await assert.rejects(f.run());assert.equal(f.state.saved.stage,'captured');assert.equal(f.state.writes,1);
  await f.run();assert.equal(f.state.writes,1);assert.equal(f.state.saved.stage,'proved');
  assert.equal(f.calls.filter(c=>c.init?.body===YAML_PROOF_SOURCE.accepted).length,1);
});
test('account, hierarchy, ownership, access and tag mismatches stop without writing',async()=>{
  for(const patch of [{parents:[{id:'outside-test'}]},{ownedByMe:'true'},{shared:true},{permissions:[]},{etag:'W/"weak"'},{mimeType:'application/json'},
    {properties:[]},{labels:{trashed:true}}]){
    const f=setup();Object.assign(f.data[pins.subject.id],patch);await assert.rejects(f.run());assert.equal(f.state.writes,0);assert.equal(f.calls.filter(c=>c.init).length,0);
  }
  const f=setup();f.state.account='other-synthetic-owner';await assert.rejects(f.run());assert.equal(f.calls.length,1);
  const g=setup();g.data[pins.test.id].parents=[{id:'outside-test'}];await assert.rejects(g.run());assert.equal(g.calls.filter(c=>c.path.includes(pins.subject.id)).length,0);
});
test('unexpected bytes, changing reads and duplicate metadata cannot produce a write',async()=>{
  for(const change of [f=>{f.state.text='fixture: unexpected\n';},f=>{f.state.changeDuringRead=true;},f=>{f.state.rawMetadata='{"id":"first","id":"second"}';}]){
    const f=setup();change(f);await assert.rejects(f.run());assert.equal(f.state.writes,0);
  }
});
test('stale-write acceptance or a later external edit cannot produce proof',async()=>{
  const f=setup();f.state.acceptStale=true;await assert.rejects(f.run());assert.notEqual(f.state.saved.stage,'proved');
  const g=setup();g.state.afterRequest=call=>{if(call.init?.body===YAML_PROOF_SOURCE.stale)g.state.text=YAML_PROOF_SOURCE.before;};
  await assert.rejects(g.run());assert.notEqual(g.state.saved.stage,'proved');
});
test('expired journal ownership prevents mutation and acknowledgement',async()=>{
  const f=setup();f.state.lease=false;await assert.rejects(f.run());assert.equal(f.calls.length,0);
  const g=setup();g.state.afterRequest=call=>{if(call.init)g.state.lease=false;};
  await assert.rejects(g.run());assert.equal(g.state.writes,1);assert.equal(g.state.saved.stage,'captured');
});
test('changed captured pins or source tags stop without repairing the file',async()=>{
  const f=setup();f.state.loseAck=true;await assert.rejects(f.run());f.state.saved.pinJSON='{}';
  await assert.rejects(f.run());assert.equal(f.state.writes,1);
  const g=setup();g.state.loseAck=true;await assert.rejects(g.run());g.state.text=YAML_PROOF_SOURCE.before;
  await assert.rejects(g.run());assert.equal(g.state.writes,1);
});
test('caller pin edits after the first await cannot retarget requests',async()=>{
  const f=setup(),copy=structuredClone(pins);let edited=false;
  const journal={...f.journal,requireCurrent:async()=>{if(!edited){edited=true;copy.subject.id='another-subject';copy.proof.parentId='outside-test';}}};
  await runYamlContentProof(f.client,copy,journal);assert.ok(f.calls.every(c=>!c.path.includes('another-subject')));
  assert.equal(f.state.writes,1);
});
