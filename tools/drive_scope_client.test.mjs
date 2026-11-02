import test from 'node:test';
import assert from 'node:assert/strict';
import {DriveScopeClient, reserveBudget, isExternalSyntheticFixture} from './drive_scope_client.mjs';
function budget(initial = '0') {
  let value = initial;
  return {getItem: () => value, setItem: (_, next) => {value = next;}};
}
test('separate fixture setup can only consume budget, never reset it', () => {
  const storage = budget('12'); reserveBudget(storage, 4); assert.equal(storage.getItem(), '16');
  for (const count of [0, -4, 1.5, 485, NaN]) assert.throws(() => reserveBudget(storage, count), /budget/);
  assert.equal(storage.getItem(), '16');
});
test('external-file content is read only after exact synthetic metadata matches', () => {
  const expected = {name:'SYNTHETIC-external-child.json',mimeType:'application/json',parents:['folder']};
  assert.equal(isExternalSyntheticFixture(expected, 'folder'), true);
  for (const changed of [{name:'private.json'}, {mimeType:'application/pdf'}, {parents:['other']}, {parents:['folder','other']}]) {
    assert.equal(isExternalSyntheticFixture({...expected,...changed}, 'folder'), false);
  }
});
test('browser transport invokes native fetch on its global receiver', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = function () { assert.equal(this, globalThis); return Promise.resolve({status: 200}); };
  try { assert.equal((await new DriveScopeClient('fixture', budget()).request('/drive/v3/files')).status, 200); }
  finally { globalThis.fetch = original; }
});
test('budget persists failed requests and prevents request 501', async () => {
  const storage = budget('499'); let calls = 0;
  const client = new DriveScopeClient('fixture', storage, async () => {calls++; throw new Error('offline');});
  await assert.rejects(client.request('/drive/v3/files'), /offline/);
  await assert.rejects(client.request('/drive/v3/files'), /budget/);
  assert.equal(calls, 1); assert.equal(storage.getItem(), '500');
});
test('failed budget persistence and invalid budget both fail closed', async () => {
  let calls = 0;
  const fetcher = async () => {calls++;};
  await assert.rejects(new DriveScopeClient('fixture', {getItem: () => '0', setItem: () => {throw new Error('storage');}}, fetcher).request('/drive/v3/files'), /storage/);
  await assert.rejects(new DriveScopeClient('fixture', budget('NaN'), fetcher).request('/drive/v3/files'), /budget/);
  assert.equal(calls, 0);
});
test('credentials cannot go to another service or be forwarded by redirect', async () => {
  let sent;
  const client = new DriveScopeClient('fixture', budget(), async (...args) => {sent = args; return {status: 403};});
  for (const path of ['https://evil.example/drive/v3/files', '/oauth2/v3/userinfo', '/drive/v3/files/../../x']) {
    await assert.rejects(client.request(path), /Endpoint/);
  }
  const result = await client.request('/drive/v3/files?fields=id');
  assert.equal(result.status, 403); // No hidden retry or permission widening.
  assert.equal(sent[0], 'https://www.googleapis.com/drive/v3/files?fields=id');
  assert.equal(sent[1].redirect, 'error');
  assert.equal(sent[1].credentials, 'omit');
  assert.equal(sent[1].referrerPolicy, 'no-referrer');
  assert.equal(sent[1].headers.Authorization, 'Bearer fixture');
});
