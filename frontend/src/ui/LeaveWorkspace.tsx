import { useCallback, useEffect, useState, type FormEvent } from "react";
import { leaveApi, peopleApi } from "../api";
import type { Employee, EmployeePage, LeaveBalance, LeaveRequest, LeaveType, User } from "../types";

type Props = { user: User };
const currentYear = new Date().getFullYear();

function LeaveStatusPill({ status }: { status: LeaveRequest["status"] }) {
  return <span className={`pill ${status.toLowerCase().replace("_", "-")}`}>{status.replaceAll("_", " ")}</span>;
}

export function LeaveWorkspace({ user }: Props) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const canReview = canManage || user.roles.includes("MANAGER");
  const canSelfServe = user.roles.some((role) => role === "EMPLOYEE" || role === "MANAGER");
  const [types, setTypes] = useState<LeaveType[]>([]);
  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [reviewRequests, setReviewRequests] = useState<LeaveRequest[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError("");
    const results = await Promise.allSettled([
      leaveApi.types(canManage),
      canSelfServe ? leaveApi.ownBalances(currentYear) : Promise.resolve([]),
      canSelfServe ? leaveApi.ownRequests() : Promise.resolve([]),
      canReview ? leaveApi.requests() : Promise.resolve([]),
      canManage ? peopleApi.employees(new URLSearchParams({ page: "0", size: "100", status: "ACTIVE" })) : Promise.resolve(null),
    ]);
    const [typeResult, balanceResult, ownResult, reviewResult, employeeResult] = results;
    const failed = results.find((result) => result.status === "rejected");
    if (failed?.status === "rejected") setError(failed.reason instanceof Error ? failed.reason.message : "Could not load leave information");
    if (typeResult.status === "fulfilled") setTypes(typeResult.value);
    if (balanceResult.status === "fulfilled") setBalances(balanceResult.value);
    if (ownResult.status === "fulfilled") setRequests(ownResult.value);
    if (reviewResult.status === "fulfilled") setReviewRequests(reviewResult.value);
    if (employeeResult.status === "fulfilled" && employeeResult.value) {
      const page = employeeResult.value as EmployeePage;
      setEmployees(page.content);
      if (!selectedEmployee && page.content.length) setSelectedEmployee(page.content[0].id);
    }
  }, [canManage, canReview, canSelfServe, selectedEmployee]);

  useEffect(() => { void load(); }, [load]);

  async function submitLeave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    setBusy(true); setError(""); setNotice("");
    try {
      await leaveApi.apply({ leaveTypeId: String(form.get("leaveTypeId")), startDate: String(form.get("startDate")), endDate: String(form.get("endDate")), reason: String(form.get("reason")) });
      setNotice("Leave request submitted. The requested days are reserved while it is pending.");
      element.reset();
      await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not submit leave request"); }
    finally { setBusy(false); }
  }

  async function cancelRequest(id: string) {
    setBusy(true); setError("");
    try { await leaveApi.cancel(id); setNotice("Pending request cancelled and reserved days released."); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not cancel request"); }
    finally { setBusy(false); }
  }

  async function decide(request: LeaveRequest, status: "APPROVED" | "REJECTED") {
    const note = window.prompt(status === "APPROVED" ? "Optional decision note" : "Reason for rejecting this request");
    if (note === null) return;
    if (status === "REJECTED" && !note.trim()) { setError("Add a reason when rejecting a leave request."); return; }
    setBusy(true); setError("");
    try { await leaveApi.decide(request.id, status, note); setNotice(`Leave request ${status.toLowerCase()}.`); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not review leave request"); }
    finally { setBusy(false); }
  }

  async function createType(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    setBusy(true); setError("");
    try {
      await leaveApi.createType({ code: String(form.get("code")), name: String(form.get("name")), description: String(form.get("description")), paid: form.get("paid") === "on" });
      element.reset(); setNotice("Leave type created."); await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not create leave type"); }
    finally { setBusy(false); }
  }

  async function saveBalance(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setError("");
    try {
      await leaveApi.setBalance(selectedEmployee, String(form.get("leaveTypeId")), currentYear, Number(form.get("entitledDays")));
      setNotice("Leave balance saved.");
      const updated = await leaveApi.employeeBalances(selectedEmployee, currentYear);
      setManagedBalances(updated);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not save leave balance"); }
    finally { setBusy(false); }
  }

  const [managedBalances, setManagedBalances] = useState<LeaveBalance[]>([]);
  useEffect(() => {
    if (canManage && selectedEmployee) leaveApi.employeeBalances(selectedEmployee, currentYear).then(setManagedBalances).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Could not load employee balances"));
  }, [canManage, selectedEmployee]);

  return <section className="leave-section">
    <div className="directory-heading"><div><div className="eyebrow">TIME AWAY</div><h1>Leave management</h1><p>Submit requests, track available days, and review team approvals.</p></div></div>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}

    {canSelfServe && <>
      <div className="leave-balance-grid">{balances.map((balance) => <article className="leave-balance-card" key={balance.leaveTypeId}><span>{balance.leaveTypeName}</span><strong>{balance.availableDays} <small>available</small></strong><div>{balance.usedDays} used · {balance.pendingDays} pending · {balance.entitledDays} total</div></article>)}{!balances.length && <p className="muted">No leave balances configured for {currentYear}.</p>}</div>
      <div className="leave-form-card"><h2>Request time off</h2><form className="leave-request-form" onSubmit={submitLeave}>
        <label>Leave type<select name="leaveTypeId" required defaultValue=""><option value="" disabled>Select leave type</option>{types.filter((type) => type.active).map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
        <label>From<input type="date" name="startDate" required min={new Date().toISOString().slice(0, 10)}/></label>
        <label>To<input type="date" name="endDate" required min={new Date().toISOString().slice(0, 10)}/></label>
        <label className="leave-reason">Reason<textarea name="reason" required maxLength={1000} rows={2} placeholder="Share a short explanation"/></label>
        <button className="submit" disabled={busy || !types.some((type) => type.active)}>{busy ? "Submitting…" : "Submit request"}</button>
      </form><p className="muted">Weekdays count toward leave. Pending requests reserve days until approved, rejected, or cancelled.</p></div>
      <h2 className="leave-subheading">My requests</h2>
      <div className="leave-request-list">{requests.map((request) => <article className="leave-request-card" key={request.id}><div><strong>{request.leaveTypeName}</strong><span>{request.startDate} – {request.endDate} · {request.days} days</span><p>{request.reason}</p>{request.decisionNote && <small>Decision note: {request.decisionNote}</small>}</div><div className="leave-request-actions"><LeaveStatusPill status={request.status}/>{request.status === "PENDING" && <button className="text-button" disabled={busy} onClick={() => void cancelRequest(request.id)}>Cancel</button>}</div></article>)}{!requests.length && <p className="muted">No leave requests yet.</p>}</div>
    </>}

    {canReview && <div className="leave-review-panel"><h2>{user.roles.includes("MANAGER") && !canManage ? "My team requests" : "Requests awaiting review"}</h2>
      {reviewRequests.map((request) => <article className="leave-request-card" key={request.id}><div><strong>{request.employeeName} <small>({request.employeeCode})</small></strong><span>{request.leaveTypeName} · {request.startDate} – {request.endDate} · {request.days} days</span><p>{request.reason}</p>{request.decisionNote && <small>Decision note: {request.decisionNote}</small>}</div>{request.status === "PENDING" ? <div className="leave-review-actions"><button className="quiet" disabled={busy} onClick={() => void decide(request, "APPROVED")}>Approve</button><button className="text-button" disabled={busy} onClick={() => void decide(request, "REJECTED")}>Reject</button></div> : <LeaveStatusPill status={request.status}/>}</article>)}{!reviewRequests.length && <p className="muted">No leave requests for your team.</p>}
    </div>}

    {canManage && <div className="leave-admin-grid"><div className="leave-form-card"><h2>Configure leave type</h2><form className="leave-type-form" onSubmit={createType}><label>Code<input name="code" required maxLength={30} pattern="[A-Za-z0-9_-]+" placeholder="PERSONAL"/></label><label>Name<input name="name" required maxLength={100} placeholder="Personal leave"/></label><label>Description<input name="description" maxLength={500}/></label><label className="leave-paid"><input type="checkbox" name="paid" defaultChecked/> Paid leave</label><button className="quiet" disabled={busy}>Add leave type</button></form></div>
      <div className="leave-form-card"><h2>Set employee balance · {currentYear}</h2><form className="leave-type-form" onSubmit={saveBalance}><label>Employee<select required value={selectedEmployee} onChange={(event) => setSelectedEmployee(event.target.value)}>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeCode}</option>)}</select></label><label>Leave type<select name="leaveTypeId" required defaultValue=""><option value="" disabled>Select leave type</option>{types.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label><label>Entitled weekdays<input type="number" name="entitledDays" min="0" step="0.5" required defaultValue="0"/></label><button className="quiet" disabled={busy || !selectedEmployee}>Save balance</button></form><div className="managed-balance-list">{managedBalances.map((balance) => <span key={balance.leaveTypeId}>{balance.leaveTypeName}: {balance.availableDays} available / {balance.entitledDays} entitled</span>)}</div></div></div>}
  </section>;
}
