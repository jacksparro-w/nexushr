import { useCallback, useEffect, useState, type FormEvent } from "react";
import { attendanceApi } from "../api";
import type { AttendanceAudit, AttendanceRecord, AttendanceSummary, User } from "../types";

type Props = { user: User };

function monthRange(month: string) {
  const [year, number] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, number, 0)).getUTCDate();
  return { from: `${month}-01`, to: `${month}-${String(lastDay).padStart(2, "0")}` };
}

function displayTime(value: string | null) {
  return value ? new Date(value).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—";
}

function duration(minutes: number) {
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

function localInput(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`;
}

function CorrectionForm({ record, onCorrect }: { record: AttendanceRecord; onCorrect: () => void }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const inValue = String(form.get("clockInAt"));
      const outValue = String(form.get("clockOutAt"));
      await attendanceApi.correct(record.id, new Date(inValue).toISOString(), outValue ? new Date(outValue).toISOString() : null, String(form.get("reason")));
      onCorrect();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not correct attendance");
    } finally {
      setBusy(false);
    }
  }

  return <details className="attendance-correction"><summary>Correct record</summary><form onSubmit={submit}>
    <label>Clock in<input type="datetime-local" step="1" name="clockInAt" required defaultValue={localInput(record.clockInAt)}/></label>
    <label>Clock out<input type="datetime-local" step="1" name="clockOutAt" defaultValue={localInput(record.clockOutAt)}/></label>
    <label>Reason<input name="reason" maxLength={500} required placeholder="Explain why this record needs correction"/></label>
    <button className="quiet" disabled={busy}>{busy ? "Saving…" : "Save correction"}</button>
  </form>{error && <p className="error" role="alert">{error}</p>}</details>;
}

function AuditHistory({ recordId }: { recordId: string }) {
  const [entries, setEntries] = useState<AttendanceAudit[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  async function load() {
    try {
      setEntries(await attendanceApi.audit(recordId));
      setLoaded(true);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load correction history");
    }
  }
  return <details onToggle={(event) => {
    if ((event.currentTarget as HTMLDetailsElement).open && !loaded) void load();
  }}><summary>Audit history</summary>{error && <p className="error" role="alert">{error}</p>}
    {entries.map((entry) => <div className="attendance-audit-entry" key={entry.id}>
      <strong>{new Date(entry.correctedAt).toLocaleString()} · {entry.correctedBy}</strong>
      <span>{displayTime(entry.previousClockInAt)}–{displayTime(entry.previousClockOutAt)} → {displayTime(entry.correctedClockInAt)}–{displayTime(entry.correctedClockOutAt)}</span>
      <span>{entry.reason}</span>
    </div>)}
    {loaded && !entries.length && <p className="muted">No corrections recorded.</p>}
  </details>;
}

export function AttendanceWorkspace({ user }: Props) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const [month, setMonth] = useState(() => {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
  });
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [currentRecord, setCurrentRecord] = useState<AttendanceRecord | null>(null);
  const [summary, setSummary] = useState<AttendanceSummary | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const currentOpen = currentRecord?.status === "IN_PROGRESS" ? currentRecord : null;
  const dayComplete = currentRecord?.status === "COMPLETE";

  const load = useCallback(async () => {
    setError("");
    const range = monthRange(month);
    try {
      if (canManage) {
        const result = await attendanceApi.managedHistory(range.from, range.to, page);
        setRecords(result.content);
        setTotalPages(result.totalPages);
        setSummary(await attendanceApi.managedSummary(month));
      } else {
        setRecords(await attendanceApi.ownHistory(range.from, range.to));
        setCurrentRecord(await attendanceApi.current());
        setSummary(await attendanceApi.summary(month));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load attendance");
    }
  }, [canManage, month, page]);

  useEffect(() => { void load(); }, [load]);

  async function recordPunch(action: "in" | "out") {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = action === "in" ? await attendanceApi.clockIn() : await attendanceApi.clockOut();
      setMessage(action === "in" ? `Clocked in at ${displayTime(result.clockInAt)}.` : `Clocked out at ${displayTime(result.clockOutAt)}.`);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Attendance action failed");
    } finally {
      setBusy(false);
    }
  }

  return <section className="attendance-section">
    <div className="directory-heading"><div><div className="eyebrow">DAILY OPERATIONS</div><h1>Attendance</h1><p>{canManage ? "Review employee attendance and correct records with an auditable reason." : "Record your workday and review your attendance history."}</p></div></div>
    {!canManage && <div className="attendance-punch-card"><div><strong>{currentOpen ? "Shift in progress" : dayComplete ? "Attendance complete for today" : "Ready to start your workday?"}</strong><span>{currentOpen ? `Clocked in at ${displayTime(currentOpen.clockInAt)}${currentOpen.late ? " · Late arrival" : ""}` : dayComplete ? "Your daily attendance record is complete." : "One clock-in and clock-out per workday."}</span></div><button className="submit" disabled={busy || Boolean(dayComplete)} onClick={() => void recordPunch(currentOpen ? "out" : "in")}>{busy ? "Recording…" : currentOpen ? "Clock out" : dayComplete ? "Completed" : "Clock in"}</button></div>}
    {error && <p className="error" role="alert">{error}</p>}{message && <p className="notice" role="status">{message}</p>}
    {summary && <div className="attendance-summary"><article><span>{canManage ? "Recorded workdays" : "Days worked"}</span><strong>{summary.daysWorked}</strong></article><article><span>Late arrivals</span><strong>{summary.lateDays}</strong></article><article><span>Completed days</span><strong>{summary.completedDays}</strong></article><article><span>Total hours worked</span><strong>{duration(summary.totalWorkedMinutes)}</strong></article></div>}
    <div className="attendance-history-heading"><h2>{canManage ? "Attendance records" : "My attendance history"}</h2><label>Month<input type="month" value={month} onChange={(event) => { setPage(0); setMonth(event.target.value); }}/></label></div>
    <div className="employee-table-wrap"><table><thead><tr>{canManage && <th>Employee</th>}<th>Date</th><th>Clock in</th><th>Clock out</th><th>Worked</th><th>Status</th>{canManage && <th>Correction</th>}</tr></thead><tbody>
      {records.map((record) => <tr key={record.id}>{canManage && <td><strong>{record.employeeName}</strong><span>{record.employeeCode}</span></td>}<td>{record.workDate}</td><td>{displayTime(record.clockInAt)}</td><td>{displayTime(record.clockOutAt)}</td><td>{record.clockOutAt ? duration(record.workedMinutes) : "—"}</td><td><span className={`pill ${record.late ? "late" : record.status === "IN_PROGRESS" ? "in-progress" : "active"}`}>{record.status === "IN_PROGRESS" ? "IN PROGRESS" : record.late ? "LATE" : "COMPLETE"}</span></td>{canManage && <td><CorrectionForm record={record} onCorrect={() => void load()}/><AuditHistory recordId={record.id}/></td>}</tr>)}
      {!records.length && <tr><td colSpan={canManage ? 7 : 5} className="empty-state">No attendance records for this month.</td></tr>}
    </tbody></table></div>
    {canManage && <div className="pagination"><span>{records.length ? `Page ${page + 1} of ${totalPages}` : "0 records"}</span><div><button className="quiet" disabled={page <= 0} onClick={() => setPage((value) => value - 1)}>Previous</button><button className="quiet" disabled={!totalPages || page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Next</button></div></div>}
  </section>;
}
