import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import schema10 from "../../../schemas/session.v1.schema.json?raw";
import schema11 from "../../../schemas/session.v1.1.schema.json?raw";
import { isMap, isScalar, isSeq, parseDocument } from "yaml";

export const MAX_HISTORY_BYTES = 8 * 1024 * 1024;
const ajv = new Ajv2020({ allErrors: false, strict: true, strictRequired: false,
  coerceTypes: false, useDefaults: false, removeAdditional: false });
addFormats(ajv);
// Only repository schemas are compiled, never schemas supplied by the imported file.
const validators = new Map([
  ["1.0.0", ajv.compile(JSON.parse(schema10))],
  ["1.1.0", ajv.compile(JSON.parse(schema11))],
]);

export interface HistoryEvent {
  eventId: string; type: string; recordedAt: string; planStepId?: string; phaseId?: string; setId?: string;
  scheduledElapsedMs?: number; observedRunMs?: number; actualElapsedMs?: number;
  pauseMs?: number; unobservedGapMs?: number; unknownGapCount?: number;
  detail?: { gapDurationKnown?: boolean; gapDurationMs?: number; completionKind?: string; resumedAfterInterruption?: boolean };
}
export interface HumanHistory {
  schemaVersion: string; sessionId: string; templateId: string; revisionId: string;
  executionPlanHash: string; engineVersion: string; startedAt: string; finishedAt?: string;
  timeZone: string; localTrainingDate?: string; status: string; completionKind?: string;
  sourcePlanSnapshot?: { compiledPlanJson?: string; hashFormat?: string; planFormatVersion?: string; templateYaml?: string; exerciseYamls?: string[] };
  timing?: Record<string, number>;
  events: HistoryEvent[];
  organizationContext?: { season?: { name: string }; program?: { name: string } };
  overallRpe?: number | null; personalQualityRating?: number | null; journalSummary?: string; notes?: string;
  performanceBySet?: { blockId: string; occurrenceId: string; setId: string; variationId: string;
    completedRepetitions?: number; holdDurationMs?: number; rpe?: number; notes?: string;
    resistanceActual?: { weight?: { value: number; unit: string }; bands?: { equipmentId: string; count: number; placement: string }[]; notes?: string };
  }[];
}
export interface HistoryPhase {
  index: number; stepId: string; phaseId?: string; setId?: string; name: string; startMs: number; endMs: number;
  exerciseId?: string; variationId?: string; resistanceLabel?: string;
}
export interface ReadHistory { record: HumanHistory; phases: HistoryPhase[]; title?: string; labels: Record<string, string> }

function sourceLabels(source: HumanHistory["sourcePlanSnapshot"]): { title?: string; labels: Record<string, string> } {
  const labels: Record<string, string> = Object.create(null);
  const label = (names: unknown) => {
    if (!isMap(names)) return undefined;
    const english = names.get("en");
    if (typeof english === "string") return english;
    return names.items.map(pair => isScalar(pair.value) ? pair.value.value : undefined).find(value => typeof value === "string") as string | undefined;
  };
  const document = (text?: unknown) => {
    if (typeof text !== "string" || !text || text.length > 1048576) return undefined;
    try {
      const parsed = parseDocument(text, { strict: true, uniqueKeys: true });
      // Only inspect scalar names at known AST paths. Never expand aliases or execute tags.
      return parsed.errors.length === 0 && isMap(parsed.contents) ? parsed.contents : undefined;
    } catch { return undefined; }
  };
  const title = label(document(source?.templateYaml)?.get("names", true));
  for (const text of Array.isArray(source?.exerciseYamls) ? source.exerciseYamls : []) {
    const root = document(text);
    if (!root) continue;
    const id = root.get("familyId"); const name = label(root.get("names", true));
    if (typeof id === "string" && name) labels[`family/${id}`] = name;
    const variations = root.get("variations", true);
    if (isSeq(variations)) for (const item of variations.items) {
      if (!isMap(item)) continue;
      const variationId = item.get("variationId"); const variationName = label(item.get("names", true));
      if (typeof id === "string" && typeof variationId === "string" && variationName) labels[`variation/${id}/${variationId}`] = variationName;
    }
  }
  return { title, labels };
}

function requireFact(value: unknown, message: string): asserts value {
  if (!value) throw new Error(message);
}
const safeTime = (value: unknown): value is number => Number.isSafeInteger(value) && (value as number) >= 0;

/** Local, read-only validation. Does not persist or transmit the selected file. */
export async function readHistory(text: string): Promise<ReadHistory> {
  requireFact(new TextEncoder().encode(text).byteLength <= MAX_HISTORY_BYTES, "This viewer accepts session files up to 8 MB.");
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new Error("This is not a valid session JSON file."); }
  requireFact(value && typeof value === "object" && !Array.isArray(value), "Expected a session record.");
  const version = (value as Record<string, unknown>).schemaVersion;
  const validate = typeof version === "string" ? validators.get(version) : undefined;
  requireFact(validate, "This session format is not supported by this viewer.");
  requireFact(validate(value), "This session file is invalid or incomplete. The file has not been changed.");
  const record = value as HumanHistory;
  for (const set of record.performanceBySet ?? []) {
    for (const count of [set.completedRepetitions, set.holdDurationMs, ...(set.resistanceActual?.bands ?? []).map(band => band.count)]) {
      requireFact(count === undefined || safeTime(count), "A set result is outside the supported range.");
    }
  }
  requireFact(record.events.length <= 10000, "This session has more events than this viewer supports. The file has not been changed.");
  const ids = record.events.map(event => event.eventId);
  requireFact(ids.length === new Set(ids).size, "The session repeats an event identifier.");
  if (version === "1.0.0") {
    for (const time of Object.values(record.timing ?? {})) requireFact(safeTime(time), "A timing value is outside the supported range.");
    for (const event of record.events) for (const time of [event.scheduledElapsedMs, event.actualElapsedMs]) {
      requireFact(time === undefined || safeTime(time), "An event timing value is outside the supported range.");
    }
    return { record, phases: [], ...sourceLabels(record.sourcePlanSnapshot) }; // Do not invent newer timing facts.
  }

  const source = record.sourcePlanSnapshot!;
  requireFact(source.hashFormat === "sha256-kotlin-timed-1-json-utf8" && record.engineVersion === "kotlin-timed-1", "This frozen plan format is not supported.");
  const bytes = new TextEncoder().encode(source.compiledPlanJson!);
  requireFact(globalThis.crypto?.subtle, "This browser cannot verify the frozen plan. Open the viewer on localhost or a secure connection.");
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), b => b.toString(16).padStart(2, "0")).join("");
  requireFact(record.executionPlanHash === `sha256:${hash}`, "The frozen plan failed its integrity check.");
  let plan: { templateId: string; revisionId: string; planFormatVersion: string; expectedDurationMs: number; phases: HistoryPhase[] };
  try { plan = JSON.parse(source.compiledPlanJson!); } catch { throw new Error("The frozen plan is unreadable."); }
  requireFact(plan && plan.templateId === record.templateId && plan.revisionId === record.revisionId && plan.planFormatVersion === source.planFormatVersion, "The frozen plan identity differs from the session.");
  requireFact(Array.isArray(plan.phases) && plan.phases.length > 0 && plan.phases.length <= 10000, "The frozen timeline is invalid.");
  let boundary = 0;
  for (const [index, phase] of plan.phases.entries()) {
    requireFact(phase && phase.index === index && typeof phase.stepId === "string" && typeof phase.name === "string" && safeTime(phase.startMs) && safeTime(phase.endMs) && phase.startMs === boundary && phase.endMs > boundary, "The frozen timeline is inconsistent.");
    for (const id of [phase.stepId, phase.phaseId, phase.setId, phase.exerciseId, phase.variationId]) {
      requireFact(id == null || (typeof id === "string" && /^[a-zA-Z0-9][a-zA-Z0-9_-]{2,127}$/.test(id)), "The frozen timeline has an invalid source reference.");
    }
    requireFact(phase.resistanceLabel == null || typeof phase.resistanceLabel === "string", "The frozen resistance label is invalid.");
    boundary = phase.endMs;
  }
  const timing = record.timing!;
  requireFact(boundary === plan.expectedDurationMs && timing.plannedDurationMs === plan.expectedDurationMs && timing.scheduledElapsedMs <= boundary, "The planned duration or position is inconsistent.");
  for (const counter of Object.values(timing)) requireFact(safeTime(counter), "A timing value is outside the supported range.");
  let localDate: string;
  try {
    const parts = new Intl.DateTimeFormat("en", { timeZone: record.timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date(record.startedAt));
    const part = (type: string) => parts.find(p => p.type === type)?.value;
    localDate = `${part("year")}-${part("month")}-${part("day")}`;
  } catch { throw new Error("The recorded time zone is not supported."); }
  requireFact(localDate === record.localTrainingDate, "The recorded local training date is inconsistent.");
  const counters = ["observedRunMs", "pauseMs", "unobservedGapMs", "unknownGapCount"] as const;
  const previous = Object.fromEntries(counters.map(key => [key, 0]));
  const counts: Record<string, number> = {};
  for (const event of record.events) {
    counts[event.type] = (counts[event.type] ?? 0) + 1;
    requireFact(safeTime(event.scheduledElapsedMs) && event.scheduledElapsedMs <= boundary, "An event position is invalid.");
    for (const key of counters) {
      const observed = event[key];
      requireFact(safeTime(observed) && previous[key] <= observed && observed <= timing[key], "Event timing is inconsistent.");
      previous[key] = observed;
    }
    if (event.planStepId) {
      const edge = event.type === "phaseCompleted" ? "endMs" : "startMs";
      requireFact(plan.phases.some(p => p.stepId === event.planStepId && (!event.phaseId || p.phaseId === event.phaseId) && (!event.setId || p.setId === event.setId) && p[edge] === event.scheduledElapsedMs), "An event refers to a different frozen phase.");
    }
    if (event.type === "planEndReached") requireFact(event.scheduledElapsedMs === boundary, "Plan end was not reached.");
  }
  requireFact(record.events[0].type === "sessionStarted" && counts.sessionStarted === 1, "The session start is missing or repeated.");
  const last = record.events.at(-1)!;
  const finalizations = (counts.sessionEnded ?? 0) + (counts.sessionDiscarded ?? 0);
  if (record.completionKind === "unfinished") {
    requireFact(finalizations === 0 && (record.status !== "awaitingCompletion" || counts.planEndReached), "The unfinished session has an inconsistent outcome.");
  } else {
    requireFact(finalizations === 1 && last.type === (record.completionKind === "discarded" ? "sessionDiscarded" : "sessionEnded") && last.detail?.completionKind === record.completionKind && last.recordedAt === record.finishedAt, "The final outcome is inconsistent.");
    for (const key of ["scheduledElapsedMs", ...counters] as const) requireFact(last[key] === timing[key], "The final timing differs from the history.");
    requireFact(record.completionKind !== "plannedEnd" || counts.planEndReached, "The planned end is not recorded.");
  }
  return { record, phases: plan.phases, ...sourceLabels(source) };
}
