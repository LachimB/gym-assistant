import test from 'node:test';import assert from 'node:assert/strict';
import {runFolderMetadataProof} from './drive_folder_metadata_probe.mjs';
const pin=(id,title,parentId,propertyName='gymConsolidationM1',marker='synthetic-proof-marker')=>({id,title,parentId,propertyName,marker});
const pins={permissionId:'synthetic-owner',root:pin('root-test','Gym Assistant','my-drive-test','gymProjectM1','gym-project-synthetic'),test:pin('test-env','test','root-test','gymEnvironmentM1','env-test-synthetic'),proof:pin('proof-root','CONSOLIDATION PROOF','test-env'),left:pin('proof-left','left','proof-root'),right:pin('proof-right','right','proof-root'),subject:pin('proof-subject','before','proof-left')};
function setup(){
 const folder=p=>({id:p.id,title:p.title,mimeType:'application/vnd.google-apps.folder',parents:[{id:p.parentId}],etag:'"original"',ownedByMe:true,shared:false,labels:{trashed:false},properties:[{key:p.propertyName,value:p.marker,visibility:'PRIVATE'}],permissions:[{id:pins.permissionId,type:'user',role:'owner'}]});
 const data=Object.fromEntries([pins.root,pins.test,pins.proof,pins.left,pins.right,pins.subject].map(p=>[p.id,folder(p)]));
 const state={journal:null,loseAck:false,acceptStale:false,lease:true,writes:0,observed:0};
 const journal={requireCurrent:async()=>assert.ok(state.lease),load:async()=>structuredClone(state.journal),observe:async()=>{state.observed++;},transition:async(before,next)=>{assert.deepEqual(state.journal,before);state.journal=structuredClone(next);}};
 const client={request:async(path,init)=>{const url=new URL('https://www.googleapis.com'+path),id=url.pathname.split('/').at(-1);assert.ok(data[id]);
  if(!init)return {status:200,json:async()=>structuredClone(data[id])};
  assert.equal(init.method,'PATCH');assert.equal(id,pins.subject.id);const stale=init.headers['If-Match']!==data[id].etag;
  if(stale&&!state.acceptStale)return {status:412};
  state.writes++;data[id].title=JSON.parse(init.body).title;assert.equal(data[id].parents[0].id,url.searchParams.get('removeParents'));data[id].parents=[{id:url.searchParams.get('addParents')}];data[id].etag='"changed-'+state.writes+'"';
  if(state.loseAck){state.loseAck=false;throw new Error('Synthetic lost acknowledgement');}return {status:200,json:async()=>({})};}};
 return {data,state,journal,client,run:()=>runFolderMetadataProof(client,pins,journal)};
}
test('conditional rename and move rejects stale parent/name change and persists proof',async()=>{const f=setup();assert.deepEqual(await f.run(),{staleWriteRejected:true});assert.equal(f.state.writes,1);assert.equal(f.state.journal.stage,'proved');assert.equal(f.data[pins.subject.id].parents[0].id,pins.right.id);await f.run();assert.equal(f.state.writes,1);});
test('uncertain acknowledgement resumes from same subject without a second accepted write',async()=>{const f=setup();f.state.loseAck=true;await assert.rejects(f.run());assert.equal(f.state.journal.stage,'captured');await f.run();assert.equal(f.state.writes,1);assert.equal(f.state.journal.stage,'proved');});
test('bad ownership, sharing, tag or lease prevents any write',async()=>{for(const patch of [{shared:true},{ownedByMe:false},{etag:'W/"weak"'},{permissions:[]}]){const f=setup();Object.assign(f.data[pins.subject.id],patch);await assert.rejects(f.run());assert.equal(f.state.writes,0);}const f=setup();f.state.lease=false;await assert.rejects(f.run());assert.equal(f.state.writes,0);});
test('stale-write acceptance or changed source tag cannot claim success',async()=>{const f=setup();f.state.acceptStale=true;await assert.rejects(f.run());assert.notEqual(f.state.journal.stage,'proved');const g=setup();g.state.loseAck=true;await assert.rejects(g.run());g.data[pins.subject.id].title='before';g.data[pins.subject.id].parents=[{id:pins.left.id}];await assert.rejects(g.run());assert.equal(g.state.writes,1);});
