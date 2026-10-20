import type { Announcement, AppNotification, AttendanceAudit, AttendancePage, AttendanceRecord, AttendanceSummary, AuthResponse, Department, Employee, EmployeeDocument, EmployeePage, LeaveBalance, LeaveRequest, LeaveStatus, LeaveType, PayrollCorrection, PayrollPage, PayrollPayslip, PayrollRun, PayrollStructure, PerformanceGoal, PerformanceReview, ReviewCycle, Role } from "./types";

const API = import.meta.env.VITE_API_URL || (import.meta.env.DEV ? "http://localhost:8090" : window.location.origin);
let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse> | null = null;

function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API}/api/auth/refresh`, {
      method: "POST",
      credentials: "include",
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Session expired. Please sign in again.");
        return (await response.json()) as AuthResponse;
      })
      .then((tokens) => {
        accessToken = tokens.accessToken;
        return tokens;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

function expireSession() {
  accessToken = null;
  window.dispatchEvent(new Event("nexushr:session-expired"));
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
  parseText = false,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  let refreshedAccessToken = false;
  let response = await fetch(`${API}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });
  if (
    response.status === 401 &&
    retry &&
    path !== "/api/auth/login" &&
    path !== "/api/auth/refresh"
  ) {
    let tokens: AuthResponse;
    try {
      tokens = await refreshSession();
    } catch {
      expireSession();
      throw new Error("Session expired. Please sign in again.");
    }
    headers.set("Authorization", `Bearer ${tokens.accessToken}`);
    refreshedAccessToken = true;
    response = await fetch(`${API}${path}`, {
      ...init,
      headers,
      credentials: "include",
    });
  }
  if (response.status === 401 && refreshedAccessToken) {
    throw new Error("The API rejected this request after refreshing your session (401).");
  }
  if (!response.ok) {
    if (response.status === 401 && path === "/api/auth/login") {
      throw new Error("Email or password is incorrect, or this account has not been created yet.");
    }
    const body = (await response.json().catch(() => null)) as {
      message?: string;
      detail?: string;
    } | null;
    throw new Error(
      body?.detail ?? body?.message ?? `Request failed (${response.status})`,
    );
  }
  if (response.status === 204) return undefined as T;
  return parseText ? (await response.text() as T) : (response.json() as Promise<T>);
}

export const authApi = {
  login: async (email: string, password: string) => {
    accessToken = null;
    const result = await request<AuthResponse>(
      "/api/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      false,
    );
    accessToken = result.accessToken;
    return result.user;
  },
  restore: async () => {
    const result = await refreshSession();
    return result.user;
  },
  logout: async () => {
    try {
      await request("/api/auth/logout", { method: "POST" }, false);
    } finally {
      accessToken = null;
    }
  },
  createAccount: (email: string, password: string, roles: Role[] = ["EMPLOYEE"]) =>
    request<{ id: string; email: string; roles: string[] }>("/api/auth/accounts", {
      method: "POST",
      body: JSON.stringify({ email, password, roles }),
    }),
};

export const peopleApi = {
  departments: () => request<Department[]>("/api/departments"),
  employees: (params: URLSearchParams) => request<EmployeePage>(`/api/employees?${params}`),
  me: () => request<Employee>("/api/employees/me"),
  createEmployee: (employee: Omit<Employee, "id" | "departmentName" | "managerName">) =>
    request<Employee>("/api/employees", { method: "POST", body: JSON.stringify(employee) }),
  updateEmployee: (id: string, employee: Omit<Employee, "id" | "departmentName" | "managerName">) =>
    request<Employee>(`/api/employees/${id}`, { method: "PUT", body: JSON.stringify(employee) }),
  deleteEmployee: (id: string) => request<void>(`/api/employees/${id}`, { method: "DELETE" }),
  createDepartment: (department: { code: string; name: string; description: string }) =>
    request<Department>("/api/departments", { method: "POST", body: JSON.stringify(department) }),
  documents: (id: string) => request<EmployeeDocument[]>(`/api/employees/${id}/documents`),
  addDocument: (id: string, document: Omit<EmployeeDocument, "id" | "uploadedAt">) =>
    request<EmployeeDocument>(`/api/employees/${id}/documents`, { method: "POST", body: JSON.stringify(document) }),
  deleteDocument: (employeeId: string, documentId: string) =>
    request<void>(`/api/employees/${employeeId}/documents/${documentId}`, { method: "DELETE" }),
};

export const attendanceApi = {
  clockIn: () => request<AttendanceRecord>("/api/attendance/clock-in", { method: "POST" }),
  clockOut: () => request<AttendanceRecord>("/api/attendance/clock-out", { method: "POST" }),
  ownHistory: (from: string, to: string) =>
    request<AttendanceRecord[]>(`/api/attendance/me?${new URLSearchParams({ from, to })}`),
  summary: (month: string) =>
    request<AttendanceSummary>(`/api/attendance/me/summary?${new URLSearchParams({ month })}`),
  current: () => request<AttendanceRecord | null>("/api/attendance/me/current"),
  managedSummary: (month: string) =>
    request<AttendanceSummary>(`/api/attendance/summary?${new URLSearchParams({ month })}`),
  teamSummary: (month: string) =>
    request<AttendanceSummary>(`/api/attendance/team/summary?${new URLSearchParams({ month })}`),
  managedHistory: (from: string, to: string, page: number) =>
    request<AttendancePage>(`/api/attendance?${new URLSearchParams({ from, to, page: String(page), size: "50" })}`),
  correct: (id: string, clockInAt: string, clockOutAt: string | null, reason: string) =>
    request<AttendanceRecord>(`/api/attendance/${id}/correction`, {
      method: "PATCH",
      body: JSON.stringify({ clockInAt, clockOutAt, reason }),
    }),
  audit: (id: string) => request<AttendanceAudit[]>(`/api/attendance/${id}/audit`),
};

export const leaveApi = {
  types: (includeInactive = false) => request<LeaveType[]>(`/api/leaves/types?includeInactive=${includeInactive}`),
  createType: (value: { code: string; name: string; description: string; paid: boolean }) =>
    request<LeaveType>("/api/leaves/types", { method: "POST", body: JSON.stringify(value) }),
  ownBalances: (year: number) => request<LeaveBalance[]>(`/api/leaves/me/balances?year=${year}`),
  employeeBalances: (employeeId: string, year: number) =>
    request<LeaveBalance[]>(`/api/leaves/employees/${employeeId}/balances?year=${year}`),
  setBalance: (employeeId: string, typeId: string, year: number, entitledDays: number) =>
    request<LeaveBalance>(`/api/leaves/employees/${employeeId}/balances/${typeId}?year=${year}`, {
      method: "PUT", body: JSON.stringify({ entitledDays }),
    }),
  apply: (value: { leaveTypeId: string; startDate: string; endDate: string; reason: string }) =>
    request<LeaveRequest>("/api/leaves/me/requests", { method: "POST", body: JSON.stringify(value) }),
  ownRequests: () => request<LeaveRequest[]>("/api/leaves/me/requests"),
  cancel: (id: string) => request<void>(`/api/leaves/me/requests/${id}/cancel`, { method: "POST" }),
  requests: (status?: LeaveStatus) => request<LeaveRequest[]>(`/api/leaves/requests${status ? `?status=${status}` : ""}`),
  decide: (id: string, status: "APPROVED" | "REJECTED", note: string) =>
    request<LeaveRequest>(`/api/leaves/requests/${id}/decision`, { method: "POST", body: JSON.stringify({ status, note }) }),
};

export const payrollApi = {
  structures: () => request<PayrollStructure[]>("/api/payroll/structures"),
  setStructure: (employeeId: string, value: { currency: string; baseSalary: number; taxRatePercent: number; effectiveFrom: string; active: boolean; components: { name: string; type: "ALLOWANCE" | "DEDUCTION"; amount: number }[] }) =>
    request<PayrollStructure>(`/api/payroll/employees/${employeeId}/structure`, { method: "PUT", body: JSON.stringify(value) }),
  generate: (month: string) => request<PayrollRun>(`/api/payroll/runs?${new URLSearchParams({ month })}`, { method: "POST" }),
  runs: () => request<PayrollRun[]>("/api/payroll/runs"),
  approve: (id: string) => request<PayrollRun>(`/api/payroll/runs/${id}/approve`, { method: "POST" }),
  markPaid: (id: string) => request<PayrollRun>(`/api/payroll/runs/${id}/mark-paid`, { method: "POST" }),
  runPayslips: (id: string, page = 0) => request<PayrollPage>(`/api/payroll/runs/${id}/payslips?page=${page}&size=50`),
  ownPayslips: () => request<PayrollPayslip[]>("/api/payroll/me/payslips"),
  correct: (id: string, adjustment: { name: string; type: "ALLOWANCE" | "DEDUCTION"; amount: number }, reason: string, taxRatePercent?: number) =>
    request<PayrollCorrection>(`/api/payroll/payslips/${id}/corrections`, { method: "POST", body: JSON.stringify({ adjustments: [adjustment], reason, taxRatePercent }) }),
  corrections: (id: string) => request<PayrollCorrection[]>(`/api/payroll/payslips/${id}/corrections`),
  exportCsv: (id: string) => request<string>(`/api/payroll/runs/${id}/export`, {}, true, true),
};

export const performanceApi = {
  ownGoals: () => request<PerformanceGoal[]>("/api/performance/goals/me"),
  employeeGoals: (employeeId: string) => request<PerformanceGoal[]>(`/api/performance/employees/${employeeId}/goals`),
  createGoal: (value: { title: string; description: string; keyResult: string; targetDate: string }) => request<PerformanceGoal>("/api/performance/goals", { method: "POST", body: JSON.stringify(value) }),
  createEmployeeGoal: (employeeId: string, value: { title: string; description: string; keyResult: string; targetDate: string }) => request<PerformanceGoal>(`/api/performance/employees/${employeeId}/goals`, { method: "POST", body: JSON.stringify(value) }),
  updateGoal: (id: string, value: { title: string; description: string; keyResult: string; targetDate: string; status: PerformanceGoal["status"] }) => request<PerformanceGoal>(`/api/performance/goals/${id}`, { method: "PUT", body: JSON.stringify(value) }),
  cycles: () => request<ReviewCycle[]>("/api/performance/cycles"),
  createCycle: (value: { name: string; startDate: string; endDate: string }) => request<ReviewCycle>("/api/performance/cycles", { method: "POST", body: JSON.stringify(value) }),
  closeCycle: (id: string) => request<ReviewCycle>(`/api/performance/cycles/${id}/close`, { method: "POST" }),
  ownReviews: () => request<PerformanceReview[]>("/api/performance/reviews/me"),
  teamReviews: () => request<PerformanceReview[]>("/api/performance/reviews/team"),
  allReviews: () => request<PerformanceReview[]>("/api/performance/reviews"),
  selfAssess: (id: string, assessment: string) => request<PerformanceReview>(`/api/performance/reviews/${id}/self-assessment`, { method: "POST", body: JSON.stringify({ assessment }) }),
  managerReview: (id: string, rating: number, feedback: string) => request<PerformanceReview>(`/api/performance/reviews/${id}/manager-review`, { method: "POST", body: JSON.stringify({ rating, feedback }) }),
};

export const notificationApi = {
  list: () => request<AppNotification[]>("/api/notifications"),
  unreadCount: () => request<{ count: number }>("/api/notifications/unread-count"),
  markRead: (id: string) => request<AppNotification>(`/api/notifications/${id}/read`, { method: "PATCH" }),
  markAllRead: () => request<void>("/api/notifications/read-all", { method: "POST" }),
  announcements: () => request<Announcement[]>("/api/announcements"),
  publishAnnouncement: (title: string, body: string) => request<Announcement>("/api/announcements", {
    method: "POST", body: JSON.stringify({ title, body }),
  }),
  subscribe(onNotification: (notification: AppNotification) => void) {
    let stopped = false;
    let socket: WebSocket | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let retry = 1000;
    const connect = async () => {
      if (stopped) return;
      try {
        const token = accessToken ?? (await refreshSession()).accessToken;
        if (stopped) return;
        const url = new URL("/ws/notifications", API);
        url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
        socket = new WebSocket(url);
        socket.onopen = () => socket?.send(JSON.stringify({ type: "AUTH", accessToken: token }));
        socket.onmessage = (event) => {
          try {
            const frame = JSON.parse(String(event.data)) as { type?: string; notification?: AppNotification };
            if (frame.type === "READY") retry = 1000;
            if (frame.type === "NOTIFICATION" && frame.notification) onNotification(frame.notification);
          } catch { /* Ignore malformed frames and keep the connection alive. */ }
        };
        socket.onclose = () => {
          if (stopped) return;
          timer = setTimeout(() => {
            void refreshSession().catch(() => undefined).finally(() => { void connect(); });
            retry = Math.min(retry * 2, 30000);
          }, retry);
        };
      } catch {
        if (!stopped) {
          timer = setTimeout(() => { void connect(); retry = Math.min(retry * 2, 30000); }, retry);
        }
      }
    };
    void connect();
    return () => { stopped = true; if (timer) clearTimeout(timer); socket?.close(); };
  },
};

export const intelligenceApi = {
  workforce: () => request<import("./types").WorkforceSummary>("/api/intelligence/workforce"),
  ask: (question: string) => request<import("./types").WorkforceInsight>("/api/intelligence/questions", {
    method: "POST", body: JSON.stringify({ question }),
  }),
};
