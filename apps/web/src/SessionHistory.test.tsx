import { describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { renderToStaticMarkup } from "react-dom/server";
import { readHistory, MAX_HISTORY_BYTES } from "./SessionHistory";
import { HistoryDetails } from "./HistoryScreen";

const fixture = (name: string) => readFileSync(resolve(import.meta.dirname, `../../../examples/sessions/${name}.json`), "utf8");
const partial = () => JSON.parse(fixture("synthetic-human-partial-session"));

describe("read-only human session history", () => {
  it("reads the exact Kotlin fixtures with independent hash verification and no network", async () => {
    const fetch = vi.spyOn(globalThis, "fetch");
    try {
      for (const name of ["synthetic-human-partial-session", "synthetic-human-completed-session"]) {
        const result = await readHistory(fixture(name));
        expect(result.record.schemaVersion).toBe("1.1.0");
        expect(result.title).toBe("Synthetic alternating pyramid");
        expect(result.phases).toHaveLength(9);
        expect(JSON.stringify(result.record)).not.toContain('"type":"tick"');
      }
      expect(fetch).not.toHaveBeenCalled();
    } finally { fetch.mockRestore(); }
  });
  it("shows observed time, pause and unknown gaps without inventing completed exercise", async () => {
    const history = await readHistory(fixture("synthetic-human-partial-session"));
    const html = renderToStaticMarkup(<HistoryDetails history={history} sample/>);
    for (const text of ["Ended early", "5.75 s", "1.2 s", "0.9 s", "duration unknown", "including planned rests", "Synthetic example", "Quadruped hip lift"]) expect(html).toContain(text);
    expect(html).not.toContain("private-receipt");
  });
  it("reads older files without inventing newer timing facts", async () => {
    const result = await readHistory(fixture("synthetic-completed-session"));
    expect(result.record.schemaVersion).toBe("1.0.0");
    expect(result.record.completionKind).toBeUndefined();
    const html = renderToStaticMarkup(<HistoryDetails history={result}/>);
    for (const text of ["Earlier recording format", "Not recorded", "Earlier format; not verified"]) expect(html).toContain(text);
  });
  it("rejects tick logs, unsupported versions and broken plan hashes", async () => {
    for (const change of [
      (value: any) => { value.schemaVersion = "3.0.0"; },
      (value: any) => { value.events[0].type = "tick"; },
      (value: any) => { value.recording = { actions: [] }; },
      (value: any) => { value.sourcePlanSnapshot.compiledPlanJson += " "; },
      (value: any) => { value.executionPlanHash = "sha256:" + "0".repeat(64); },
    ]) {
      const value = partial(); change(value);
      await expect(readHistory(JSON.stringify(value))).rejects.toThrow();
    }
  });
  it("rejects duplicated events, invented gap durations and inconsistent outcomes", async () => {
    for (const change of [
      (value: any) => { value.events.push(value.events[0]); },
      (value: any) => { value.events.find((e: any) => e.type === "interruption").detail.gapDurationMs = 0; },
      (value: any) => { value.completionKind = "plannedEnd"; },
      (value: any) => { value.events.at(-1).observedRunMs = 0; },
      (value: any) => { value.localTrainingDate = "2026-11-02"; },
      (value: any) => { value.events[1].planStepId = "unknown_step"; },
    ]) {
      const value = partial(); change(value);
      await expect(readHistory(JSON.stringify(value))).rejects.toThrow();
    }
  });
  it("renders user text as text, never as HTML", async () => {
    const value = partial(); value.notes = '<script>alert("private")</script>';
    const result = await readHistory(JSON.stringify(value));
    const html = renderToStaticMarkup(<HistoryDetails history={result}/>);
    expect(html).not.toContain("<script>"); expect(html).toContain("&lt;script&gt;");
  });
  it("bounds input size and rejects malformed JSON without echoing its contents", async () => {
    await expect(readHistory(" ".repeat(MAX_HISTORY_BYTES + 1))).rejects.toThrow("up to 8 MB");
    await expect(readHistory('{"private_source":"do not echo"')).rejects.toThrow("not a valid session JSON");
  });
});
