import { useRef, useState } from "react";
import partialSample from "../../../examples/sessions/synthetic-human-partial-session.json?raw";
import extraRepetitionSample from "../../../examples/sessions/synthetic-runtime-v2-session.json?raw";
import completedSample from "../../../examples/sessions/synthetic-human-completed-session.json?raw";
import { MAX_HISTORY_BYTES, readHistory, type ReadHistory, type HistoryEvent } from "./SessionHistory";

export const duration = (ms?: number | null) => ms == null ? "Not recorded" : `${(ms / 1000).toLocaleString("en", { maximumFractionDigits: 3 })} s`;
const eventNames: Record<string, string> = {
  repetitionAdded: "Extra repetition added",
  sessionStarted: "Workout started", phaseStarted: "Phase started", phaseCompleted: "Phase completed",
  pause: "Paused", resume: "Resumed", skip: "Skipped ahead to", rewind: "Moved back to", interruption: "Interruption",
  planEndReached: "Reached end of plan", sessionEnded: "Workout ended", sessionDiscarded: "Workout discarded",
  sessionCompleted: "Workout completed", manualRepCompleted: "Repetition completed", setActualUpdated: "Set results updated",
  blockInserted: "Block added", blockReordered: "Block order changed", noteAdded: "Note added",
};
const outcomes: Record<string, string> = {
  unfinished: "Unfinished", plannedEnd: "Reached planned end", partial: "Ended early", discarded: "Discarded",
};
function eventLabel(event: HistoryEvent, modern: boolean): string {
  if (!modern) return eventNames[event.type] ?? event.type;
  if (event.type === "interruption") return event.detail?.gapDurationKnown
    ? `Interruption · ${duration(event.detail.gapDurationMs)} unobserved`
    : "Interruption · duration unknown";
  if (event.type === "resume" && event.detail?.resumedAfterInterruption) return "Resumed after interruption";
  return eventNames[event.type] ?? event.type;
}

export function HistoryDetails({ history, sample = false }: { history: ReadHistory; sample?: boolean }) {
  const { record, phases, labels, title } = history;
  const modern = record.schemaVersion !== "1.0.0";
  const timing = record.timing ?? {};
  const phaseFor = (event: HistoryEvent) => phases.find(p => event.adjustmentId ? p.adjustmentId === event.adjustmentId && p.role === "work" : event.phaseInstanceId ? p.instanceId === event.phaseInstanceId : p.stepId === event.planStepId && (!event.phaseId || p.phaseId === event.phaseId));
  const phaseLabel = (event: HistoryEvent) => {
    const phase = phaseFor(event);
    if (!phase) return event.planStepId ? "Recorded phase" : "Whole workout";
    return [labels[`family/${phase.exerciseId}`], labels[`variation/${phase.exerciseId}/${phase.variationId}`], phase.side, phase.name, record.schemaVersion === "1.2.0" && phase.repetitionIndex ? `repetition ${phase.repetitionIndex}` : undefined].filter(Boolean).join(" · ");
  };
  return <section aria-label="Workout details" className="history-details">
    {sample && <p className="sample-label">Synthetic example · not your workout history</p>}
    <h2>{title ?? "Recorded workout"}</h2>
    <p className="outcome">{outcomes[record.completionKind ?? ""] ?? record.status}</p>
    <p>{record.localTrainingDate ?? record.startedAt} · {record.timeZone}</p>
    <p>{record.organizationContext?.program?.name ?? "Ungrouped workout"}{record.organizationContext?.season ? ` · ${record.organizationContext.season.name}` : ""}</p>
    {!modern && <p className="warning">Earlier recording format. Missing timing and interruption details are shown as not recorded.</p>}
    {record.completionKind === "plannedEnd" && <p>Reaching the end of the plan does not mean every repetition was performed. Skips remain in the event history.</p>}
    <dl className="timing-summary">
      <div><dt>Planned duration</dt><dd>{duration(modern ? timing.plannedDurationMs : timing.scheduledDurationMs)}</dd></div>
      {record.schemaVersion === "1.2.0" && <><div><dt>Adjusted planned duration</dt><dd>{duration(timing.adjustedPlannedDurationMs)}</dd></div><div><dt>Position in adjusted plan</dt><dd>{duration(timing.adjustedScheduledElapsedMs)}</dd></div><div><dt>Extra repetitions added</dt><dd>{record.adjustments?.length}</dd></div></>}
      <div><dt>Position in plan</dt><dd>{record.schemaVersion === "1.2.0" && timing.scheduledElapsedMs === null ? "Within an added repetition" : duration(modern ? timing.scheduledElapsedMs : undefined)}</dd></div>
      <div><dt>Observed running time, including planned rests</dt><dd>{duration(modern ? timing.observedRunMs : undefined)}</dd></div>
      <div><dt>Paused time</dt><dd>{duration(modern ? timing.pauseMs : undefined)}</dd></div>
      <div><dt>Known unobserved time</dt><dd>{duration(modern ? timing.unobservedGapMs : undefined)}</dd></div>
      <div><dt>Interruptions of unknown duration</dt><dd>{modern ? timing.unknownGapCount : "Not recorded"}</dd></div>
    </dl>
    {!modern && <dl className="timing-summary">
      <div><dt>Recorded active duration</dt><dd>{duration(timing.activeDurationMs)}</dd></div>
      <div><dt>Recorded rest duration</dt><dd>{duration(timing.restDurationMs)}</dd></div>
      <div><dt>Recorded total duration</dt><dd>{duration(timing.totalDurationMs)}</dd></div>
    </dl>}
    {record.overallRpe != null && <p>Overall effort (RPE): {record.overallRpe}/10</p>}
    {record.personalQualityRating != null && <p>Session quality: {record.personalQualityRating}/5</p>}
    {record.journalSummary && <p>Journal: {record.journalSummary}</p>}
    {record.notes && <p>Notes: {record.notes}</p>}
    {record.performanceBySet && record.performanceBySet.length > 0 && <section aria-label="Recorded set results">
      <h3>Recorded set results</h3>
      <div className="history-table"><table><thead><tr><th scope="col">Set</th><th scope="col">Completed repetitions</th><th scope="col">Hold duration</th><th scope="col">Actual resistance</th><th scope="col">Effort</th><th scope="col">Notes</th></tr></thead>
        <tbody>{record.performanceBySet.map((set, index) => {
          const phase = phases.find(p => p.setId === set.setId);
          const name = phase && labels[`family/${phase.exerciseId}`];
          const resistance = set.resistanceActual;
          const parts = [resistance?.weight ? `${resistance.weight.value} ${resistance.weight.unit}` : "",
            ...(resistance?.bands ?? []).map(band => `${band.equipmentId} × ${band.count} · ${band.placement}`), resistance?.notes ?? ""].filter(Boolean);
          return <tr key={`${set.blockId}/${set.occurrenceId}/${set.setId}/${index}`}>
            <td>{name ?? set.setId}</td><td>{set.completedRepetitions ?? "Not recorded"}</td><td>{duration(set.holdDurationMs)}</td>
            <td>{parts.join("; ") || "Not recorded"}</td><td>{set.rpe === undefined ? "Not recorded" : `${set.rpe}/10`}</td><td>{set.notes ?? "—"}</td>
          </tr>;
        })}</tbody></table></div>
      <p className="meta">If an exercise label was not captured, its original set reference is shown. Missing results are not inferred from phase counts.</p>
    </section>}
    <h3>Workout events</h3>
    <p className="meta">Times show when events were recorded. Timer saves are not listed as workout events.</p>
    <div className="history-table"><table><thead><tr><th scope="col">Recorded at</th><th scope="col">Event</th><th scope="col">Phase</th><th scope="col">{modern ? "Observed running time" : "Recorded actual elapsed time"}</th></tr></thead>
      <tbody>{record.events.map(event => <tr key={event.eventId}>
        <td><time dateTime={event.recordedAt}>{event.recordedAt}</time></td>
        <td>{eventLabel(event, modern)}</td><td>{phaseLabel(event)}</td>
        <td>{duration(modern ? event.observedRunMs : event.actualElapsedMs)}</td>
      </tr>)}</tbody></table></div>
    <details><summary>Source details</summary><dl>
      <dt>Session</dt><dd>{record.sessionId}</dd><dt>Training Template</dt><dd>{record.templateId}</dd>
      <dt>Template revision</dt><dd>{record.revisionId}</dd><dt>Engine</dt><dd>{record.engineVersion}</dd>
      <dt>Plan integrity</dt><dd>{modern ? "Frozen plan hash verified" : "Earlier format; not verified by this viewer"}</dd>
    </dl></details>
  </section>;
}

export function HistoryScreen() {
  const [history, setHistory] = useState<ReadHistory | null>(null);
  const [sample, setSample] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  async function open(text: string, isSample: boolean, request = ++generation.current) {
    if (request !== generation.current) return;
    setHistory(null); setError(""); setBusy(true);
    try {
      const result = await readHistory(text);
      if (request === generation.current) { setHistory(result); setSample(isSample); }
    } catch (cause) {
      if (request === generation.current) setError(cause instanceof Error ? cause.message : "Unable to read this session.");
    } finally { if (request === generation.current) setBusy(false); }
  }
  return <main className="history-page">
    <h1>Workout history</h1>
    <p>Open a saved session to review its meaningful events and timing. Files stay in this browser tab; this view does not upload, edit or record workouts.</p>
    <label className="file-label">Open a session JSON file
      <input type="file" accept="application/json,.json" onChange={async event => {
        const file = event.currentTarget.files?.[0]; event.currentTarget.value = "";
        if (!file) return;
        if (file.size > MAX_HISTORY_BYTES) { ++generation.current; setHistory(null); setBusy(false); setError("This viewer accepts session files up to 8 MB."); return; }
        const request = ++generation.current;
        setHistory(null); setError(""); setBusy(true);
        try { await open(await file.text(), false, request); } catch {
          if (request === generation.current) { setError("Unable to read the selected file."); setBusy(false); }
        }
      }}/>
    </label>
    <div className="controls">
      <button type="button" onClick={() => void open(partialSample, true)}>View synthetic partial example</button>
      <button type="button" onClick={() => void open(completedSample, true)}>View synthetic completed example</button>
      <button type="button" onClick={() => void open(extraRepetitionSample, true)}>View synthetic extra-repetition example</button>
      <button type="button" onClick={() => { ++generation.current; setHistory(null); setError(""); setBusy(false); }}>Clear view</button>
    </div>
    {busy && <p role="status">Checking the saved session…</p>}
    {error && <p role="alert" className="warning">{error}</p>}
    {history ? <HistoryDetails history={history} sample={sample}/> : !busy && !error ? <p>No session is open. Drive synchronization is not connected to this view yet.</p> : null}
  </main>;
}
