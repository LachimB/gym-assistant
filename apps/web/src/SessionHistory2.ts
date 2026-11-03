import template10 from '../../../schemas/template.v1.schema.json?raw';
import template11 from '../../../schemas/template.v1.1.schema.json?raw';
import exercise from '../../../schemas/exercise.v1.schema.json?raw';
import planSchema from '../../../schemas/compiled-plan.v2.schema.json?raw';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import {compileTemplate2,appendRepetition2,baseCursor2,hashUtf8_2,identity2,type Plan2,type Adjustment2,type State2} from '../../../packages/engine-ts/src/runtime2';
import type {HumanHistory,ReadHistory,HistoryPhase} from './SessionHistory';

const contracts={templates:{'1.0.0':JSON.parse(template10),'1.1.0':JSON.parse(template11)},exercise:JSON.parse(exercise)};
const planAjv=new Ajv2020({strict:true,strictRequired:false,strictTypes:false});addFormats(planAjv);
const validatePlan=planAjv.compile(JSON.parse(planSchema));
function fact(value:unknown):asserts value {if(!value)throw new Error('The frozen source, adjusted plan or recorded events are inconsistent. The file has not been changed.');}
const safe=(n:unknown):n is number=>Number.isSafeInteger(n)&&(n as number)>=0;
const normalized=(value:unknown):string=>JSON.stringify(value,(_key,item)=>item&&typeof item==='object'&&!Array.isArray(item)?Object.fromEntries(Object.entries(item).sort(([a],[b])=>a.localeCompare(b))):item);
export async function readRuntimeHistory(record:HumanHistory):Promise<ReadHistory> {
  fact(globalThis.crypto?.subtle);
  const source=record.sourcePlanSnapshot!;
  fact(record.engineVersion==='kotlin-timed-2'&&source.hashFormat==='sha256-utf8');
  fact(await hashUtf8_2(source.sourceSnapshotJson!)===source.sourceHash && await hashUtf8_2(source.compiledPlanJson!)===source.compiledPlanHash);
  const input=JSON.parse(source.sourceSnapshotJson!) as {templateYaml:string;exerciseYamls:string[]};
  fact(input&&Object.keys(input).sort().join(',')==='exerciseYamls,templateYaml'&&typeof input.templateYaml==='string'&&Array.isArray(input.exerciseYamls)&&input.exerciseYamls.every(s=>typeof s==='string'));
  let plan=JSON.parse(source.compiledPlanJson!) as Plan2;
  fact(validatePlan(plan));
  const independentlyCompiled=await compileTemplate2(input.templateYaml,input.exerciseYamls,contracts);
  fact(normalized(plan)===normalized(independentlyCompiled)&&plan.adjustments.length===0);
  fact(plan.templateId===record.templateId&&plan.revisionId===record.revisionId);
  const effective=record.effectivePlanSnapshot!;
  fact(await hashUtf8_2(effective.compiledPlanJson)===effective.compiledPlanHash);
  const finalPlan=JSON.parse(effective.compiledPlanJson) as Plan2;fact(validatePlan(finalPlan));
  const timing=record.timing!;
  for(const [key,n] of Object.entries(timing))fact(n===null&&key==='scheduledElapsedMs'||safe(n));
  const counters=['observedRunMs','pauseMs','unobservedGapMs','unknownGapCount'] as const;
  const previous:Record<string,number>=Object.fromEntries(counters.map(k=>[k,0]));
  let added=0;const adjustments=record.adjustments!;const counts:Record<string,number>={};
  for(const [index,event] of record.events.entries()) {
    fact(event.eventId===await identity2('event',record.sessionId,'history-2',String(index)));
    counts[event.type]=(counts[event.type]??0)+1;
    fact(safe(event.adjustedScheduledElapsedMs)&&event.adjustedScheduledElapsedMs<=plan.expectedDurationMs);
    for(const key of counters) {const n=event[key];fact(safe(n)&&n>=previous[key]&&n<=timing[key]!);previous[key]=n;}
    if(event.type==='repetitionAdded') {
      const adjustment=adjustments[added++];fact(adjustment&&adjustment.adjustmentId===event.adjustmentId&&adjustment.setId===event.setId&&adjustment.recordedAt===event.recordedAt);
      const state:State2={status:'running',cursorMs:event.adjustedScheduledElapsedMs,observedRunMs:event.observedRunMs!,pauseMs:event.pauseMs!,unobservedGapMs:event.unobservedGapMs!,completedInstanceIds:[],completionAcknowledged:false,events:[]};
      plan=(await appendRepetition2(plan,state,adjustment)).plan;
    }
    let base=baseCursor2(plan,event.adjustedScheduledElapsedMs);
    if(event.phaseInstanceId) {
      const phase=plan.phases.find(p=>p.instanceId===event.phaseInstanceId);fact(phase);
      fact((event.type==='phaseCompleted'?phase.endMs:phase.startMs)===event.adjustedScheduledElapsedMs);
      if(event.type==='phaseCompleted')base=phase.baseStartMs===undefined?undefined:phase.baseStartMs+phase.endMs-phase.startMs;
    }
    fact((base??null)===event.scheduledElapsedMs);
    if(event.type==='planEndReached')fact(event.adjustedScheduledElapsedMs===plan.expectedDurationMs);
  }
  fact(added===adjustments.length&&normalized(plan)===normalized(finalPlan)&&normalized(plan.adjustments)===normalized(adjustments));
  fact(timing.plannedDurationMs===plan.originalDurationMs&&timing.adjustedPlannedDurationMs===plan.expectedDurationMs);
  fact(safe(timing.adjustedScheduledElapsedMs)&&timing.adjustedScheduledElapsedMs<=plan.expectedDurationMs&&(baseCursor2(plan,timing.adjustedScheduledElapsedMs)??null)===timing.scheduledElapsedMs);
  const parts=new Intl.DateTimeFormat('en',{timeZone:record.timeZone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(record.startedAt));
  const part=(key:string)=>parts.find(p=>p.type===key)?.value;
  fact(`${part('year')}-${part('month')}-${part('day')}`===record.localTrainingDate);
  fact(record.events[0]?.type==='sessionStarted'&&counts.sessionStarted===1);
  const ends=(counts.sessionEnded??0)+(counts.sessionDiscarded??0),last=record.events.at(-1)!;
  if(record.completionKind==='unfinished')fact(ends===0&&(record.status!=='awaitingCompletion'||counts.planEndReached));
  else {
    fact(ends===1&&last.type===(record.completionKind==='discarded'?'sessionDiscarded':'sessionEnded')&&last.detail?.completionKind===record.completionKind&&last.recordedAt===record.finishedAt);
    for(const key of ['adjustedScheduledElapsedMs','scheduledElapsedMs',...counters] as const)fact(last[key]===timing[key]);
    fact(record.completionKind!=='plannedEnd'||counts.planEndReached);
  }
  const phases:HistoryPhase[]=plan.phases.map((p,index)=>({...p,index,stepId:p.sourceStepId}));
  const labels:Record<string,string>=Object.create(null);
  for(const phase of plan.phases) {
    if(phase.exerciseName)labels[`family/${phase.exerciseId}`]=phase.exerciseName;
    if(phase.variationName)labels[`variation/${phase.exerciseId}/${phase.variationId}`]=phase.variationName;
  }
  return {record,phases,title:plan.templateName,labels};
}
