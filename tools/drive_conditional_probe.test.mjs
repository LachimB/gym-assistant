import test from 'node:test';
import assert from 'node:assert/strict';
import {probeConditionalContent, FIXTURE} from './drive_conditional_probe.mjs';
const content = {experiment:'gym-assistant-m1-synthetic',createdOutsideApplicationGrant:true,containsRealTrainingData:false};
function setup(change = {}) {
  const calls = [];
  const account = {user:{emailAddress:'<private-contact-redacted>'}};
  const parent = {name:`Gym Assistant — SYNTHETIC M1 TEST — ${FIXTURE.marker}`,mimeType:'application/vnd.google-apps.folder',appProperties:{gymSyntheticM1:FIXTURE.marker}};
  const metadata = {id:FIXTURE.child,title:FIXTURE.name,mimeType:'application/json',parents:[{id:FIXTURE.folder}],etag:'"current"'};
  const data = [account,parent,metadata,content,{id:FIXTURE.child,etag:'"new"'},null,{...content,conditionalProbe:'nonce'}];
  const statuses = [200,200,200,200,200,412,200];
  if (change.index !== undefined) data[change.index] = change.value;
  if (change.statusIndex !== undefined) statuses[change.statusIndex] = change.status;
  return {calls,client:{request:async (path,init) => {
    const index = calls.length; calls.push({path,init});
    return {status:statuses[index],json:async () => data[index]};
  }}};
}
const run = client => probeConditionalContent(client,'<private-contact-redacted>',()=>{},'nonce');
test('content compare-and-swap uses v2 tag twice and verifies retained payload', async () => {
  const {client,calls} = setup();
  assert.deepEqual(await run(client),{outcome:'staleWriteRejected',requests:7});
  assert.equal(calls.length,7);
  for (const call of calls.slice(4,6)) {assert.equal(call.init.method,'PUT');assert.equal(call.init.headers['If-Match'],'"current"');assert.match(call.path,/^\/upload\/drive\/v2\/files\//);}
  assert.notEqual(calls[4].init.body,calls[5].init.body);
});
test('wrong account stops before any folder access', async () => {
  const {client,calls} = setup({index:0,value:{user:{emailAddress:'<private-contact-redacted>'}}});
  await assert.rejects(run(client),/Account mismatch/);assert.equal(calls.length,1);
});
test('wrong folder or child stops before content access', async () => {
  for (const index of [1,2]) {const {client,calls} = setup({index,value:{}});await assert.rejects(run(client),/mismatch/);assert.equal(calls.length,index+1);}
});
test('missing weak or malformed tag never causes a write', async () => {
  for (const etag of [undefined,'W/"weak"','*','"bad\r\nvalue"']) {
    const fixture = setup();
    const client = {request:async (...args) => {
      const r = await fixture.client.request(...args);const original=r.json;
      if(fixture.calls.length === 3) r.json=async()=>({...await original(),etag});return r;
    }};
    assert.equal((await run(client)).outcome,'noStrongTag');assert.equal(fixture.calls.length,3);
  }
});
test('unexpected content is never overwritten', async () => {
  const {client,calls} = setup({index:3,value:{...content,privateField:'unexpected'}});
  await assert.rejects(run(client),/made-up fixture/);assert.equal(calls.length,4);
});
test('missing replacement tag prevents stale write attempt', async () => {
  const {client,calls} = setup({index:4,value:{id:FIXTURE.child}});
  await assert.rejects(run(client),/new strong tag/);assert.equal(calls.length,5);
});
test('accepted stale write or changed retained content never produces success', async () => {
  for (const change of [{statusIndex:5,status:200},{index:6,value:{...content,conditionalProbe:'stale-nonce'}}]) {
    const {client}=setup(change);await assert.rejects(run(client),/Unexpected status|differs/);
  }
});
