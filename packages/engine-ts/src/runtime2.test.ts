import {describe,it,expect,beforeAll} from 'vitest';
import {readFileSync} from 'node:fs';
import {compileTemplate2,readYaml2,initialState2,reduce2,appendRepetition2,activePhase2,additionAvailable2,baseCursor2,MAX_SAFE_2,type Plan2,type ImportContracts2} from './runtime2';
const read=(path:string)=>readFileSync(new URL('../../../'+path,import.meta.url),'utf8');
const source=read('examples/templates/timed-runtime-v2.yaml'),exercises=[read('examples/exercises/bridge-band-family.yaml')];
const contracts:ImportContracts2={templates:{'1.0.0':JSON.parse(read('schemas/template.v1.schema.json')),'1.1.0':JSON.parse(read('schemas/template.v1.1.schema.json'))},exercise:JSON.parse(read('schemas/exercise.v1.schema.json'))};
const clean=<T>(x:T):T=>JSON.parse(JSON.stringify(x));
const extra=(id='addition_left_1',setId='set_left')=>({adjustmentId:id,setId,recordedAt:'2026-11-03T10:00:00Z'});
let plan:Plan2;
beforeAll(async()=>{plan=await compileTemplate2(source,exercises,contracts);});
const running=(p:Plan2,ms:number)=>reduce2(p,reduce2(p,initialState2(),{op:'start'}),{op:'advance',ms});
describe('version2 timed execution',()=>{
  it('matches the independent Kotlin-produced frozen plan including names, sides, weight and stable IDs',()=>{
    expect(clean(plan)).toEqual(JSON.parse(read('examples/expected/timed-runtime-v2.plan.json')));
    expect(plan.expectedDurationMs).toBe(80000);expect(plan.phases[0].side).toBe('left');expect(plan.phases[0].resistance?.weight?.value).toBe(12.5);
  });
  it('inserts only work/reset before the following hold and preserves the active timer and completed identities',async()=>{
    const s=running(plan,7000),before=clean(plan),change=await appendRepetition2(plan,s,extra());
    expect(change.plan.expectedDurationMs).toBe(90000);expect(change.state.cursorMs).toBe(s.cursorMs);expect(activePhase2(change.plan,s.cursorMs)).toEqual(activePhase2(plan,s.cursorMs));
    expect(change.plan.phases.filter(p=>p.adjustmentId).map(p=>p.role)).toEqual(['work','reset']);
    expect(change.plan.phases.find(p=>p.setId==='set_long_hold')?.startMs).toBe(30000);expect(change.plan.phases.filter(p=>p.setId==='set_long_hold')).toHaveLength(1);
    for(const original of plan.phases){const changed=change.plan.phases.find(p=>p.instanceId===original.instanceId)!;expect(changed.ordinal).toBe(original.ordinal);expect(changed.baseStartMs).toBe(original.baseStartMs);}
    expect(clean(plan)).toEqual(before);expect(change.state.completedInstanceIds).toEqual(s.completedInstanceIds);
  });
  it('inserts reset/work ahead of a long rest without duplicating that rest',async()=>{
    const change=await appendRepetition2(plan,running(plan,45000),extra('addition_right_1','set_right'));
    expect(change.plan.phases.filter(p=>p.adjustmentId).map(p=>p.role)).toEqual(['reset','work']);expect(change.plan.expectedDurationMs).toBe(90000);
    expect(change.plan.phases.filter(p=>p.sourceStepId==='step_final_rest')).toHaveLength(1);expect(change.plan.phases.slice(-1)[0].endMs-change.plan.phases.slice(-1)[0].startMs).toBe(30000);
  });
  it('orders multiple additions and idempotently reuses an accepted domain action',async()=>{
    const one=await appendRepetition2(plan,running(plan,1000),extra()),two=await appendRepetition2(one.plan,one.state,extra('addition_left_2'));
    expect(two.plan.expectedDurationMs).toBe(100000);expect(two.plan.phases.filter(p=>p.adjustmentId&&p.role==='work').map(p=>p.repetitionIndex)).toEqual([3,4]);
    expect(await appendRepetition2(two.plan,two.state,extra())).toEqual(two);
    await expect(appendRepetition2(two.plan,two.state,{...extra(),recordedAt:'2026-11-03T11:00:00Z'})).rejects.toThrow();
    for(const recordedAt of ['2026-11-03','2026-02-30T10:00:00Z'])await expect(appendRepetition2(plan,running(plan,1000),{...extra('invalid_date'),recordedAt})).rejects.toThrow();
  });
  it('keeps pause, gap, skipped work and original-plan position separate',async()=>{
    const s=reduce2(plan,running(plan,3000),{op:'pause'}),change=await appendRepetition2(plan,s,extra());
    const paused=reduce2(change.plan,change.state,{op:'advance',ms:1200});expect(paused.pauseMs).toBe(1200);expect(paused.observedRunMs).toBe(3000);expect(paused.status).toBe('paused');
    const resumed=reduce2(change.plan,paused,{op:'resume'}),inside=reduce2(change.plan,resumed,{op:'advance',ms:19000});expect(baseCursor2(change.plan,inside.cursorMs)).toBeUndefined();
    const skipped=reduce2(change.plan,inside,{op:'stepForward'});expect(skipped.observedRunMs).toBe(inside.observedRunMs);expect(skipped.completedInstanceIds).not.toContain(activePhase2(change.plan,inside.cursorMs)?.instanceId);
    const recovered=reduce2(change.plan,skipped,{op:'noteUnobserved',ms:900});expect(recovered.unobservedGapMs).toBe(900);expect(additionAvailable2(change.plan,recovered)).toBeUndefined();
  });
  it('refuses the wrong set/side, an ended set and ambiguous source reset links',async()=>{
    await expect(appendRepetition2(plan,running(plan,1000),extra('wrong_side','set_right'))).rejects.toThrow();expect(additionAvailable2(plan,running(plan,22000))).toBeUndefined();expect(additionAvailable2(plan,running(plan,51000))).toBeUndefined();
    for(const text of [source.replace('resetForStepId: step_left_1','resetForStepId: step_right_1'),source.replaceAll('resetMs: 4000','resetMs: 3000'),source.replaceAll('side: left','side: alternating')])await expect(compileTemplate2(text,exercises,contracts)).rejects.toThrow();
  });
  it('rejects unsafe source types/aliases/tags/duplicates and never reports source contents',async()=>{
    for(const text of ['a: 1\na: 2','a: &a [*a]','a: !!str 1','---\na: 1\n---\nb: 2','['.repeat(70)+']'.repeat(70),'a: .nan','a: 2026-11-03','a: yes'])expect(()=>readYaml2(text)).toThrow();
    for(const text of [source.replaceAll('repetitions: 2',"repetitions: '2'"),source.replaceAll('durationMs: 6000','durationMs: -1')])await expect(compileTemplate2(text,exercises,contracts)).rejects.toThrow();
    await expect(compileTemplate2('private_source_marker: [',exercises,contracts)).rejects.not.toThrow(/private_source_marker/);
  });
  it('rejects counter overflow and negative time',()=>{
    const s={...running(plan,1),observedRunMs:MAX_SAFE_2};expect(()=>reduce2(plan,s,{op:'advance',ms:1})).toThrow();expect(()=>reduce2(plan,running(plan,1),{op:'advance',ms:-1})).toThrow();
  });
  it('matches every Kotlin golden boundary including domain IDs, rewind and recovery',async()=> {
    let p=plan,state=initialState2();const trace:unknown[]=[];
    const capture=(label:string)=>trace.push({label,status:state.status,cursorMs:state.cursorMs,observedRunMs:state.observedRunMs,pauseMs:state.pauseMs,unobservedGapMs:state.unobservedGapMs,
      baseCursorMs:baseCursor2(p,state.cursorMs)??null,completedInstanceIds:[...state.completedInstanceIds].sort(),events:state.events,plan:p});
    const send=(label:string,cmd:Parameters<typeof reduce2>[2])=>{state=reduce2(p,state,cmd);capture(label)};
    const add=async(id:string)=>{const change=await appendRepetition2(p,state,extra(id));p=change.plan;state=change.state;capture(id)};
    send('start',{op:'start'});send('tick',{op:'advance',ms:7000});await add('addition_left_1');await add('addition_left_2');
    send('pause',{op:'pause'});send('paused',{op:'advance',ms:1200});send('resume',{op:'resume'});
    send('into_extra',{op:'advance',ms:16000});send('skip',{op:'stepForward'});send('rewind',{op:'stepBackward'});
    send('gap',{op:'noteUnobserved',ms:900});send('explicit_resume',{op:'resume'});send('finish_time',{op:'advance',ms:200000});send('acknowledge',{op:'acknowledge'});
    expect(clean(trace)).toEqual(JSON.parse(read('examples/expected/timed-runtime-v2.trace.json')));
  });

});
