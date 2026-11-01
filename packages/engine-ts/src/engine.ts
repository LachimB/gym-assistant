import { parse } from "yaml";

export interface CompiledPhase {
  index: number;
  stepId: string;
  name: string;
  startMs: number;
  endMs: number;
  phaseId?: string;
  setId?: string;
  repetitionIndex?: number;
  exerciseId?: string;
  variationId?: string;
  resistanceLabel: string;
}

export interface CompiledPlan {
  schemaVersion: string;
  templateId: string;
  revisionId: string;
  planFormatVersion: string;
  expectedDurationMs: number;
  phases: CompiledPhase[];
}

export type SessionStatus = "loaded" | "running" | "paused" | "recoveredPaused" | "completed";

export interface EngineEvent {
  type: string;
  scheduledElapsedMs: number;
  observedRunMs: number;
  pauseMs: number;
  unobservedGapMs: number;
  phaseIndex: number | null;
}

export interface EngineState {
  status: SessionStatus;
  scheduledElapsedMs: number;
  observedRunMs: number;
  pauseMs: number;
  unobservedGapMs: number;
  completedPhaseIndexes: number[];
  completionAcknowledged: boolean;
  events: EngineEvent[];
}

export type EngineCommand =
  | { op: "start" }
  | { op: "pause" }
  | { op: "resume" }
  | { op: "advance"; ms: number }
  | { op: "stepForward" }
  | { op: "stepBackward" }
  | { op: "noteUnobserved"; ms: number }
  | { op: "acknowledge" }
  | { op: "endEarly" };

type YamlMap = Record<string, unknown>;

function asMap(value: unknown): YamlMap {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("expected a YAML map");
  }
  return value as YamlMap;
}

function asList(value: unknown): unknown[] {
  if (!Array.isArray(value)) throw new Error("expected a YAML list");
  return value;
}

function text(value: unknown): string {
  if (value === undefined || value === null) throw new Error("missing YAML value");
  return String(value);
}

function resistanceLabel(resistance: unknown): string {
  if (resistance === undefined || resistance === null) return "";
  const bands = asList(asMap(resistance).bands ?? []);
  return bands
    .map((band) => {
      const item = asMap(band);
      return `${text(item.equipmentId)} x${text(item.count)} ${text(item.placement)}`;
    })
    .join(", ");
}

export function compileTemplate(templateYaml: string, exerciseYamls: string[]): CompiledPlan {
  const variationsByExercise = new Map<string, Set<string>>();
  for (const yaml of exerciseYamls) {
    const root = asMap(parse(yaml));
    const familyId = text(root.familyId);
    const variations = new Set(asList(root.variations).map((item) => text(asMap(item).variationId)));
    variationsByExercise.set(familyId, variations);
  }
  const root = asMap(parse(templateYaml));
  const phases: CompiledPhase[] = [];
  let cursor = 0;
  const seen = new Set<string>();
  const claim = (id: string) => {
    if (seen.has(id)) throw new Error(`duplicate structural ID ${id}`);
    seen.add(id);
  };
  for (const sectionNode of asList(root.sections)) {
    const section = asMap(sectionNode);
    claim(text(section.sectionId));
    for (const blockNode of asList(section.blocks)) {
      const block = asMap(blockNode);
      claim(text(block.blockId));
      const sets = new Map<string, {
        repetitions: number;
        exerciseId: string;
        variationId: string;
        resistanceLabel: string;
        phases: { phaseId: string; name: string; durationMs: number }[];
      }>();
      for (const occurrenceNode of asList(block.occurrences)) {
        const occurrence = asMap(occurrenceNode);
        claim(text(occurrence.occurrenceId));
        const exerciseId = text(occurrence.exerciseId);
        const known = variationsByExercise.get(exerciseId);
        if (!known) throw new Error(`unknown exercise ${exerciseId}`);
        for (const setNode of asList(occurrence.sets)) {
          const set = asMap(setNode);
          const setId = text(set.setId);
          if (sets.has(setId)) throw new Error(`duplicate set ID ${setId}`);
          claim(setId);
          const variationId = text(set.variationId);
          if (!known.has(variationId)) throw new Error(`unknown variation ${variationId}`);
          const phaseDefs = asList(set.phases).map((phaseNode) => {
            const phase = asMap(phaseNode);
            if (text(phase.mode) !== "timed") throw new Error("M0 compiler only accepts timed phases");
            return {
              phaseId: text(phase.phaseId),
              name: text(phase.name),
              durationMs: Number(phase.durationMs),
            };
          });
          if (new Set(phaseDefs.map((phase) => phase.phaseId)).size !== phaseDefs.length) {
            throw new Error("duplicate phase id");
          }
          sets.set(setId, {
            repetitions: Number(set.repetitions),
            exerciseId,
            variationId,
            resistanceLabel: resistanceLabel(set.resistance),
            phases: phaseDefs,
          });
        }
      }
      const coverage: string[] = [];
      for (const stepNode of asList(block.order)) {
        const step = asMap(stepNode);
        const stepId = text(step.stepId);
        claim(stepId);
        if (text(step.kind) === "rest") {
          const duration = Number(step.durationMs);
          phases.push({
            index: phases.length,
            stepId,
            name: "rest",
            startMs: cursor,
            endMs: cursor + duration,
            resistanceLabel: "",
          });
          cursor += duration;
        } else {
          const setId = text(step.setId);
          const setDef = sets.get(setId);
          if (!setDef) throw new Error(`unknown set: ${setId}`);
          const repetition = Number(step.repetitionIndex);
          coverage.push(`${setId}:${repetition}`);
          for (const phase of setDef.phases) {
            phases.push({
              index: phases.length,
              stepId,
              name: phase.name,
              startMs: cursor,
              endMs: cursor + phase.durationMs,
              phaseId: phase.phaseId,
              setId,
              repetitionIndex: repetition,
              exerciseId: setDef.exerciseId,
              variationId: setDef.variationId,
              resistanceLabel: setDef.resistanceLabel,
            });
            cursor += phase.durationMs;
          }
        }
      }
      const expected = [...sets.entries()].flatMap(([setId, setDef]) =>
        Array.from({ length: setDef.repetitions }, (_, index) => `${setId}:${index + 1}`),
      );
      if (expected.length !== coverage.length || expected.some((item) => !coverage.includes(item))) {
        throw new Error("missing or extra repetitions");
      }
    }
  }
  return {
    schemaVersion: text(root.schemaVersion),
    templateId: text(root.templateId),
    revisionId: text(root.revisionId),
    planFormatVersion: "1.0.0",
    expectedDurationMs: cursor,
    phases,
  };
}

export function initialState(): EngineState {
  return {
    status: "loaded",
    scheduledElapsedMs: 0,
    observedRunMs: 0,
    pauseMs: 0,
    unobservedGapMs: 0,
    completedPhaseIndexes: [],
    completionAcknowledged: false,
    events: [],
  };
}

function activePhase(plan: CompiledPlan, scheduled: number): CompiledPhase | undefined {
  return plan.phases.find((phase) => scheduled >= phase.startMs && scheduled < phase.endMs);
}

function event(state: EngineState, type: string, phaseIndex: number | null): EngineEvent {
  return {
    type,
    scheduledElapsedMs: state.scheduledElapsedMs,
    observedRunMs: state.observedRunMs,
    pauseMs: state.pauseMs,
    unobservedGapMs: state.unobservedGapMs,
    phaseIndex,
  };
}

export function reduce(plan: CompiledPlan, state: EngineState, command: EngineCommand): EngineState {
  if (command.op === "start") {
    if (state.status !== "loaded") return state;
    const next = { ...state, status: "running" as const };
    return { ...next, events: [...state.events, event(next, "sessionStarted", 0), event(next, "phaseStarted", 0)] };
  }
  if (command.op === "pause") {
    if (state.status !== "running") return state;
    return { ...state, status: "paused", events: [...state.events, event(state, "pause", null)] };
  }
  if (command.op === "resume") {
    if (state.status === "paused") {
      return { ...state, status: "running", events: [...state.events, event(state, "resume", null)] };
    }
    if (state.status === "recoveredPaused") {
      return { ...state, status: "running", events: [...state.events, event(state, "resumeAfterGap", null)] };
    }
    return state;
  }
  if (command.op === "acknowledge") {
    if (state.status !== "completed" || state.completionAcknowledged) return state;
    return { ...state, completionAcknowledged: true };
  }
  if (command.op === "endEarly") {
    if (state.status !== "running" && state.status !== "paused" && state.status !== "recoveredPaused") {
      return state;
    }
    return { ...state, status: "completed", completionAcknowledged: true };
  }
  if (command.op === "noteUnobserved") {
    if (state.status !== "running" && state.status !== "paused") return state;
    const next = { ...state, status: "recoveredPaused" as const, unobservedGapMs: state.unobservedGapMs + command.ms };
    return { ...next, events: [...state.events, event(next, "unobservedGap", null)] };
  }
  if (command.op === "advance") {
    if (command.ms === 0) return state;
    if (state.status === "paused") return { ...state, pauseMs: state.pauseMs + command.ms };
    if (state.status !== "running") return state;
    const total = plan.expectedDurationMs;
    let scheduled = state.scheduledElapsedMs;
    const target = Math.min(scheduled + command.ms, total);
    const completed = new Set(state.completedPhaseIndexes);
    const events = [...state.events];
    while (scheduled < target) {
      const phase = activePhase(plan, scheduled);
      if (!phase) break;
      if (target >= phase.endMs) {
        completed.add(phase.index);
        scheduled = phase.endMs;
        // Both events describe the boundary, not the start of this advance.
        const boundaryState = {
          ...state,
          scheduledElapsedMs: scheduled,
          observedRunMs: state.observedRunMs + (scheduled - state.scheduledElapsedMs),
        };
        events.push(event(boundaryState, "phaseCompleted", phase.index));
        const next = activePhase(plan, scheduled);
        if (next) events.push(event(boundaryState, "phaseStarted", next.index));
      } else {
        scheduled = target;
      }
    }
    return {
      ...state,
      status: scheduled >= total ? "completed" : "running",
      scheduledElapsedMs: scheduled,
      observedRunMs: state.observedRunMs + (scheduled - state.scheduledElapsedMs),
      completedPhaseIndexes: [...completed].sort((a, b) => a - b),
      events,
    };
  }
  if (state.status !== "running" && state.status !== "paused") return state;
  const current = activePhase(plan, state.scheduledElapsedMs);
  if (!current) return state;
  const destination = command.op === "stepForward"
    ? plan.phases[current.index + 1]?.startMs
    : current.index > 0
      ? plan.phases[current.index - 1].startMs
      : undefined;
  if (destination === undefined) return state;
  const landed = activePhase(plan, destination);
  const completed = command.op === "stepForward"
    ? state.completedPhaseIndexes
    : state.completedPhaseIndexes.filter((index) => index < (landed?.index ?? 0));
  const moved: EngineState = { ...state, scheduledElapsedMs: destination, completedPhaseIndexes: completed };
  const kind = command.op === "stepForward" ? "skip" : "rewind";
  return {
    ...moved,
    events: [...state.events, event(moved, kind, landed?.index ?? null), event(moved, "phaseStarted", landed?.index ?? null)],
  };
}

export function reduceAll(plan: CompiledPlan, commands: EngineCommand[]): EngineState {
  return commands.reduce((state, command) => reduce(plan, state, command), initialState());
}

export function snapshot(plan: CompiledPlan, state: EngineState) {
  const phase = activePhase(plan, state.scheduledElapsedMs);
  const remaining = !phase || state.status === "completed" ? 0 : phase.endMs - state.scheduledElapsedMs;
  return {
    status: state.status,
    scheduledElapsedMs: state.scheduledElapsedMs,
    observedRunMs: state.observedRunMs,
    pauseMs: state.pauseMs,
    unobservedGapMs: state.unobservedGapMs,
    activePhaseIndex: phase?.index ?? null,
    phaseName: phase?.name ?? null,
    stepId: phase?.stepId ?? null,
    setId: phase?.setId ?? null,
    repetitionIndex: phase?.repetitionIndex ?? null,
    exerciseId: phase?.exerciseId ?? null,
    variationId: phase?.variationId ?? null,
    resistanceLabel: phase?.resistanceLabel ?? "",
    phaseRemainingMs: remaining,
    completedPhaseIndexes: [...state.completedPhaseIndexes].sort((a, b) => a - b),
    simulatedCue: state.status === "running" && phase ? `Simulated cue: ${phase.name}` : null,
  };
}

export function timelineProjection(plan: CompiledPlan) {
  return plan.phases.map((phase) => {
    const row: Record<string, string | number> = {
      stepId: phase.stepId,
      startMs: phase.startMs,
      endMs: phase.endMs,
      name: phase.name,
    };
    if (phase.phaseId !== undefined) row.phaseId = phase.phaseId;
    if (phase.setId !== undefined) row.setId = phase.setId;
    if (phase.repetitionIndex !== undefined) row.repetitionIndex = phase.repetitionIndex;
    return row;
  });
}
