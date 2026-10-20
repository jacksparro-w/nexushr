import { useEffect, useMemo, useState } from "react";
import { attendanceApi, leaveApi, payrollApi, performanceApi, peopleApi } from "../api";
import type { AttendanceSummary, LeaveBalance, LeaveRequest, PayrollPayslip, PayrollRun, PerformanceGoal, PerformanceReview, User } from "../types";

type DashboardData = {
  attendance?: AttendanceSummary;
  balances?: LeaveBalance[];
  leave?: LeaveRequest[];
  payslips?: PayrollPayslip[];
  goals?: PerformanceGoal[];
  reviews?: PerformanceReview[];
  runs?: PayrollRun[];
  payroll?: { netTotal: number; currency: string } | null;
  headcount?: { active: number; inactive: number; terminated: number };
};

const currentMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
};

function csvDownload(rows: [string, string | number][], filename: string) {
  const csv = rows.map(([label, value]) => `${JSON.stringify(label)},${JSON.stringify(value)}`).join("\r\n");
  const url = URL.createObjectURL(new Blob([`Metric,Value\r\n${csv}`], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function DashboardWorkspace({ user }: { user: User }) {
  const hr = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const manager = !hr && user.roles.includes("MANAGER");
  const [month, setMonth] = useState(currentMonth);
  const [data, setData] = useState<DashboardData>({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    const jobs: Promise<unknown>[] = hr
      ? [
          attendanceApi.managedSummary(month),
          leaveApi.requests(),
          payrollApi.runs().then(async (runs) => {
            const run = runs.find((item) => item.month === month);
            if (!run) return { runs, payroll: null };
            const firstPage = await payrollApi.runPayslips(run.id, 0);
            const slips = [...firstPage.content];
            for (let page = 1; page < firstPage.totalPages; page++) {
              slips.push(...(await payrollApi.runPayslips(run.id, page)).content);
            }
            const currencies = [...new Set(slips.map((slip) => slip.currency))];
            return { runs, payroll: currencies.length === 1 ? { netTotal: slips.reduce((sum, slip) => sum + slip.netPay, 0), currency: currencies[0] } : null };
          }),
          ...(["ACTIVE", "INACTIVE", "TERMINATED"] as const).map((status) =>
            peopleApi.employees(new URLSearchParams({ page: "0", size: "1", status })),
          ),
        ]
      : manager
        ? [attendanceApi.teamSummary(month), leaveApi.requests(), performanceApi.teamReviews()]
        : [
            attendanceApi.summary(month),
            leaveApi.ownBalances(Number(month.slice(0, 4))),
            leaveApi.ownRequests(),
            payrollApi.ownPayslips(),
            performanceApi.ownGoals(),
          ];
    Promise.allSettled(jobs).then((results) => {
      if (!active) return;
      const failures = results.flatMap((result) => result.status === "rejected" ? [result.reason instanceof Error ? result.reason.message : "A dashboard data request failed"] : []);
      setError(failures.join(" "));
      const values = results.map((result) => result.status === "fulfilled" ? result.value : undefined);
      if (hr) {
        const attendance = values[0] as AttendanceSummary | undefined;
        const leave = values[1] as LeaveRequest[] | undefined;
        const payrollResult = values[2] as { runs: PayrollRun[]; payroll: DashboardData["payroll"] } | undefined;
        const activePage = values[3] as { totalElements: number } | undefined;
        const inactivePage = values[4] as { totalElements: number } | undefined;
        const terminatedPage = values[5] as { totalElements: number } | undefined;
        setData({ attendance, leave, runs: payrollResult?.runs, payroll: payrollResult?.payroll, headcount: activePage && inactivePage && terminatedPage ? { active: activePage.totalElements, inactive: inactivePage.totalElements, terminated: terminatedPage.totalElements } : undefined });
      } else if (manager) {
        setData({ attendance: values[0] as AttendanceSummary | undefined, leave: values[1] as LeaveRequest[] | undefined, reviews: values[2] as PerformanceReview[] | undefined });
      } else {
        setData({ attendance: values[0] as AttendanceSummary | undefined, balances: values[1] as LeaveBalance[] | undefined, leave: values[2] as LeaveRequest[] | undefined, payslips: values[3] as PayrollPayslip[] | undefined, goals: values[4] as PerformanceGoal[] | undefined });
      }
      setLoading(false);
    });
    return () => { active = false; };
  }, [hr, manager, month, user.id]);

  const stats = useMemo(() => {
    const pendingLeave = data.leave?.filter((request) => request.status === "PENDING").length;
    const completedReviews = data.reviews?.filter((review) => review.completedAt).length;
    const openGoals = data.goals?.filter((goal) => goal.status === "IN_PROGRESS").length;
    const businessDays = (() => {
      const [year, m] = month.split("-").map(Number);
      const end = new Date(year, m, 0).getDate();
      const daysPassed = month === currentMonth() ? new Date().getDate() : month < currentMonth() ? end : 0;
      let weekdays = 0;
      for (let day = 1; day <= daysPassed; day++) {
        const weekday = new Date(year, m - 1, day).getDay();
        if (weekday !== 0 && weekday !== 6) weekdays++;
      }
      return weekdays;
    })();
    const attendanceRate = hr && data.headcount && data.attendance && data.headcount.active && businessDays
      ? Math.min(100, Math.round((data.attendance.daysWorked / (data.headcount.active * businessDays)) * 100))
      : undefined;
    return { pendingLeave, completedReviews, openGoals, attendanceRate };
  }, [data, hr, month]);

  const cards: [string, string | number][] = hr
    ? [["Active employees", data.headcount?.active ?? "—"], ["Attendance rate", stats.attendanceRate === undefined ? "—" : `${stats.attendanceRate}%`], ["Pending leave", stats.pendingLeave ?? "—"], ["Payroll net total", data.payroll ? `${data.payroll.currency} ${data.payroll.netTotal.toLocaleString()}` : "—"]]
    : manager
      ? [["Team attendance records", data.attendance?.daysWorked ?? "—"], ["Late records", data.attendance?.lateDays ?? "—"], ["Leave awaiting decision", stats.pendingLeave ?? "—"], ["Reviews completed", stats.completedReviews ?? "—"]]
      : [["Days attended", data.attendance?.daysWorked ?? "—"], ["Late days", data.attendance?.lateDays ?? "—"], ["Pending leave", stats.pendingLeave ?? "—"], ["Active goals", stats.openGoals ?? "—"]];
  const chart = hr
    ? [["Active", data.headcount?.active ?? 0], ["Inactive", data.headcount?.inactive ?? 0], ["Terminated", data.headcount?.terminated ?? 0]] as [string, number][]
    : manager
      ? [["Pending leave", data.leave?.filter((request) => request.status === "PENDING").length ?? 0], ["Approved", data.leave?.filter((request) => request.status === "APPROVED").length ?? 0], ["Reviews waiting", data.reviews?.filter((review) => !review.completedAt).length ?? 0]] as [string, number][]
      : [["In progress", data.goals?.filter((goal) => goal.status === "IN_PROGRESS").length ?? 0], ["Completed", data.goals?.filter((goal) => goal.status === "COMPLETED").length ?? 0], ["Cancelled", data.goals?.filter((goal) => goal.status === "CANCELLED").length ?? 0]] as [string, number][];
  const chartMax = Math.max(1, ...chart.map(([, value]) => value));
  const title = hr ? "HR overview" : manager ? "Manager overview" : "My overview";
  const csvRows = [...cards, ...(hr ? [["Inactive employees", data.headcount?.inactive ?? "—"] as [string, string | number], ["Terminated employees", data.headcount?.terminated ?? "—"] as [string, string | number], ["Payroll runs", data.runs?.length ?? "—"] as [string, string | number]] : [])];

  return <section className="dashboard-section" aria-labelledby="dashboard-title">
    <div className="directory-heading"><div><div className="eyebrow">WORKFORCE OVERVIEW</div><h1 id="dashboard-title">{title}</h1><p>{hr ? "Organization metrics from current HR, attendance, leave, and payroll records." : manager ? "Your direct reports’ attendance, leave queue, and review progress." : "Your attendance, time off, payslips, and goals in one place."}</p></div><div className="dashboard-tools"><label>Month<input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label><button className="quiet" disabled={loading} onClick={() => csvDownload(csvRows, `nexushr-dashboard-${month}.csv`)}>Export CSV</button></div></div>
    {loading && <p className="muted" role="status">Loading dashboard…</p>}
    {error && <p className="error" role="alert">Some dashboard data could not be loaded: {error}</p>}
    <div className="dashboard-cards">{cards.map(([label, value]) => <article className="dashboard-card" key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
    <div className="dashboard-panel"><h2>{hr ? "Headcount by status" : manager ? "Team activity" : "Goal progress"}</h2><div className="dashboard-chart" role="img" aria-label={`${hr ? "Headcount" : manager ? "Team activity" : "Goal progress"} chart: ${chart.map(([label, value]) => `${label} ${value}`).join(", ")}`}>
      {chart.map(([label, value]) => <div className="dashboard-bar-row" key={label}><span>{label}</span><div className="dashboard-bar-track"><div className="dashboard-bar" style={{ width: `${Math.round(value / chartMax * 100)}%` }} /></div><strong>{value}</strong></div>)}
    </div></div>
    {!loading && !error && !data.attendance && !data.leave && !data.headcount && !data.goals && <p className="muted">No dashboard records are available for this month.</p>}
    {!loading && !!data.balances?.length && <div className="dashboard-panel"><h2>Leave balances</h2><div className="dashboard-table">{data.balances.map((balance) => <div key={balance.leaveTypeId}><span>{balance.leaveTypeName}</span><span>{balance.availableDays} available · {balance.pendingDays} pending</span></div>)}</div></div>}
    {!loading && !!data.payslips?.length && <div className="dashboard-panel"><h2>Recent payslips</h2><div className="dashboard-table">{data.payslips.slice(0, 5).map((slip) => <div key={slip.id}><span>{slip.month}</span><span>{slip.currency} {slip.netPay.toLocaleString()}</span></div>)}</div></div>}
    {!loading && !!data.leave?.length && <div className="dashboard-panel"><h2>{manager ? "Team leave requests" : hr ? "Recent leave requests" : "My leave requests"}</h2><div className="dashboard-table">{data.leave.slice(0, 5).map((request) => <div key={request.id}><span>{request.employeeName} · {request.leaveTypeName} · {request.startDate}</span><span className={`pill ${request.status.toLowerCase()}`}>{request.status}</span></div>)}</div></div>}
    {!loading && !!data.reviews?.length && <div className="dashboard-panel"><h2>Team review queue</h2><div className="dashboard-table">{data.reviews.slice(0, 5).map((review) => <div key={review.id}><span>{review.employeeName} · {review.cycleName}</span><span>{review.completedAt ? "Completed" : review.selfSubmittedAt ? "Needs manager review" : "Awaiting self-assessment"}</span></div>)}</div></div>}
  </section>;
}
