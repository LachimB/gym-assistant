// One approved synthetic acceptance artifact only; never a general Drive browser.
import {FIXTURE} from './drive_conditional_probe.mjs';
export const PHONE_DELIVERY = Object.freeze({
  id:'1PpkSmoA4pjgM8mFvhzDLc8ffVV68KLnV',
  name:'SYNTHETIC-session-synthetic_delivery_sample_v1.json',
  sha256:'07c722af74d1ff2939ba65b2711e39c8ca1e4e0e8ebb29e29cd2dd4a46098b3b',
});
export async function readPhoneSyntheticDelivery(client, expectedEmail, report) {
  if (!expectedEmail?.endsWith('@gmail.com')) throw new Error('Approved personal account required');
  async function get(label,path) {
    const response=await client.request(path);
    report(`${label}: HTTP ${response.status}`);
    if(response.status!==200) throw new Error('Unexpected status; stopped without retry or scope change');
    return response;
  }
  const account=await (await get('Verify personal browser account','/drive/v3/about?fields=user%28emailAddress%29')).json();
  if(account.user?.emailAddress!==expectedEmail) throw new Error('Account mismatch; no file access');
  const parent=await (await get('Verify labelled synthetic destination',`/drive/v3/files/${FIXTURE.folder}?fields=name,mimeType,appProperties`)).json();
  if(parent.name!==`Gym Assistant — SYNTHETIC M1 TEST — ${FIXTURE.marker}` || parent.mimeType!=='application/vnd.google-apps.folder' || parent.appProperties?.gymSyntheticM1!==FIXTURE.marker) throw new Error('Synthetic destination mismatch');
  const metadata=await (await get('Verify exact phone-created synthetic file',`/drive/v3/files/${PHONE_DELIVERY.id}?fields=id,name,mimeType,parents,appProperties,size`)).json();
  const size=Number(metadata.size);
  if(metadata.id!==PHONE_DELIVERY.id || metadata.name!==PHONE_DELIVERY.name || metadata.mimeType!=='application/json' ||
      metadata.parents?.length!==1 || metadata.parents[0]!==FIXTURE.folder || metadata.appProperties?.gymSyntheticM1!==FIXTURE.marker ||
      !Number.isSafeInteger(size) || size<1 || size>65536) throw new Error('Exact synthetic metadata mismatch; no content read');
  const q=encodeURIComponent(`'${FIXTURE.folder}' in parents and trashed = false and name = '${PHONE_DELIVERY.name}'`);
  const list=await (await get('Check one matching file visible to this app grant',`/drive/v3/files?q=${q}&fields=files(id),nextPageToken`)).json();
  if(list.nextPageToken || list.files?.length!==1 || list.files[0].id!==PHONE_DELIVERY.id) throw new Error('Matching synthetic files are ambiguous; no content read');
  const response=await get('Read exact synthetic canonical bytes',`/drive/v3/files/${PHONE_DELIVERY.id}?alt=media`);
  const reader=response.body.getReader();const chunks=[];let total=0;
  try {
    while(true) {const {done,value}=await reader.read();if(done)break;total+=value.length;if(total>65536)throw new Error('Synthetic content exceeds limit');chunks.push(value);}
  } finally {await reader.cancel();}
  const bytes=new Uint8Array(total);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
  if(total!==size)throw new Error('Synthetic content length changed');
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),n=>n.toString(16).padStart(2,'0')).join('');
  if(hash!==PHONE_DELIVERY.sha256)throw new Error('Canonical bytes differ from the verified phone delivery; no download');
  const text=new TextDecoder('utf-8',{fatal:true}).decode(bytes);const session=JSON.parse(text);
  if(session.schemaVersion!=='1.1.0' || session.sessionId!=='synthetic_delivery_sample_v1' || session.templateId!=='template_composite_demo' || session.status!=='completed' || session.completionKind!=='plannedEnd' || session.timing?.observedRunMs!==50000) throw new Error('Unexpected synthetic session contract');
  report('PASS: browser grant reads the exact phone-delivered canonical bytes. One matching file visible in the test folder; no duplicate from retry observed. Download is synthetic only.');
  return text;
}
