import { useCallback, useEffect, useState, type FormEvent } from "react";
import { performanceApi, peopleApi } from "../api";
import type { Employee, EmployeePage, PerformanceGoal, PerformanceReview, ReviewCycle, User } from "../types";

export function PerformanceWorkspace({ user }: { user: User }) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const isManager = user.roles.includes("MANAGER");
  const canSelfServe = user.roles.some((role) => role === "EMPLOYEE" || role === "MANAGER");
  const [goals, setGoals] = useState<PerformanceGoal[]>([]);
  const [ownReviews, setOwnReviews] = useState<PerformanceReview[]>([]);
  const [managedReviews, setManagedReviews] = useState<PerformanceReview[]>([]);
  const [cycles, setCycles] = useState<ReviewCycle[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [teamGoals, setTeamGoals] = useState<PerformanceGoal[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError("");
    if (canSelfServe) {
      const [goalResult, reviewResult] = await Promise.all([
        performanceApi.ownGoals().then((value) => ({ value }), (reason: unknown) => ({ reason })),
        performanceApi.ownReviews().then((value) => ({ value }), (reason: unknown) => ({ reason })),
      ]);
      if ("value" in goalResult) setGoals(goalResult.value);
      else setError(goalResult.reason instanceof Error ? goalResult.reason.message : "Could not load goals");
      if ("value" in reviewResult) setOwnReviews(reviewResult.value);
      else setError(reviewResult.reason instanceof Error ? reviewResult.reason.message : "Could not load review history");
    }
    if (canManage || isManager) {
      const managed = await Promise.allSettled([canManage ? performanceApi.allReviews() : performanceApi.teamReviews(), canManage ? performanceApi.cycles() : Promise.resolve([])]);
      if (managed[0].status === "fulfilled") setManagedReviews(managed[0].value);
      if (managed[1].status === "fulfilled") setCycles(managed[1].value);
      const failedManaged = managed.find((result) => result.status === "rejected");
      if (failedManaged?.status === "rejected") setError(failedManaged.reason instanceof Error ? failedManaged.reason.message : "Could not load managed reviews");
      const people = await peopleApi.employees(new URLSearchParams({ page: "0", size: "100", status: "ACTIVE" }));
      setEmployees(people.content);
      if (!selectedEmployee && people.content.length) setSelectedEmployee(people.content[0].id);
    }
  }, [canManage, canSelfServe, isManager, selectedEmployee]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (selectedEmployee && (canManage || isManager)) performanceApi.employeeGoals(selectedEmployee).then(setTeamGoals).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Could not load employee goals"));
  }, [canManage, isManager, selectedEmployee]);

  async function createGoal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const element = event.currentTarget; const form = new FormData(element); setBusy(true); setError("");
    try { await performanceApi.createGoal({ title: String(form.get("title")), description: String(form.get("description")), keyResult: String(form.get("keyResult")), targetDate: String(form.get("targetDate")) }); setNotice("Goal created."); element.reset(); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not create goal"); } finally { setBusy(false); }
  }

  async function assignGoal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const element = event.currentTarget; const form = new FormData(element); setBusy(true); setError("");
    try { await performanceApi.createEmployeeGoal(selectedEmployee, { title: String(form.get("title")), description: String(form.get("description")), keyResult: String(form.get("keyResult")), targetDate: String(form.get("targetDate")) }); setNotice("Goal assigned."); element.reset(); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not assign goal"); } finally { setBusy(false); }
  }

  async function completeGoal(goal: PerformanceGoal) {
    setBusy(true); setError("");
    try { await performanceApi.updateGoal(goal.id, { title: goal.title, description: goal.description, keyResult: goal.keyResult, targetDate: goal.targetDate, status: "COMPLETED" }); setNotice("Goal marked complete."); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not update goal"); } finally { setBusy(false); }
  }

  async function createCycle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const element = event.currentTarget; const form = new FormData(element); setBusy(true); setError("");
    try { await performanceApi.createCycle({ name: String(form.get("cycleName")), startDate: String(form.get("startDate")), endDate: String(form.get("endDate")) }); setNotice("Review cycle opened and review records created for active employees."); element.reset(); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not create review cycle"); } finally { setBusy(false); }
  }

  async function selfAssess(review: PerformanceReview) {
    const text = window.prompt("Write your self-assessment (include progress against goals and key results):");
    if (text === null) return; setBusy(true); setError("");
    try { await performanceApi.selfAssess(review.id, text); setNotice("Self-assessment submitted."); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not submit self-assessment"); } finally { setBusy(false); }
  }

  async function managerReview(review: PerformanceReview) {
    const ratingText = window.prompt("Manager rating from 1 to 5:"); if (ratingText === null) return;
    const rating = Number(ratingText); if (!Number.isInteger(rating) || rating < 1 || rating > 5) { setError("Rating must be a whole number from 1 to 5."); return; }
    const feedback = window.prompt("Manager feedback:"); if (feedback === null) return; setBusy(true); setError("");
    try { await performanceApi.managerReview(review.id, rating, feedback); setNotice("Manager review completed."); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not complete review"); } finally { setBusy(false); }
  }

  async function closeCycle(cycle: ReviewCycle) {
    setBusy(true); setError("");
    try { await performanceApi.closeCycle(cycle.id); setNotice("Review cycle closed."); await load(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not close review cycle"); } finally { setBusy(false); }
  }

  return <section className="performance-section">
    <div className="directory-heading"><div><div className="eyebrow">GROWTH & DEVELOPMENT</div><h1>Performance</h1><p>Set measurable goals and complete review cycles with your manager.</p></div></div>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {canSelfServe && <div className="performance-card"><h2>My goals</h2><form className="performance-goal-form" onSubmit={createGoal}><label>Goal title<input name="title" required maxLength={160} placeholder="Improve customer response time"/></label><label>Description<input name="description" required maxLength={2000} placeholder="What you plan to accomplish"/></label><label>Measurable key result<input name="keyResult" required maxLength={500} placeholder="Reduce average response time by 20%"/></label><label>Target date<input type="date" name="targetDate" required min={new Date().toISOString().slice(0,10)}/></label><button className="quiet" disabled={busy}>Add goal</button></form>
      <div className="performance-list">{goals.map((goal) => <article className="performance-item" key={goal.id}><div><strong>{goal.title}</strong><p>{goal.description}</p><small>Key result: {goal.keyResult} · Target {goal.targetDate}</small></div><div><span className={`pill ${goal.status.toLowerCase().replace("_","-")}`}>{goal.status.replaceAll("_"," ")}</span>{goal.status === "IN_PROGRESS" && <button className="text-button" disabled={busy} onClick={() => void completeGoal(goal)}>Mark complete</button>}</div></article>)}{!goals.length && <p className="muted">No goals yet.</p>}</div>
    </div>}
    {canManage && <div className="performance-card"><h2>Review cycles</h2><form className="performance-cycle-form" onSubmit={createCycle}><label>Cycle name<input name="cycleName" required maxLength={160} placeholder="2026 annual review"/></label><label>Starts<input type="date" name="startDate" required/></label><label>Deadline<input type="date" name="endDate" required/></label><button className="quiet" disabled={busy}>Open cycle</button></form><div className="performance-list">{cycles.map((cycle) => <article className="performance-item" key={cycle.id}><div><strong>{cycle.name}</strong><p>{cycle.startDate} – {cycle.endDate} · {cycle.reviewCount} employees</p></div><div><span className={`pill ${cycle.status.toLowerCase()}`}>{cycle.status}</span>{cycle.status === "OPEN" && <button className="text-button" disabled={busy} onClick={() => void closeCycle(cycle)}>Close cycle</button>}</div></article>)}</div></div>}
    {(canManage || isManager) && <div className="performance-card"><h2>Team goals</h2><label>Employee<select value={selectedEmployee} onChange={(event) => setSelectedEmployee(event.target.value)}>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeCode}</option>)}</select></label><form className="performance-goal-form" onSubmit={assignGoal}><label>Goal title<input name="title" required maxLength={160}/></label><label>Description<input name="description" required maxLength={2000}/></label><label>Measurable key result<input name="keyResult" required maxLength={500}/></label><label>Target date<input type="date" name="targetDate" required min={new Date().toISOString().slice(0,10)}/></label><button className="quiet" disabled={busy || !selectedEmployee}>Assign goal</button></form><div className="performance-list">{teamGoals.map((goal) => <article className="performance-item" key={goal.id}><div><strong>{goal.title}</strong><p>{goal.employeeName} · {goal.keyResult} · target {goal.targetDate}</p></div><span className={`pill ${goal.status.toLowerCase().replace("_","-")}`}>{goal.status.replaceAll("_"," ")}</span></article>)}{!teamGoals.length && <p className="muted">No goals assigned to this employee yet.</p>}</div></div>}
    {!!ownReviews.length && <div className="performance-card"><h2>My review history</h2><div className="performance-list">{ownReviews.map((review) => <article className="performance-item" key={review.id}><div><strong>{review.cycleName}</strong><p>Deadline {review.cycleEnd} · {review.selfSubmittedAt ? "Self-assessment submitted" : "Self-assessment pending"}</p>{review.selfAssessment && <p>{review.selfAssessment}</p>}{review.managerFeedback && <p><b>Manager rating: {review.managerRating}/5</b><br/>{review.managerFeedback}</p>}</div>{!review.selfSubmittedAt && review.cycleStatus === "OPEN" && <button className="quiet" disabled={busy} onClick={() => void selfAssess(review)}>Write self-assessment</button>}</article>)}</div></div>}
    {(isManager || canManage) && <div className="performance-card"><h2>{isManager && !canManage ? "My team reviews" : "Review scorecards"}</h2><div className="performance-list">{managedReviews.map((review) => <article className="performance-item" key={review.id}><div><strong>{review.employeeName} · {review.cycleName}</strong><p>{review.selfAssessment ? `Self-assessment: ${review.selfAssessment}` : "Waiting for employee self-assessment"}</p>{review.managerFeedback && <p>Rating {review.managerRating}/5 · {review.managerFeedback}</p>}</div>{isManager && review.selfSubmittedAt && !review.completedAt && review.cycleStatus === "OPEN" && <button className="quiet" disabled={busy} onClick={() => void managerReview(review)}>Add rating & feedback</button>}</article>)}{!managedReviews.length && <p className="muted">No team reviews available.</p>}</div></div>}
  </section>;
}
