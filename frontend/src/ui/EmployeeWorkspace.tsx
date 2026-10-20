import { useEffect, useState, type FormEvent } from "react";
import { authApi, peopleApi } from "../api";
import type { Department, Employee, EmployeeDocument, EmployeePage, ManagerOption, Role, User } from "../types";

type Props = { user: User };

function EmployeeDocuments({ employee, canManage }: { employee: Employee; canManage: boolean }) {
  const [documents, setDocuments] = useState<EmployeeDocument[]>([]);
  const [error, setError] = useState("");
  const [loaded, setLoaded] = useState(false);

  async function load() {
    try {
      setDocuments(await peopleApi.documents(employee.id));
      setLoaded(true);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load document metadata");
    }
  }

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await peopleApi.addDocument(employee.id, {
        documentType: String(form.get("documentType")),
        fileName: String(form.get("fileName")),
        contentType: String(form.get("contentType")),
        sizeBytes: Number(form.get("sizeBytes")),
      });
      (event.target as HTMLFormElement).reset();
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not add document metadata");
    }
  }

  async function remove(documentId: string) {
    try {
      await peopleApi.deleteDocument(employee.id, documentId);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not delete document metadata");
    }
  }

  return <details className="document-details" onToggle={(event) => {
    if ((event.currentTarget as HTMLDetailsElement).open && !loaded) void load();
  }}><summary>Document metadata</summary>{error && <p className="error" role="alert">{error}</p>}
    {documents.map((document) => <div className="document-item" key={document.id}><span>{document.documentType} · {document.fileName} ({document.sizeBytes} bytes)</span>{canManage && <button className="text-button" onClick={() => void remove(document.id)}>Remove</button>}</div>)}
    {!documents.length && loaded && <p className="muted">No document metadata recorded.</p>}
    {canManage && <form className="document-form" onSubmit={add}><label>Type<select name="documentType"><option>IDENTITY</option><option>TAX</option><option>OFFER_LETTER</option><option>CONTRACT</option><option>CERTIFICATION</option><option>OTHER</option></select></label><label>File name<input name="fileName" required maxLength={255}/></label><label>Content type<input name="contentType" required defaultValue="application/pdf"/></label><label>Size (bytes)<input name="sizeBytes" type="number" min="0" required/></label><button className="quiet">Add metadata</button></form>}
  </details>;
}

function EmployeeLoginSetup({ employee }: { employee: Employee }) {
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function createLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await authApi.createAccount(employee.workEmail, String(form.get("password")));
      (event.target as HTMLFormElement).reset();
      setMessage("Login created. The employee can sign in with this email and the initial password.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create login");
    } finally {
      setBusy(false);
    }
  }

  return <details className="employee-login-setup"><summary>Create login</summary>
    <form onSubmit={createLogin}>
      <label>Initial password<input name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required/></label>
      <button className="quiet" disabled={busy}>{busy ? "Creating…" : "Create employee login"}</button>
    </form>
    {message && <p className="notice" role="status">{message}</p>}
    {error && <p className="error" role="alert">{error}</p>}
  </details>;
}

function AccountManagement({ user, onAccountCreated }: { user: User; onAccountCreated: () => void }) {
  const availableRoles: Role[] = user.roles.includes("ADMIN")
    ? ["EMPLOYEE", "MANAGER", "HR"]
    : ["EMPLOYEE", "MANAGER"];
  const [roles, setRoles] = useState<Role[]>(["EMPLOYEE"]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function toggleRole(role: Role, checked: boolean) {
    setRoles((current) => checked
      ? [...new Set([...current, role])]
      : current.filter((item) => item !== role));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const account = await authApi.createAccount(String(form.get("email")), String(form.get("password")), roles);
      (event.target as HTMLFormElement).reset();
      setRoles(["EMPLOYEE"]);
      setMessage(`Login account created for ${account.email}. Share the initial password with that person securely.`);
      onAccountCreated();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create login account");
    } finally {
      setBusy(false);
    }
  }

  return <details className="management-card account-management">
    <summary>Create login account</summary>
    <p>Login accounts are separate from employee profiles. Use the employee profile’s work email so self-service profile access matches.</p>
    <form className="people-form" onSubmit={submit}>
      <label>Work email<input name="email" type="email" autoComplete="off" required/></label>
      <label>Initial password<input name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required/></label>
      <fieldset><legend>Access roles</legend>{availableRoles.map((role) => <label className="role-choice" key={role}><input type="checkbox" checked={roles.includes(role)} onChange={(event) => toggleRole(role, event.target.checked)}/>{role}</label>)}</fieldset>
      <button className="submit" disabled={busy || roles.length === 0}>{busy ? "Creating…" : "Create account"}</button>
    </form>
    {message && <p className="notice" role="status">{message}</p>}
    {error && <p className="error" role="alert">{error}</p>}
  </details>;
}

export function EmployeeWorkspace({ user }: Props) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const isManager = user.roles.includes("MANAGER");
  const [departments, setDepartments] = useState<Department[]>([]);
  const [managers, setManagers] = useState<ManagerOption[]>([]);
  const [employees, setEmployees] = useState<EmployeePage | null>(null);
  const [profile, setProfile] = useState<Employee | null>(null);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [query, setQuery] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function loadDirectory(nextPage = page, search = query) {
    setError("");
    try {
      const params = new URLSearchParams({ page: String(nextPage), size: "10" });
      if (search.trim()) params.set("q", search.trim());
      if (departmentFilter) params.set("departmentId", departmentFilter);
      if (statusFilter) params.set("status", statusFilter);
      const data = await peopleApi.employees(params);
      setEmployees(data);
      setPage(data.number);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load employees");
    }
  }

  useEffect(() => {
    if (canManage || isManager) {
      peopleApi.departments().then(setDepartments).catch(() => undefined);
      if (canManage) peopleApi.managers().then(setManagers).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Could not load manager accounts"));
      void loadDirectory(0, "");
    } else {
      peopleApi.me().then(setProfile).catch((cause: unknown) =>
        setError(cause instanceof Error ? cause.message : "Employee profile is unavailable"),
      );
    }
  }, [canManage, isManager]);

  async function createEmployee(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const employee = {
        employeeCode: String(form.get("employeeCode")),
        firstName: String(form.get("firstName")),
        lastName: String(form.get("lastName")),
        workEmail: String(form.get("workEmail")),
        phone: String(form.get("phone")) || null,
        designation: String(form.get("designation")),
        departmentId: String(form.get("departmentId")),
        managerId: String(form.get("managerId")) || null,
        status: String(form.get("status")) as Employee["status"],
        onboardingStatus: String(form.get("onboardingStatus")) as Employee["onboardingStatus"],
        hireDate: String(form.get("hireDate")),
      };
      if (editing) await peopleApi.updateEmployee(editing.id, employee);
      else await peopleApi.createEmployee(employee);
      (event.target as HTMLFormElement).reset();
      setEditing(null);
      setNotice(editing ? "Employee profile updated." : "Employee profile created.");
      void peopleApi.managers().then(setManagers).catch(() => undefined);
      await loadDirectory(0);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create employee");
    } finally {
      setBusy(false);
    }
  }

  async function deleteEmployee(employee: Employee) {
    if (!window.confirm(`Delete ${employee.firstName} ${employee.lastName}'s profile?`)) return;
    setError("");
    try {
      await peopleApi.deleteEmployee(employee.id);
      setNotice("Employee profile deleted.");
      await loadDirectory();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not delete employee");
    }
  }

  async function createDepartment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const department = await peopleApi.createDepartment({
        code: String(form.get("code")),
        name: String(form.get("name")),
        description: String(form.get("description")),
        managerId: String(form.get("managerId")) || null,
      });
      setDepartments((current) => [...current, department].sort((a, b) => a.name.localeCompare(b.name)));
      (event.target as HTMLFormElement).reset();
      setNotice("Department created.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create department");
    }
  }

  async function assignDepartmentManager(department: Department, managerId: string) {
    setError("");
    setNotice("");
    try {
      const updated = await peopleApi.updateDepartment(department.id, {
        code: department.code,
        name: department.name,
        description: department.description,
        active: department.active,
        managerId: managerId || null,
      });
      setDepartments((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(`Department manager updated for ${updated.name}.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not assign department manager");
    }
  }

  if (!canManage && !isManager) {
    return <section className="directory"><div className="eyebrow">MY PROFILE</div><h1>Employee profile</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {profile && <article className="profile-card"><h2>{profile.firstName} {profile.lastName}</h2><p>{profile.designation} · {profile.departmentName}</p><dl>
        <dt>Employee ID</dt><dd>{profile.employeeCode}</dd><dt>Work email</dt><dd>{profile.workEmail}</dd>
        <dt>Status</dt><dd>{profile.status.replace("_", " ")}</dd><dt>Onboarding</dt><dd>{profile.onboardingStatus.replace("_", " ")}</dd><dt>Hire date</dt><dd>{profile.hireDate}</dd>
      </dl><EmployeeDocuments employee={profile} canManage={false}/></article>}
    </section>;
  }

  return <section className="directory">
    <div className="directory-heading"><div><div className="eyebrow">PEOPLE OPERATIONS</div><h1>{canManage ? "Employee directory" : "My team"}</h1><p>Profiles, teams, and onboarding in one place.</p></div>
      <form className="search-form" onSubmit={(event) => { event.preventDefault(); void loadDirectory(0); }}><label className="sr-only" htmlFor="employee-search">Search employees</label><input id="employee-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search name, email, or ID"/><label className="sr-only" htmlFor="department-filter">Department</label><select id="department-filter" value={departmentFilter} onChange={(event) => { setDepartmentFilter(event.target.value); }}><option value="">All departments</option>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select><label className="sr-only" htmlFor="status-filter">Employment status</label><select id="status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="TERMINATED">Terminated</option></select><button className="quiet">Search</button></form>
    </div>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    {canManage && <AccountManagement user={user} onAccountCreated={() => { void peopleApi.managers().then(setManagers).catch(() => undefined); }} />}
    {canManage && <details className="management-card" open={Boolean(editing)}><summary>{editing ? `Edit ${editing.firstName} ${editing.lastName}` : "Add employee"}</summary><form className="people-form" key={editing?.id ?? "new"} onSubmit={createEmployee}>
      <label>Employee ID<input name="employeeCode" required maxLength={30} defaultValue={editing?.employeeCode}/></label><label>First name<input name="firstName" required defaultValue={editing?.firstName}/></label><label>Last name<input name="lastName" required defaultValue={editing?.lastName}/></label><label>Work email<input name="workEmail" type="email" required defaultValue={editing?.workEmail}/></label><label>Phone<input name="phone" defaultValue={editing?.phone ?? ""}/></label><label>Designation<input name="designation" required defaultValue={editing?.designation}/></label><label>Department<select name="departmentId" required defaultValue={editing?.departmentId ?? ""}><option value="" disabled>Select department</option>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label><label>Manager<select name="managerId" defaultValue={editing?.managerId ?? ""}><option value="">No manager</option>{editing?.managerId && !managers.some((manager) => manager.id === editing.managerId) && <option value={editing.managerId}>{editing.managerName ?? "Current manager"}</option>}{managers.filter((manager) => manager.id !== editing?.id).map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName} · {manager.departmentName}</option>)}</select></label><label>Status<select name="status" defaultValue={editing?.status ?? "ACTIVE"}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="TERMINATED">Terminated</option></select></label><label>Onboarding<select name="onboardingStatus" defaultValue={editing?.onboardingStatus ?? "NOT_STARTED"}><option value="NOT_STARTED">Not started</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETE">Complete</option></select></label><label>Hire date<input name="hireDate" type="date" required defaultValue={editing?.hireDate}/></label>
      <button className="submit" disabled={busy}>{busy ? "Saving…" : editing ? "Save changes" : "Create profile"}</button>{editing && <button type="button" className="quiet" onClick={() => setEditing(null)}>Cancel edit</button>}
    </form></details>}
    {canManage && <details className="management-card"><summary>Departments and department managers</summary><form className="people-form" onSubmit={createDepartment}><label>Department code<input name="code" required maxLength={30}/></label><label>Name<input name="name" required/></label><label>Description<input name="description"/></label><label>Department manager<select name="managerId" defaultValue=""><option value="">No manager</option>{managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName} · {manager.departmentName}</option>)}</select></label><button className="quiet">Create department</button></form>{departments.map((department) => <label key={department.id}>{department.name} manager<select value={department.managerId ?? ""} onChange={(event) => void assignDepartmentManager(department, event.target.value)}><option value="">No manager</option>{managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName} · {manager.departmentName}</option>)}</select></label>)}</details>}
    <div className="employee-table-wrap"><table><thead><tr><th>Employee</th><th>Department</th><th>Designation</th><th>Manager</th><th>Status</th><th>Onboarding</th>{canManage && <th>Actions</th>}</tr></thead><tbody>
      {employees?.content.map((employee) => <tr key={employee.id}><td><strong>{employee.firstName} {employee.lastName}</strong><span>{employee.employeeCode} · {employee.workEmail}</span><EmployeeDocuments employee={employee} canManage={canManage}/>{canManage && <EmployeeLoginSetup employee={employee}/>}</td><td>{employee.departmentName}</td><td>{employee.designation}</td><td>{employee.managerName ?? "—"}</td><td><span className={`pill ${employee.status.toLowerCase()}`}>{employee.status.replace("_", " ")}</span></td><td>{employee.onboardingStatus.replaceAll("_", " ")}</td>{canManage && <td><button className="text-button" onClick={() => setEditing(employee)}>Edit</button><button className="text-button" onClick={() => void deleteEmployee(employee)}>Delete</button></td>}</tr>)}
      {!employees?.content.length && <tr><td colSpan={canManage ? 7 : 6} className="empty-state">No employee profiles found.</td></tr>}
    </tbody></table></div>
    <div className="pagination"><span>{employees?.totalElements ?? 0} employees</span><div><button className="quiet" disabled={!employees || page <= 0} onClick={() => void loadDirectory(page - 1)}>Previous</button><span>Page {employees ? page + 1 : 0} of {employees?.totalPages ?? 0}</span><button className="quiet" disabled={!employees || page + 1 >= employees.totalPages} onClick={() => void loadDirectory(page + 1)}>Next</button></div></div>
  </section>;
}
