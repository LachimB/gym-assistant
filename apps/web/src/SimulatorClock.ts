import { initialState, reduce, type CompiledPlan, type EngineCommand, type EngineState } from "@engine";

/** Process-local preview clock; no tab-suspension or persistent-recovery guarantee. */
export class SimulatorClock {
  state: EngineState = initialState();
  wallClock = false;
  private lastSampleMs: number;
  private runningFractionMs = 0;
  private pausedFractionMs = 0;

  constructor(private readonly plan: CompiledPlan, private readonly nowMs: () => number) {
    this.lastSampleMs = nowMs();
  }

  tick(): EngineState {
    const now = this.nowMs();
    if (!Number.isFinite(now) || now < this.lastSampleMs) throw new Error("clock must be monotonic");
    const elapsed = now - this.lastSampleMs;
    this.lastSampleMs = now;
    const status = this.state.status;
    if (this.wallClock && (status === "running" || status === "paused")) {
      const accumulated = elapsed + (status === "running" ? this.runningFractionMs : this.pausedFractionMs);
      const wholeMs = Math.floor(accumulated);
      if (status === "running") this.runningFractionMs = accumulated - wholeMs;
      else this.pausedFractionMs = accumulated - wholeMs;
      this.state = reduce(this.plan, this.state, { op: "advance", ms: wholeMs });
    }
    return this.state;
  }

  send(command: EngineCommand): EngineState {
    // Settle the old status before pause/resume/navigation changes it.
    if (command.op === "noteUnobserved") {
      // The interval since the last sample is uncertain, not observed work.
      this.lastSampleMs = this.nowMs();
    } else {
      this.tick();
    }
    this.state = reduce(this.plan, this.state, command);
    return this.state;
  }

  setWallClock(enabled: boolean): EngineState {
    this.tick();
    this.wallClock = enabled;
    return this.state;
  }

  reset(): EngineState {
    this.state = initialState();
    this.wallClock = false;
    this.runningFractionMs = 0;
    this.pausedFractionMs = 0;
    this.lastSampleMs = this.nowMs();
    return this.state;
  }
}
