import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {readPhoneSyntheticDelivery,PHONE_DELIVERY} from './drive_phone_readback.mjs';
import {FIXTURE} from './drive_conditional_probe.mjs';
// Reconstruct from the checked-in public Kotlin golden; no phone data fixture.
const session=JSON.parse(readFileSync(new URL('../examples/sessions/synthetic-human-completed-session.json',import.meta.url),'utf8'));
session.sessionId='synthetic_delivery_sample_v1';
session.events.forEach((event,index)=>{event.eventId='event_'+createHash('sha256').update(`${session.sessionId}:history-1:${index}`).digest('hex');});
const text=JSON.stringify(session);
function fixture(change={}) {
  let calls=0;
  const data=[{user:{emailAddress:'fixture@gmail.com'}},
    {name:`Gym Assistant — SYNTHETIC M1 TEST — ${FIXTURE.marker}`,mimeType:'application/vnd.google-apps.folder',appProperties:{gymSyntheticM1:FIXTURE.marker}},
    {id:PHONE_DELIVERY.id,name:PHONE_DELIVERY.name,mimeType:'application/json',parents:[FIXTURE.folder],appProperties:{gymSyntheticM1:FIXTURE.marker},size:String(Buffer.byteLength(text))},
    {files:[{id:PHONE_DELIVERY.id}]},text];
  if(change.index!==undefined)data[change.index]=change.value;
  const paths=[];
  return {paths,get calls(){return calls;},client:{request:async path=>{paths.push(path);const value=data[calls++];return new Response(typeof value==='string'?value:JSON.stringify(value),{status:200});}}};
}
test('browser reads the exact generated phone sample and only one visible matching file',async()=>{
  assert.equal(createHash('sha256').update(text).digest('hex'),PHONE_DELIVERY.sha256);
  const f=fixture();assert.equal(await readPhoneSyntheticDelivery(f.client,'fixture@gmail.com',()=>{}),text);
  assert.equal(f.calls,5);assert.ok(f.paths.every(path=>path.startsWith('/drive/v3/')));
});
test('wrong account or fixture metadata stops before content access',async()=>{
  for(const change of [{index:0,value:{user:{emailAddress:'other@gmail.com'}}},{index:1,value:{}},{index:2,value:{}}]){
    const f=fixture(change);await assert.rejects(readPhoneSyntheticDelivery(f.client,'fixture@gmail.com',()=>{}),/mismatch/);assert.equal(f.calls,change.index+1);
  }
});
test('duplicate names or incomplete listing prevent a content read',async()=>{
  for(const value of [{files:[{id:PHONE_DELIVERY.id},{id:'second'}]},{files:[{id:PHONE_DELIVERY.id}],nextPageToken:'more'}]){
    const f=fixture({index:3,value});await assert.rejects(readPhoneSyntheticDelivery(f.client,'fixture@gmail.com',()=>{}),/ambiguous/);assert.equal(f.calls,4);
  }
});
test('different bytes cannot be downloaded as the verified native record',async()=>{
  const changed=text.replace('Synthetic alternating pyramid','Altered synthetic example  ');
  const f=fixture({index:4,value:changed});await assert.rejects(readPhoneSyntheticDelivery(f.client,'fixture@gmail.com',()=>{}),/length changed|bytes differ/);assert.equal(f.calls,5);
});
