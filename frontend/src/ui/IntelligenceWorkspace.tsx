import { useEffect, useState, type FormEvent } from "react";
import { intelligenceApi } from "../api";
import type { User, WorkforceInsight, WorkforceSummary } from "../types";

export function IntelligenceWorkspace({ user }: { user: User }) {
  const allowed = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const [summary, setSummary] = useState<WorkforceSummary | null>(null);
  const [question, setQuestion] = useState("");
  const [insight, setInsight] = useState<WorkforceInsight | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!allowed) return;
    let active = true;
    intelligenceApi.workforce().then((value) => { if (active) setSummary(value); })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : "Could not load workforce metrics"); });
    return () => { active = false; };
  }, [allowed]);

  if (!allowed) return null;

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError("");
    try { setInsight(await intelligenceApi.ask(question)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not generate an insight"); }
    finally { setBusy(false); }
  }

  return <section className="dashboard-section" aria-labelledby="intelligence-title">
    <div className="directory-heading"><div><div className="eyebrow">AI WORKFORCE INTELLIGENCE</div><h1 id="intelligence-title">Workforce insights</h1><p>Aggregate trends and explainable decision-support estimates.</p></div></div>
    {error && <p className="error" role="alert">{error}</p>}
    <div className="dashboard-cards">
      <article className="dashboard-card"><span>Active employees</span><strong>{summary?.activeEmployees ?? "—"}</strong></article>
      <article className="dashboard-card"><span>Attendance records · 30 days</span><strong>{summary?.attendanceRecordsLast30Days ?? "—"}</strong></article>
      <article className="dashboard-card"><span>Late records · 30 days</span><strong>{summary?.lateRecordsLast30Days ?? "—"}</strong></article>
      <article className="dashboard-card"><span>Goals in progress</span><strong>{summary?.goalsInProgress ?? "—"}</strong></article>
    </div>
    <div className="dashboard-panel"><h2>Ask about workforce trends</h2>
      <form onSubmit={ask} className="directory-filters">
        <label className="sr-only" htmlFor="workforce-question">Question</label>
        <input id="workforce-question" maxLength={500} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Summarize recent attendance and goal trends" required />
        <button className="quiet" disabled={busy}>{busy ? "Analyzing…" : "Ask"}</button>
      </form>
      {insight && <div role="status"><p>{insight.answer}</p><small>{insight.aiGenerated ? "AI generated from aggregate metrics" : "Rule-based fallback · AI provider unavailable or disabled"} · {insight.dataScope}</small><p className="muted">{insight.disclaimer}</p></div>}
      <p className="muted">Insights use aggregate metrics only. Attrition indicators are advisory, may be incomplete, and must not be used for employment decisions.</p>
    </div>
  </section>;
}
