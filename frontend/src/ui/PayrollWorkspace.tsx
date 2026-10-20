import { useCallback, useEffect, useState, type FormEvent } from "react";
import { payrollApi, peopleApi } from "../api";
import type { Employee, PayrollCorrection, PayrollPayslip, PayrollRun, PayrollStructure, User } from "../types";

type Props = { user: User };
type CompensationPart = { name: string; type: "ALLOWANCE" | "DEDUCTION"; amount: string };

function money(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
}

function CorrectionHistory({ payslipId, currency }: { payslipId: string; currency: string }) {
  const [entries, setEntries] = useState<PayrollCorrection[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  async function load() {
    try { setEntries(await payrollApi.corrections(payslipId)); setLoaded(true); setError(""); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not load correction history"); }
  }
  return <details onToggle={(event) => { if ((event.currentTarget as HTMLDetailsElement).open && !loaded) void load(); }}>
    <summary>Correction history</summary>{error && <p className="error">{error}</p>}
    {entries.map((entry) => <div className="payroll-correction-history" key={entry.id}><strong>{new Date(entry.correctedAt).toLocaleString()} · {entry.correctedBy}</strong><span>{money(entry.previousNetPay, currency)} → {money(entry.correctedNetPay, currency)}</span><span>{entry.reason}</span></div>)}
    {loaded && !entries.length && <p className="muted">No corrections recorded.</p>}
  </details>;
}

function CorrectionForm({ payslip, onCorrect }: { payslip: PayrollPayslip; onCorrect: () => void }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true); setError("");
    try {
      const rate = String(form.get("taxRatePercent"));
      await payrollApi.correct(payslip.id, { name: String(form.get("name")), type: String(form.get("type")) as "ALLOWANCE" | "DEDUCTION", amount: Number(form.get("amount")) }, String(form.get("reason")), rate === "" ? undefined : Number(rate));
      formElement.reset(); onCorrect();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not correct payslip"); }
    finally { setBusy(false); }
  }
  return <details className="payroll-correction-form"><summary>Correct draft payslip</summary><form onSubmit={submit}>
    <label>Adjustment name<input name="name" required maxLength={100} placeholder="Missed allowance"/></label>
    <label>Adjustment type<select name="type"><option value="ALLOWANCE">Allowance</option><option value="DEDUCTION">Deduction</option></select></label>
    <label>Amount<input type="number" name="amount" required min="0" step="0.01"/></label>
    <label>Tax rate override %<input type="number" name="taxRatePercent" min="0" max="100" step="0.01" placeholder="Keep current"/></label>
    <label className="payroll-correction-reason">Reason<input name="reason" required maxLength={1000} placeholder="Explain the correction"/></label>
    <button className="quiet" disabled={busy}>{busy ? "Saving…" : "Save correction"}</button>
  </form>{error && <p className="error" role="alert">{error}</p>}<CorrectionHistory payslipId={payslip.id} currency={payslip.currency}/></details>;
}

export function PayrollWorkspace({ user }: Props) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const canApprove = user.roles.includes("ADMIN");
  const canSelfServe = user.roles.some((role) => role === "EMPLOYEE" || role === "MANAGER");
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [structures, setStructures] = useState<PayrollStructure[]>([]);
  const [runs, setRuns] = useState<PayrollRun[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [selectedRunId, setSelectedRunId] = useState("");
  const [payslips, setPayslips] = useState<PayrollPayslip[]>([]);
  const [personalSlips, setPersonalSlips] = useState<PayrollPayslip[]>([]);
  const [month, setMonth] = useState(() => {
    const date = new Date();
    date.setMonth(date.getMonth() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
  });
  const [currency, setCurrency] = useState("USD");
  const [baseSalary, setBaseSalary] = useState("");
  const [taxRatePercent, setTaxRatePercent] = useState("0");
  const [effectiveFrom, setEffectiveFrom] = useState(`${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-01`);
  const [components, setComponents] = useState<CompensationPart[]>([{ name: "", type: "ALLOWANCE", amount: "" }]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError("");
    const [structureResult, runResult, employeeResult, personalResult] = await Promise.allSettled([
      canManage ? payrollApi.structures() : Promise.resolve([]),
      canManage ? payrollApi.runs() : Promise.resolve([]),
      canManage ? peopleApi.employees(new URLSearchParams({ page: "0", size: "100", status: "ACTIVE" })) : Promise.resolve(null),
      canSelfServe ? payrollApi.ownPayslips() : Promise.resolve([]),
    ]);
    const results = [structureResult, runResult, employeeResult, personalResult];
    const failed = results.find((result) => result.status === "rejected");
    if (failed?.status === "rejected") setError(failed.reason instanceof Error ? failed.reason.message : "Could not load payroll data");
    if (structureResult.status === "fulfilled") setStructures(structureResult.value);
    if (runResult.status === "fulfilled") {
      setRuns(runResult.value);
      if (!selectedRunId && runResult.value.length) setSelectedRunId(runResult.value[0].id);
    }
    if (employeeResult.status === "fulfilled" && employeeResult.value) {
      setEmployees(employeeResult.value.content);
      if (!selectedEmployee && employeeResult.value.content.length) setSelectedEmployee(employeeResult.value.content[0].id);
    }
    if (personalResult.status === "fulfilled") setPersonalSlips(personalResult.value);
  }, [canManage, canSelfServe, selectedEmployee, selectedRunId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!canManage || !selectedRunId) { setPayslips([]); return; }
    payrollApi.runPayslips(selectedRunId).then((result) => setPayslips(result.content))
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Could not load payroll slips"));
  }, [canManage, selectedRunId]);

  const selectedStructure = structures.find((structure) => structure.employeeId === selectedEmployee);
  useEffect(() => {
    if (!selectedStructure) {
      setBaseSalary(""); setTaxRatePercent("0"); setCurrency("USD");
      setComponents([{ name: "", type: "ALLOWANCE", amount: "" }]);
      return;
    }
    setBaseSalary(String(selectedStructure.baseSalary));
    setTaxRatePercent(String(selectedStructure.taxRatePercent));
    setCurrency(selectedStructure.currency);
    setEffectiveFrom(selectedStructure.effectiveFrom);
    setComponents(selectedStructure.components.map((part) => ({ ...part, amount: String(part.amount) })));
  }, [selectedEmployee, selectedStructure?.id]);

  async function saveStructure(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const activeComponents = components.filter((component) => component.name.trim() && component.amount !== "");
    setBusy(true); setError(""); setNotice("");
    try {
      await payrollApi.setStructure(selectedEmployee, {
        currency: currency.trim().toUpperCase(), baseSalary: Number(baseSalary), taxRatePercent: Number(taxRatePercent), effectiveFrom, active: true,
        components: activeComponents.map((component) => ({ name: component.name, type: component.type, amount: Number(component.amount) })),
      });
      setNotice("Salary structure saved. It will be copied into eligible payroll runs."); await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not save salary structure"); }
    finally { setBusy(false); }
  }

  async function generateRun(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const run = await payrollApi.generate(month);
      setSelectedRunId(run.id); setNotice(`Draft payroll generated for ${month} with ${run.payslipCount} payslips.`); await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not generate payroll"); }
    finally { setBusy(false); }
  }

  async function updateRun(action: "approve" | "pay", run: PayrollRun) {
    setBusy(true); setError(""); setNotice("");
    try {
      const updated = action === "approve" ? await payrollApi.approve(run.id) : await payrollApi.markPaid(run.id);
      setNotice(`Payroll ${updated.month} is ${updated.status.toLowerCase()}.`); await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not update payroll status"); }
    finally { setBusy(false); }
  }

  async function exportRun(runId: string) {
    setBusy(true); setError("");
    try {
      const csv = await payrollApi.exportCsv(runId);
      const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
      const link = document.createElement("a"); link.href = url; link.download = `payroll-${runId}.csv`; link.click(); URL.revokeObjectURL(url);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not export payroll"); }
    finally { setBusy(false); }
  }

  return <section className="payroll-section">
    <div className="directory-heading"><div><div className="eyebrow">COMPENSATION</div><h1>Payroll</h1><p>Manage salary inputs, review monthly runs, and access payslips.</p></div></div>
    <p className="payroll-policy-note">Calculation model: gross = base salary + allowances; tax = configured flat rate × gross; net = gross − deductions − tax. Configure rates for your own demonstration policy. This generic formula does not implement any jurisdiction’s statutory tax, benefits, or compliance rules.</p>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}

    {canManage && <>
      <div className="payroll-config-card"><h2>Salary structure</h2>
        {!employees.length && <p className="muted">Create an active employee profile before configuring salary.</p>}
        <form className="payroll-structure-form" onSubmit={saveStructure}>
          <label>Employee<select required value={selectedEmployee} onChange={(event) => setSelectedEmployee(event.target.value)}>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeCode}</option>)}</select></label>
          <label>Currency code<input required value={currency} maxLength={3} pattern="[A-Za-z]{3}" onChange={(event) => setCurrency(event.target.value)} placeholder="USD"/></label>
          <label>Monthly base salary<input type="number" required min="0" step="0.01" value={baseSalary} onChange={(event) => setBaseSalary(event.target.value)}/></label>
          <label>Flat tax rate (%)<input type="number" required min="0" max="100" step="0.01" value={taxRatePercent} onChange={(event) => setTaxRatePercent(event.target.value)}/></label>
          <label>Effective from<input type="date" required value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)}/></label>
          <div className="payroll-component-editor"><strong>Monthly allowances and deductions</strong>
            {components.map((component, index) => <div className="payroll-component-row" key={index}>
              <input aria-label="Component name" placeholder="Housing allowance" value={component.name} onChange={(event) => setComponents((current) => current.map((part, i) => i === index ? { ...part, name: event.target.value } : part))}/>
              <select aria-label="Component type" value={component.type} onChange={(event) => setComponents((current) => current.map((part, i) => i === index ? { ...part, type: event.target.value as CompensationPart["type"] } : part))}><option value="ALLOWANCE">Allowance</option><option value="DEDUCTION">Deduction</option></select>
              <input aria-label="Component amount" type="number" min="0" step="0.01" placeholder="Amount" value={component.amount} onChange={(event) => setComponents((current) => current.map((part, i) => i === index ? { ...part, amount: event.target.value } : part))}/>
              <button type="button" className="text-button" onClick={() => setComponents((current) => current.filter((_, i) => i !== index))}>Remove</button>
            </div>)}
            <button type="button" className="quiet" onClick={() => setComponents((current) => [...current, { name: "", type: "ALLOWANCE", amount: "" }])}>Add component</button>
          </div>
          <button className="submit" disabled={busy || !selectedEmployee}>{busy ? "Saving…" : "Save salary structure"}</button>
        </form>
      </div>

      <div className="payroll-runs-card"><div className="payroll-runs-heading"><div><h2>Monthly payroll runs</h2><p>Each calendar month can have one payroll run.</p></div><form onSubmit={generateRun}><label>Payroll month<input type="month" required value={month} max={`${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}`} onChange={(event) => setMonth(event.target.value)}/></label><button className="quiet" disabled={busy}>Generate draft</button></form></div>
        <div className="payroll-run-list">{runs.map((run) => <button key={run.id} className={`payroll-run-chip ${run.id === selectedRunId ? "selected" : ""}`} onClick={() => setSelectedRunId(run.id)}><strong>{run.month}</strong><span className={`pill ${run.status.toLowerCase()}`}>{run.status}</span><small>{run.payslipCount} payslips</small></button>)}{!runs.length && <p className="muted">No payroll runs yet.</p>}</div>
        {selectedRunId && <div className="payroll-run-actions"><button className="quiet" disabled={busy} onClick={() => void exportRun(selectedRunId)}>Export CSV</button>{canApprove && runs.find((run) => run.id === selectedRunId)?.status === "DRAFT" && <button className="quiet" disabled={busy} onClick={() => void updateRun("approve", runs.find((run) => run.id === selectedRunId)!)}>Approve run</button>}{canApprove && runs.find((run) => run.id === selectedRunId)?.status === "APPROVED" && <button className="submit" disabled={busy} onClick={() => void updateRun("pay", runs.find((run) => run.id === selectedRunId)!)}>Mark paid</button>}</div>}
      </div>
      {selectedRunId && <div className="employee-table-wrap"><table><thead><tr><th>Employee</th><th>Base</th><th>Allowances</th><th>Deductions</th><th>Gross</th><th>Tax</th><th>Net pay</th><th>Corrections</th></tr></thead><tbody>
        {payslips.map((slip) => <tr key={slip.id}><td><strong>{slip.employeeName}</strong><span>{slip.employeeCode}</span></td><td>{money(slip.baseSalary, slip.currency)}</td><td>{money(slip.allowanceTotal, slip.currency)}</td><td>{money(slip.deductionTotal, slip.currency)}</td><td>{money(slip.grossPay, slip.currency)}</td><td>{money(slip.taxAmount, slip.currency)} ({slip.taxRatePercent}%)</td><td><strong>{money(slip.netPay, slip.currency)}</strong></td><td>{slip.runStatus === "DRAFT" ? <CorrectionForm payslip={slip} onCorrect={() => { void load(); void payrollApi.runPayslips(selectedRunId).then((result) => setPayslips(result.content)); }}/> : <span className="muted">Locked</span>}</td></tr>)}
        {!payslips.length && <tr><td colSpan={8} className="empty-state">No payslips in this run.</td></tr>}
      </tbody></table></div>}
    </>}

    {canSelfServe && <div className="payroll-employee-card"><h2>My payslips</h2><div className="employee-table-wrap"><table><thead><tr><th>Month</th><th>Base</th><th>Allowances</th><th>Deductions</th><th>Tax</th><th>Net pay</th><th>Status</th></tr></thead><tbody>
      {personalSlips.map((slip) => <tr key={slip.id}><td>{slip.month}</td><td>{money(slip.baseSalary, slip.currency)}</td><td>{money(slip.allowanceTotal, slip.currency)}</td><td>{money(slip.deductionTotal, slip.currency)}</td><td>{money(slip.taxAmount, slip.currency)}</td><td><strong>{money(slip.netPay, slip.currency)}</strong></td><td><span className={`pill ${slip.runStatus.toLowerCase()}`}>{slip.runStatus}</span></td></tr>)}
      {!personalSlips.length && <tr><td colSpan={7} className="empty-state">No payslips available yet.</td></tr>}
    </tbody></table></div></div>}
  </section>;
}
