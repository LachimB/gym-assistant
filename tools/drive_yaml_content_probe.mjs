// Synthetic-only proof of the native working-file media/MIME boundary. Not a sync adapter.
// Caller pins reviewed resources, persists the journal and reserves each fixed-origin request.
import {readStrictProjectJSON} from '../apps/web/src/ProjectDriveLayout.mjs';
export const YAML_PROOF_FIELDS='id,title,mimeType,parents(id),etag,ownedByMe,shared,labels(trashed),properties(key,value,visibility),permissions(id,type,role,deleted)';
export const YAML_PROOF_SOURCE=Object.freeze(Object.fromEntries(['before','accepted','stale'].map(revision=>[revision,
  `fixture: gym-assistant-m1-yaml-condition\ncontainsRealTrainingData: false\nrevision: ${revision}\n`])));
const fail=()=>{throw new Error('Synthetic YAML proof stopped; conditional source writes remain unproven.');};
const id=v=>typeof v==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(v);
const strong=v=>typeof v==='string'&&/^"[\x21\x23-\x7e]{1,1000}"$/.test(v);
function checkedMetadata(data,pin,permissionId,mime){
  if(data?.id!==pin.id||data.title!==pin.title||data.mimeType!==mime||!strong(data.etag)||data.ownedByMe!==true||
    data.shared!==false||data.labels?.trashed!==false||!Array.isArray(data.parents)||data.parents.length!==1||data.parents[0]?.id!==pin.parentId)fail();
  const props=data.properties?.filter(p=>p.key===pin.propertyName&&p.visibility==='PRIVATE');
  if(!props||props.length!==1||props[0].value!==pin.marker||!Array.isArray(data.permissions)||data.permissions.length!==1)fail();
  const owner=data.permissions[0];
  if(owner.id!==permissionId||owner.type!=='user'||owner.role!=='owner'||owner.deleted!==undefined&&owner.deleted!==false)fail();
  return data;
}
export async function runYamlContentProof(client,pins,journal){
  const {permissionId,root,test,proof,subject}=pins;
  if(!id(permissionId))fail();
  for(const pin of [root,test,proof,subject])if(!pin||!id(pin.id)||!id(pin.parentId)||typeof pin.title!=='string'||
    !/^gym[A-Za-z0-9]{1,64}$/.test(pin.propertyName)||!id(pin.marker))fail();
  if(new Set([root.id,test.id,proof.id,subject.id]).size!==4||[root,test,proof,subject].some(p=>p.id===p.parentId)||
    [test.id,proof.id,subject.id].includes(root.parentId)||root.title!=='Gym Assistant'||test.title!=='test'||test.parentId!==root.id||
    proof.title!=='YAML CONDITIONAL PROOF'||proof.parentId!==test.id||subject.title!=='SYNTHETIC-conditional-source.yaml'||subject.parentId!==proof.id)fail();
  // Copy pins before any await so a caller cannot retarget a live operation.
  const pinJSON=JSON.stringify(pins),saved=JSON.parse(pinJSON);
  const folders=[saved.root,saved.test,saved.proof],target=saved.subject;
  let requests=0;
  const call=async(path,init,status=200)=>{
    await journal.requireCurrent();requests++;
    const response=await client.request(path,init);await journal.requireCurrent();
    if(response.status!==status)fail();return response;
  };
  const json=async(response)=>{
    const text=await response.text();if(typeof text!=='string'||new TextEncoder().encode(text).length>32768)fail();
    try{return readStrictProjectJSON(text);}catch{fail();}
  };
  const metadata=async(pin,mime)=>{
    const data=await json(await call(`/drive/v2/files/${pin.id}?fields=${YAML_PROOF_FIELDS}`));
    checkedMetadata(data,pin,saved.permissionId,mime);await journal.requireCurrent();await journal.observe(pin.id,data);return data;
  };
  const namespace=async()=>{
    const account=await json(await call('/drive/v3/about?fields=user(permissionId)'));
    if(account?.user?.permissionId!==saved.permissionId)fail();
    for(const pin of folders)await metadata(pin,'application/vnd.google-apps.folder');
  };
  const snapshot=async()=>{
    const before=await metadata(target,'text/plain');
    const text=await (await call(`/drive/v2/files/${target.id}?alt=media`)).text();
    if(!Object.values(YAML_PROOF_SOURCE).includes(text))fail();
    const after=await metadata(target,'text/plain');if(before.etag!==after.etag)fail();
    await journal.requireCurrent();return {tag:after.etag,text};
  };
  await namespace();
  let captured=await journal.load(),current=await snapshot();
  if(captured===null){
    if(current.text!==YAML_PROOF_SOURCE.before)fail();
    const next={stage:'captured',pinJSON,source:current};await journal.requireCurrent();await journal.transition(null,next);captured=next;
  }
  if(captured.pinJSON!==pinJSON||!['captured','currentVerified','proved'].includes(captured.stage)||
    !strong(captured.source?.tag)||captured.source?.text!==YAML_PROOF_SOURCE.before)fail();
  const writePath=`/upload/drive/v2/files/${target.id}?uploadType=media&fields=id`;
  const write=text=>({method:'PUT',headers:{'Content-Type':'text/plain','If-Match':captured.source.tag},body:text});
  if(current.text===YAML_PROOF_SOURCE.before){
    if(captured.stage!=='captured'||current.tag!==captured.source.tag)fail();
    await namespace();
    const result=await json(await call(writePath,write(YAML_PROOF_SOURCE.accepted)));
    if(result?.id!==target.id)fail();
    current=await snapshot();
  }
  if(current.text!==YAML_PROOF_SOURCE.accepted||current.tag===captured.source.tag)fail();
  if(captured.stage==='captured'){
    const next={...captured,stage:'currentVerified',verifiedTag:current.tag};await journal.requireCurrent();await journal.transition(captured,next);captured=next;
  }else if(!strong(captured.verifiedTag)||current.tag!==captured.verifiedTag)fail();
  if(captured.stage!=='proved'){
    await namespace();
    await call(writePath,write(YAML_PROOF_SOURCE.stale),412);
    current=await snapshot();
    if(current.text!==YAML_PROOF_SOURCE.accepted||current.tag!==captured.verifiedTag)fail();
  }
  await namespace();await journal.requireCurrent();
  if(captured.stage!=='proved')await journal.transition(captured,{...captured,stage:'proved'});
  return {staleWriteRejected:true,mimeType:'text/plain',requests};
}
