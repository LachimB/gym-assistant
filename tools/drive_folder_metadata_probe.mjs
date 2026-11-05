// Bounded synthetic folder-metadata proof; never accepts or reads document content.
// Caller verifies the approved account/plan, uses a fixed-origin bounded transport, and persists the journal before writes.
const fail=()=>{throw new Error('Folder proof stopped; no existing area may move.');};
const strong=t=>typeof t==='string'&&/^"[\x21\x23-\x7e]+"$/.test(t);
const id=v=>{if(typeof v!=='string'||! /^[A-Za-z0-9_-]{1,128}$/.test(v))fail();};
export const FIELDS='id,title,mimeType,parents(id),etag,ownedByMe,shared,labels(trashed),properties(key,value,visibility),permissions(id,type,role,deleted)';
function checkFolder(data,pin,permission,title=pin.title,parent=pin.parentId){
  if(data?.id!==pin.id||data.title!==title||data.mimeType!=='application/vnd.google-apps.folder'||!strong(data.etag)||data.ownedByMe!==true||data.shared!==false||data.labels?.trashed!==false||
    !Array.isArray(data.parents)||data.parents.length!==1||data.parents[0]?.id!==parent)fail();
  const props=data.properties?.filter(p=>p.key===pin.propertyName&&p.visibility==='PRIVATE');
  if(!props||props.length!==1||props[0].value!==pin.marker||!Array.isArray(data.permissions)||data.permissions.length!==1)fail();
  const owner=data.permissions[0];if(owner.id!==permission||owner.type!=='user'||owner.role!=='owner'||owner.deleted!==undefined&&owner.deleted!==false)fail();
  return data;
}
export async function runFolderMetadataProof(client,pins,journal){
  const {root,test,proof,left,right,subject,permissionId}=pins;
  for(const pin of [root,test,proof,left,right,subject]){id(pin.id);if(typeof pin.propertyName!=='string'||typeof pin.marker!=='string')fail();}
  id(permissionId);if(new Set([root,test,proof,left,right,subject].map(p=>p.id)).size!==6)fail();
  if(root.title!=='Gym Assistant'||test.title!=='test'||test.parentId!==root.id||proof.title!=='CONSOLIDATION PROOF'||proof.parentId!==test.id||
    left.title!=='left'||right.title!=='right'||left.parentId!==proof.id||right.parentId!==proof.id||subject.title!=='before'||subject.parentId!==left.id)fail();
  const pinJSON=JSON.stringify(pins);
  const call=async(path,init,code=200)=>{await journal.requireCurrent();const response=await client.request(path,init);if(response.status!==code)fail();return response;};
  const path=`/drive/v2/files/${subject.id}`;
  const read=async(pin)=>{
    const response=await call(`/drive/v2/files/${pin.id}?fields=${FIELDS}`);
    const data=await response.json();await journal.observe(pin.id,data);return data;
  };
  for(const pin of [root,test,proof,left,right])checkFolder(await read(pin),pin,permissionId);
  let captured=await journal.load();let current=await read(subject);
  if(captured===null){
    checkFolder(current,subject,permissionId);
    const next={stage:'captured',pinJSON,source:current};await journal.transition(null,next);captured=next;
  }
  if(captured.pinJSON!==pinJSON||!['captured','currentVerified','proved'].includes(captured.stage))fail();
  checkFolder(captured.source,subject,permissionId);
  const final=()=>checkFolder(current,subject,permissionId,'verified',right.id);
  if(current.title===subject.title&&current.parents?.[0]?.id===subject.parentId){
    if(captured.stage!=='captured'||current.etag!==captured.source.etag)fail();checkFolder(current,subject,permissionId);
    const query=new URLSearchParams({addParents:right.id,removeParents:left.id,fields:'id,etag'});
    await call(path+'?'+query,{method:'PATCH',headers:{'Content-Type':'application/json','If-Match':captured.source.etag},body:JSON.stringify({title:'verified'})});
    current=await read(subject);
  }
  final();if(current.etag===captured.source.etag)fail();
  if(captured.stage==='captured'){
    const next={...captured,stage:'currentVerified',verifiedTag:current.etag};await journal.transition(captured,next);captured=next;
  }else if(current.etag!==captured.verifiedTag)fail();
  if(captured.stage==='proved')return {staleWriteRejected:true};
  const query=new URLSearchParams({addParents:left.id,removeParents:right.id,fields:'id,etag'});
  await call(path+'?'+query,{method:'PATCH',headers:{'Content-Type':'application/json','If-Match':captured.source.etag},body:JSON.stringify({title:'stale'})},412);
  current=await read(subject);final();if(current.etag!==captured.verifiedTag)fail();
  await journal.requireCurrent();await journal.transition(captured,{...captured,stage:'proved'});
  return {staleWriteRejected:true};
}
