// Approved, bounded synthetic experiment only. Not a production storage adapter.
export const FIXTURE = Object.freeze({
  folder: '1r2LHV_D0E97USw2eb6tDnZGPD1zWMNMT',
  child: '1XMMi-8UlkMPy4z3X4dUk5Zg1hNAVVtbo',
  marker: 'c1d2a450-f45f-43c5-846e-df40845bd21e',
  name: 'SYNTHETIC-external-child.json',
});
const isStrongTag = value => typeof value === 'string' && /^"[\x21\x23-\x7e]+"$/.test(value);
export async function probeConditionalContent(client, expectedEmail, report, nonce) {
  if (!expectedEmail || !expectedEmail.endsWith('@gmail.com')) throw new Error('Personal test account required');
  if (!/^[a-zA-Z0-9-]{1,80}$/.test(nonce)) throw new Error('Invalid synthetic marker');
  const call = async (label, path, init, expected = 200) => {
    const response = await client.request(path, init);
    report(`${label}: HTTP ${response.status}`);
    if (response.status !== expected) throw new Error('Unexpected status; stopped without retry');
    return response;
  };
  const account = await (await call('Verify selected personal account', '/drive/v3/about?fields=user%28emailAddress%29')).json();
  if (account.user?.emailAddress !== expectedEmail) throw new Error('Account mismatch; no file access attempted');
  const parent = await (await call('Verify labelled synthetic folder', `/drive/v3/files/${FIXTURE.folder}?fields=name,mimeType,appProperties`)).json();
  if (parent.name !== `Gym Assistant — SYNTHETIC M1 TEST — ${FIXTURE.marker}` ||
      parent.mimeType !== 'application/vnd.google-apps.folder' || parent.appProperties?.gymSyntheticM1 !== FIXTURE.marker) {
    throw new Error('Synthetic folder mismatch; no child read attempted');
  }
  const path = `/drive/v2/files/${FIXTURE.child}`;
  const metadata = await (await call('Read exact synthetic v2 file metadata', path+'?fields=id,title,mimeType,parents(id),etag')).json();
  if (metadata.id !== FIXTURE.child || metadata.title !== FIXTURE.name || metadata.mimeType !== 'application/json' ||
      metadata.parents?.length !== 1 || metadata.parents[0].id !== FIXTURE.folder) {
    throw new Error('Synthetic child mismatch; no content read or write');
  }
  if (!isStrongTag(metadata.etag)) {
    report('OPEN: no usable strong v2 ETag. No content write attempted.');
    return {outcome:'noStrongTag', requests:3};
  }
  const content = await (await call('Read synthetic content before mutation', path+'?alt=media')).json();
  const keys = Object.keys(content);
  if (content.experiment !== 'gym-assistant-m1-synthetic' || content.createdOutsideApplicationGrant !== true ||
      content.containsRealTrainingData !== false || keys.some(key => !['experiment','createdOutsideApplicationGrant','containsRealTrainingData','conditionalProbe'].includes(key)) ||
      (content.conditionalProbe !== undefined && !/^[a-zA-Z0-9-]{1,80}$/.test(content.conditionalProbe))) {
    throw new Error('Content is not the expected made-up fixture; no write attempted');
  }
  const body = JSON.stringify({...content, conditionalProbe:nonce});
  const updatePath = `/upload/drive/v2/files/${FIXTURE.child}?uploadType=media&fields=id,etag`;
  const write = text => ({method:'PUT',headers:{'Content-Type':'application/json','If-Match':metadata.etag},body:text});
  const updated = await (await call('Write synthetic content with current ETag', updatePath, write(body))).json();
  if (updated.id !== FIXTURE.child || !isStrongTag(updated.etag) || updated.etag === metadata.etag) {
    throw new Error('Current write did not return a new strong tag; stale write not attempted');
  }
  await call('Attempt different synthetic content with stale ETag', updatePath,
    write(JSON.stringify({...content,conditionalProbe:`stale-${nonce}`.slice(0,80)})), 412);
  const retained = await (await call('Verify first content survived stale write', path+'?alt=media')).json();
  if (JSON.stringify(retained) !== body) throw new Error('Retained synthetic content differs; conflict safety unproven');
  report('PASS: Drive v2 content update accepted the current tag, rejected the stale tag with 412, and retained the first content. This does not establish v3 tag compatibility or production synchronization.');
  return {outcome:'staleWriteRejected', requests:7};
}
