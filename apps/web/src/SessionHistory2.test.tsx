import {it,expect,vi} from 'vitest';
import {readFileSync} from 'node:fs';
import {renderToStaticMarkup} from 'react-dom/server';
import {readHistory} from './SessionHistory';
import {HistoryDetails} from './HistoryScreen';
const fixture=()=>JSON.parse(readFileSync(new URL('../../../examples/sessions/synthetic-runtime-v2-session.json',import.meta.url),'utf8'));
it('independently recompiles and reads the Kotlin 1.2 history without network or internal receipts',async()=> {
  const fetch=vi.spyOn(globalThis,'fetch');
  try {
    const value=fixture(),result=await readHistory(JSON.stringify(value));
    expect(result.record).toEqual(value);expect(result.phases).toHaveLength(12);
    const html=renderToStaticMarkup(<HistoryDetails history={result}/>);
    for(const text of ['Adjusted planned duration','90 s','Within an added repetition','Extra repetition added','repetition 3','left','duration unknown','Ended early'])expect(html).toContain(text);
    expect(html).not.toContain('Earlier recording format');expect(html).not.toContain('private_operation');expect(fetch).not.toHaveBeenCalled();
  }finally {fetch.mockRestore()}
});
it('rejects corrupt source, effective bytes, adjustment identities and invented original-plan progress',async()=> {
  for(const change of [
    (v:any)=>{v.sourcePlanSnapshot.sourceSnapshotJson+=' '},
    (v:any)=>{v.effectivePlanSnapshot.compiledPlanJson+=' '},
    (v:any)=>{v.adjustments[0].setId='set_right'},
    (v:any)=>{v.events.find((e:any)=>e.type==='repetitionAdded').adjustmentId='unrelated_add'},
    (v:any)=>{v.timing.scheduledElapsedMs=22000},
    (v:any)=>{v.events.at(-1).observedRunMs=0},
    (v:any)=>{v.events[1].phaseInstanceId='phase_missing'},
    (v:any)=>{v.recording={actions:[]}},
    (v:any)=>{v.timing.unknownGapCount=3;v.events.at(-1).unknownGapCount=3},
  ]) {const value=fixture();change(value);await expect(readHistory(JSON.stringify(value))).rejects.toThrow()}
});
