import {parseDocument,isAlias,isMap,isSeq,isScalar} from 'yaml';
import Ajv2020 from 'ajv/dist/2020.js';
import {reduce,type EngineCommand,type EngineState,type SessionStatus,type CompiledPlan} from './engine';
export const MAX_SAFE_2=Number.MAX_SAFE_INTEGER,MAX_PHASES_2=10000,MAX_ADDITIONS_2=1000;
export interface Resistance2 {weight?:{value:number;unit:string};bands:{equipmentId:string;count:number;placement:string}[];notes?:string}
export interface Phase2 {instanceId:string;ordinal:number;unitId:string;sourceStepId:string;blockId:string;occurrenceId?:string;setId?:string;phaseId?:string;repetitionIndex?:number;exerciseId?:string;variationId?:string;side?:string;exerciseName?:string;variationName?:string;name:string;role:string;startMs:number;endMs:number;baseStartMs?:number;resistance?:Resistance2;adjustmentId?:string}
export interface Policy2 {setId:string;blockId:string;occurrenceId:string;anchorInstanceId:string;resetMs:number;existingReset:boolean;core:Phase2[]}
export interface Adjustment2 {adjustmentId:string;setId:string;recordedAt:string}
export interface Plan2 {templateId:string;revisionId:string;templateName:string;originalDurationMs:number;expectedDurationMs:number;phases:Phase2[];policies:Policy2[];adjustments:Adjustment2[];planFormatVersion:'2.0.0'}
export interface Event2 {type:string;adjustedScheduledElapsedMs:number;observedRunMs:number;pauseMs:number;unobservedGapMs:number;baseScheduledElapsedMs?:number;phaseInstanceId?:string;adjustmentId?:string;setId?:string}
export interface State2 {status:SessionStatus;cursorMs:number;observedRunMs:number;pauseMs:number;unobservedGapMs:number;completedInstanceIds:string[];completionAcknowledged:boolean;events:Event2[]}
type Obj=Record<string,unknown>;
function check(condition:unknown):asserts condition {if(!condition)throw new Error('Invalid or unsupported runtime contract');}
export function safe2(n:number):number {check(Number.isSafeInteger(n)&&n>=0);return n;}
const plus2=(a:number,b:number)=>safe2(a+b);
export async function hashUtf8_2(value:string):Promise<string> {const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value));return Array.from(new Uint8Array(bytes),n=>n.toString(16).padStart(2,'0')).join('');}
export async function identity2(prefix:string,...parts:string[]) {return prefix+'_'+await hashUtf8_2(parts.map(p=>`${p.length}:${p}`).join('|'));}
const map=(x:unknown):Obj=>{check(x!==null&&typeof x==='object'&&!Array.isArray(x));return x as Obj;};
const list=(x:unknown):unknown[]=>{check(Array.isArray(x));return x;};
const str=(x:unknown):string=>{check(typeof x==='string');return x;};
const label=(x:unknown):string|undefined=>{if(x===undefined)return undefined;const names=map(x);return str(names.en??Object.values(names)[0]);};
const ambiguous=(x:string)=>['.nan','.inf','+.inf','-.inf','yes','no','on','off'].includes(x.toLowerCase())||/^\d{4}-\d{2}-\d{2}([Tt ].*)?$/.test(x)||/^[+-]?0[xobXOB]/.test(x)||/^[+-]?[0-9][0-9_]*_[0-9_]+$/.test(x);
export function readYaml2(source:string):Obj {
  try {
    check(new TextEncoder().encode(source).length<=1048576);
    const doc=parseDocument(source,{schema:'core',uniqueKeys:true,version:'1.2'});check(doc.errors.length===0);
    let nodes=0;
    function visit(node:unknown,depth=0) {
      check(++nodes<=100000&&depth<=64);if(node==null)return;check(!isAlias(node));
      const info=node as {anchor?:unknown;tag?:unknown};check(!info.anchor&&!info.tag);
      if(isScalar(node)) {if(node.type==='PLAIN'&&node.source)check(!ambiguous(node.source));return;}
      if(isMap(node))for(const pair of node.items){visit(pair.key,depth+1);visit(pair.value,depth+1);}
      if(isSeq(node))for(const item of node.items)visit(item,depth+1);
    }
    visit(doc.contents);
    const value=doc.toJS({maxAliasCount:0});
    function finite(x:unknown,depth=0) {check(depth<=64);if(typeof x==='number')check(Number.isFinite(x)&&Math.abs(x)<=MAX_SAFE_2);else if(Array.isArray(x))x.forEach(v=>finite(v,depth+1));else if(x&&typeof x==='object'){check(!(x instanceof Date)&&!(x instanceof Map)&&!(x instanceof Set));Object.values(x).forEach(v=>finite(v,depth+1));}else check(x==null||['string','boolean'].includes(typeof x));}
    finite(value);return map(value);
  }catch{throw new Error('YAML is invalid, unsafe or unsupported');}
}
export interface ImportContracts2 {templates:Record<string,Obj>;exercise:Obj}
const compiled=new WeakMap<ImportContracts2,{templates:Record<string,ReturnType<Ajv2020['compile']>>;exercise:ReturnType<Ajv2020['compile']>}>();
function validators(contracts:ImportContracts2) {
  let result=compiled.get(contracts);if(result)return result;
  // These switches relax schema lint only; declared types/required fields are still enforced.
  const ajv=new Ajv2020({strict:true,strictTypes:false,strictRequired:false,coerceTypes:false,useDefaults:false,removeAdditional:false});
  result={templates:Object.fromEntries(Object.entries(contracts.templates).map(([v,s])=>[v,ajv.compile(s)])),exercise:ajv.compile(contracts.exercise)};compiled.set(contracts,result);return result;
}
export async function compileTemplate2(source:string,exerciseYamls:string[],contracts:ImportContracts2):Promise<Plan2> {
  try {
    check(exerciseYamls.length>=1&&exerciseYamls.length<=32&&[source,...exerciseYamls].reduce((sum,s)=>sum+new TextEncoder().encode(s).length,0)<=8388608);
    const root=readYaml2(source),version=str(root.schemaVersion),validation=validators(contracts);check(validation.templates[version]?.(root));
    const families=new Map<string,Obj>();
    for(const yaml of exerciseYamls){const family=readYaml2(yaml);check(validation.exercise(family));const id=str(family.familyId);check(!families.has(id));families.set(id,family);const ids=list(family.variations).map(v=>str(map(v).variationId));check(new Set(ids).size===ids.length);}
    const templateId=str(root.templateId),revisionId=str(root.revisionId),phases:Phase2[]=[],policies:Policy2[]=[],identities=new Set<string>();let cursor=0,budget=65536;
    const claim=(id:string)=>{check(!identities.has(id));identities.add(id);};
    const append=(p:Phase2)=>{check(phases.length<MAX_PHASES_2&&p.endMs>p.startMs);budget+=new TextEncoder().encode(JSON.stringify(p)).length*3+1024;check(budget<=8388608);phases.push(p);cursor=p.endMs;};
    type Def={set:Obj;occ:Obj;family:Obj;variation:Obj;resistance?:Resistance2};
    for(const rawSection of list(root.sections)) {
      const section=map(rawSection);claim(str(section.sectionId));
      for(const rawBlock of list(section.blocks)) {
        const block=map(rawBlock),blockId=str(block.blockId);claim(blockId);const sets=new Map<string,Def>(),repPhases=new Map<string,Phase2[]>();
        for(const rawOcc of list(block.occurrences)) {
          const occ=map(rawOcc);claim(str(occ.occurrenceId));const family=families.get(str(occ.exerciseId));check(family);
          for(const rawSet of list(occ.sets)) {
            const set=map(rawSet),sid=str(set.setId);claim(sid);const variation=list(family.variations).map(map).filter(v=>v.variationId===set.variationId);check(variation.length===1&&set.side!=='alternating');
            const defs=list(set.phases).map(map);check(new Set(defs.map(p=>p.phaseId)).size===defs.length);check(defs.every(p=>p.mode==='timed'&&(p.transition==null||p.transition==='immediate')));check(safe2(Number(set.repetitions))>=1&&Number(set.repetitions)<=MAX_PHASES_2);
            let resistance:Resistance2|undefined;
            if(set.resistance!==undefined){const r=map(set.resistance);const weight=r.weight===undefined?undefined:map(r.weight);resistance={weight:weight?{value:Number(weight.value),unit:str(weight.unit)}:undefined,bands:list(r.bands??[]).map(raw=>{const b=map(raw),count=safe2(Number(b.count));check(count>=1&&count<=2147483647);return {equipmentId:str(b.equipmentId),count,placement:str(b.placement)};}),notes:r.notes===undefined?undefined:str(r.notes)};}
            sets.set(sid,{set,occ,family,variation:variation[0],resistance});
          }
        }
        const order=list(block.order).map(map),coverage=new Set<string>();
        for(let index=0;index<order.length;index++) {
          const step=order[index],stepId=str(step.stepId);claim(stepId);
          if(step.kind==='rest') {
            const duration=safe2(Number(step.durationMs));check(duration>0);const role=step.role===undefined?'unclassified':str(step.role),resetFor=step.resetForStepId,prior=order[index-1];let owner:Def|undefined;
            if(role==='reset'){check(typeof resetFor==='string'&&prior?.kind==='repetition'&&prior.stepId===resetFor);owner=sets.get(str(prior.setId));check(owner);}else check(resetFor===undefined);
            const unit=owner?repPhases.get(str(resetFor))?.[0].unitId:await identity2('unit',templateId,revisionId,stepId);check(unit);
            append({instanceId:await identity2('phase',templateId,revisionId,stepId),ordinal:phases.length,unitId:unit,sourceStepId:stepId,blockId,
              occurrenceId:owner?str(owner.occ.occurrenceId):undefined,setId:owner?str(owner.set.setId):undefined,side:owner?.set.side as string|undefined,
              exerciseId:owner?str(owner.occ.exerciseId):undefined,variationId:owner?str(owner.set.variationId):undefined,repetitionIndex:owner?Number(prior.repetitionIndex):undefined,
              exerciseName:owner?(label(owner.occ.namesOverride)??label(owner.family.names)):undefined,variationName:owner?label(owner.variation.names):undefined,resistance:owner?.resistance,
              name:label(step.label)??(role==='reset'?'Reset':'Rest'),role,startMs:cursor,endMs:plus2(cursor,duration),baseStartMs:cursor});
          } else {
            const sid=str(step.setId),d=sets.get(sid);check(d);const rep=safe2(Number(step.repetitionIndex)),key=JSON.stringify([sid,rep]);check(rep>=1&&rep<=Number(d.set.repetitions)&&!coverage.has(key));coverage.add(key);
            const unitId=await identity2('unit',templateId,revisionId,stepId),parts:Phase2[]=[];
            for(const rawPhase of list(d.set.phases)) {
              const phase=map(rawPhase),duration=safe2(Number(phase.durationMs));check(duration>0);
              const p:Phase2={instanceId:await identity2('phase',templateId,revisionId,stepId,str(phase.phaseId)),ordinal:phases.length,unitId,sourceStepId:stepId,blockId,
                occurrenceId:str(d.occ.occurrenceId),setId:sid,phaseId:str(phase.phaseId),repetitionIndex:rep,exerciseId:str(d.occ.exerciseId),variationId:str(d.set.variationId),side:d.set.side as string|undefined,
                exerciseName:label(d.occ.namesOverride)??label(d.family.names),variationName:label(d.variation.names),name:str(phase.name),role:phase.role===undefined?'unclassified':str(phase.role),startMs:cursor,endMs:plus2(cursor,duration),baseStartMs:cursor,resistance:d.resistance};append(p);parts.push(p);
            }repPhases.set(stepId,parts);
          }
        }
        const expected=new Set<string>();for(const [sid,d]of sets)for(let n=1;n<=Number(d.set.repetitions);n++)expected.add(JSON.stringify([sid,n]));check(expected.size===coverage.size&&[...expected].every(k=>coverage.has(k)));
        for(const [sid,d]of sets) {
          if(d.set.inSessionRepetition===undefined)continue;const policy=map(d.set.inSessionRepetition);check(version==='1.1.0'&&policy.mode==='appendToCurrentSet');const resetMs=safe2(Number(policy.resetMs));check(list(d.set.phases).every(p=>map(p).role==='work'));
          const indices=order.map((s,i)=>s.kind==='repetition'&&s.setId===sid?i:-1).filter(i=>i>=0);check(indices.every((i,n)=>Number(order[i].repetitionIndex)===n+1));
          for(let n=0;n<indices.length-1;n++){const middle=order.slice(indices[n]+1,indices[n+1]);check(resetMs===0?middle.length===0:middle.length===1&&middle[0].role==='reset'&&middle[0].resetForStepId===order[indices[n]].stepId&&Number(middle[0].durationMs)===resetMs);}
          const last=order[indices[indices.length-1]],next=order[indices[indices.length-1]+1],existingReset=next?.role==='reset'&&next.resetForStepId===last.stepId;if(existingReset)check(Number(next.durationMs)===resetMs);
          const core=repPhases.get(str(last.stepId));check(core);const anchor=existingReset?phases.find(p=>p.sourceStepId===next.stepId):core[core.length-1];check(anchor);
          policies.push({setId:sid,blockId,occurrenceId:str(d.occ.occurrenceId),anchorInstanceId:anchor.instanceId,resetMs,existingReset,core});
        }
      }
    }
    check(phases.length>0);const plan:Plan2={templateId,revisionId,templateName:label(root.names)??'Training Template',originalDurationMs:cursor,expectedDurationMs:cursor,phases,policies,adjustments:[],planFormatVersion:'2.0.0'};requirePlanBudget2(plan);return plan;
  }catch{throw new Error('Template or exercise contract is invalid or unsupported');}
}
export function requirePlanBudget2(plan:Plan2) {
  const bytes=(value:string)=>new TextEncoder().encode(value).length;
  check(bytes(plan.templateName)<=4096);
  check(65536+plan.adjustments.length*512+plan.phases.reduce((sum,p)=>sum+bytes(JSON.stringify(p))*3+1024,0)<=8388608);
}
export const initialState2=():State2=>({status:'loaded',cursorMs:0,observedRunMs:0,pauseMs:0,unobservedGapMs:0,completedInstanceIds:[],completionAcknowledged:false,events:[]});
export const activePhase2=(p:Plan2,cursor:number)=>p.phases.find(x=>cursor>=x.startMs&&cursor<x.endMs);
export function baseCursor2(p:Plan2,cursor:number):number|undefined {if(cursor===p.expectedDurationMs)return p.originalDurationMs;const phase=activePhase2(p,cursor);return phase?.baseStartMs===undefined?undefined:plus2(phase.baseStartMs,cursor-phase.startMs);}
export function reduce2(p:Plan2,s:State2,c:EngineCommand):State2 {
  check(s.cursorMs>=0&&s.cursorMs<=p.expectedDurationMs);safe2(s.observedRunMs);safe2(s.pauseMs);safe2(s.unobservedGapMs);
  if(c.op==='advance'){safe2(c.ms);if(s.status==='running')plus2(s.observedRunMs,Math.min(c.ms,p.expectedDurationMs-s.cursorMs));if(s.status==='paused')plus2(s.pauseMs,c.ms);}if(c.op==='noteUnobserved')plus2(s.unobservedGapMs,safe2(c.ms));
  const indices=new Map(p.phases.map((phase,i)=>[phase.instanceId,i]));check(s.completedInstanceIds.every(id=>indices.has(id)));
  const legacyPlan:CompiledPlan={schemaVersion:'2.0.0',templateId:p.templateId,revisionId:p.revisionId,planFormatVersion:'2.0.0',expectedDurationMs:p.expectedDurationMs,phases:p.phases.map((phase,i)=>({index:i,stepId:phase.sourceStepId,name:phase.name,startMs:phase.startMs,endMs:phase.endMs,resistanceLabel:''}))};
  const previous:EngineState={status:s.status,scheduledElapsedMs:s.cursorMs,observedRunMs:s.observedRunMs,pauseMs:s.pauseMs,unobservedGapMs:s.unobservedGapMs,completedPhaseIndexes:s.completedInstanceIds.map(id=>indices.get(id)!),completionAcknowledged:s.completionAcknowledged,events:[]};
  const next=reduce(legacyPlan,previous,c),events:Event2[]=next.events.map(e=>{const phase=e.phaseIndex===null?undefined:p.phases[e.phaseIndex];return {type:e.type,adjustedScheduledElapsedMs:e.scheduledElapsedMs,observedRunMs:e.observedRunMs,pauseMs:e.pauseMs,unobservedGapMs:e.unobservedGapMs,baseScheduledElapsedMs:e.type==='phaseCompleted'?(phase?.baseStartMs===undefined?undefined:plus2(phase.baseStartMs,phase.endMs-phase.startMs)):baseCursor2(p,e.scheduledElapsedMs),phaseInstanceId:phase?.instanceId};});
  return {status:next.status,cursorMs:next.scheduledElapsedMs,observedRunMs:next.observedRunMs,pauseMs:next.pauseMs,unobservedGapMs:next.unobservedGapMs,completedInstanceIds:next.completedPhaseIndexes.map(i=>p.phases[i].instanceId),completionAcknowledged:next.completionAcknowledged,events:[...s.events,...events]};
}
export function additionAvailable2(p:Plan2,s:State2):Policy2|undefined {if(!['running','paused'].includes(s.status))return;const active=activePhase2(p,s.cursorMs);if(!active||!['work','reset','prepare'].includes(active.role))return;const policy=p.policies.find(x=>x.setId===active.setId);if(!policy)return;const tail=p.phases.filter(x=>x.setId===policy.setId&&x.adjustmentId!==undefined).slice(-1)[0]??p.phases.find(x=>x.instanceId===policy.anchorInstanceId);return tail&&s.cursorMs<tail.endMs?policy:undefined;}
export async function appendRepetition2(p:Plan2,s:State2,a:Adjustment2):Promise<{plan:Plan2;state:State2}> {
  check(/^[A-Za-z0-9][A-Za-z0-9_-]{2,127}$/.test(a.adjustmentId)&&/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?Z$/.test(a.recordedAt)&&Number.isFinite(Date.parse(a.recordedAt)));check(new Date(a.recordedAt).toISOString().slice(0,19)===a.recordedAt.slice(0,19));
  const prior=p.adjustments.find(x=>x.adjustmentId===a.adjustmentId);if(prior){check(prior.setId===a.setId&&prior.recordedAt===a.recordedAt);return {plan:p,state:s};}
  check(p.adjustments.length<MAX_ADDITIONS_2);const policy=additionAvailable2(p,s);check(policy&&policy.setId===a.setId);
  const tail=p.phases.filter(x=>x.setId===policy.setId&&x.adjustmentId!==undefined).slice(-1)[0]??p.phases.find(x=>x.instanceId===policy.anchorInstanceId);check(tail);
  const insertion=p.phases.indexOf(tail)+1,unitId=await identity2('unit','added',a.adjustmentId),rep=policy.core[0].repetitionIndex!+p.adjustments.filter(x=>x.setId===policy.setId).length+1;let ordinal=Math.max(...p.phases.map(x=>x.ordinal))+1;
  const core=async()=>{const result:Phase2[]=[];for(let i=0;i<policy.core.length;i++){const nextOrdinal=ordinal++;result.push({...policy.core[i],instanceId:await identity2('phase','added',a.adjustmentId,'work',String(i)),ordinal:nextOrdinal,unitId,repetitionIndex:rep,baseStartMs:undefined,adjustmentId:a.adjustmentId});}return result;};
  const reset=async():Promise<Phase2[]>=>policy.resetMs===0?[]:[{...policy.core[0],instanceId:await identity2('phase','added',a.adjustmentId,'reset'),ordinal:ordinal++,unitId,phaseId:undefined,repetitionIndex:rep,name:'Reset',role:'reset',startMs:0,endMs:policy.resetMs,baseStartMs:undefined,adjustmentId:a.adjustmentId}];
  const extra=policy.existingReset?[...await core(),...await reset()]:[...await reset(),...await core()];check(p.phases.length+extra.length<=MAX_PHASES_2);
  let cursor=0;const phases=[...p.phases.slice(0,insertion),...extra,...p.phases.slice(insertion)].map(x=>{const startMs=cursor;cursor=plus2(cursor,x.endMs-x.startMs);return {...x,startMs,endMs:cursor};});
  check(new Set(phases.map(x=>x.instanceId)).size===phases.length&&new Set(phases.map(x=>x.ordinal)).size===phases.length);
  const plan={...p,expectedDurationMs:cursor,phases,adjustments:[...p.adjustments,a]};requirePlanBudget2(plan);check(activePhase2(p,s.cursorMs)?.instanceId===activePhase2(plan,s.cursorMs)?.instanceId);
  const event:Event2={type:'repetitionAdded',adjustedScheduledElapsedMs:s.cursorMs,observedRunMs:s.observedRunMs,pauseMs:s.pauseMs,unobservedGapMs:s.unobservedGapMs,baseScheduledElapsedMs:baseCursor2(plan,s.cursorMs),adjustmentId:a.adjustmentId,setId:a.setId};return {plan,state:{...s,events:[...s.events,event]}};
}
