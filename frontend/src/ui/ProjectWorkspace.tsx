import { useEffect, useState, type FormEvent } from "react";
import { peopleApi, projectApi } from "../api";
import type { Department, ManagerOption, Project, User } from "../types";

export function ProjectWorkspace({ user }: { user: User }) {
  const canManage = user.roles.some((role) => role === "ADMIN" || role === "HR");
  const [projects, setProjects] = useState<Project[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [managers, setManagers] = useState<ManagerOption[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      setProjects(await projectApi.list());
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load projects");
    }
  }

  async function refreshManagers() {
    try {
      setManagers(await peopleApi.managers());
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not refresh manager accounts");
    }
  }

  useEffect(() => {
    void load();
    if (canManage) {
      peopleApi.departments().then(setDepartments).catch(() => undefined);
      peopleApi.managers().then(setManagers).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Could not load manager accounts"));
    }
  }, [canManage]);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await projectApi.create({
        code: String(form.get("code")),
        name: String(form.get("name")),
        description: String(form.get("description")),
        departmentId: String(form.get("departmentId")),
        managerId: String(form.get("managerId")) || null,
      });
      formElement.reset();
      setNotice("Project created and assigned.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create project");
    } finally {
      setBusy(false);
    }
  }

  async function update(project: Project, form: HTMLFormElement) {
    const values = new FormData(form);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await projectApi.update(project.id, {
        code: project.code,
        name: project.name,
        description: project.description,
        departmentId: String(values.get("departmentId")),
        managerId: String(values.get("managerId")) || null,
        active: project.active,
      });
      setNotice(`Assignments updated for ${project.name}.`);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not update project assignment");
    } finally {
      setBusy(false);
    }
  }

  return <section className="directory">
    <div className="directory-heading"><div><div className="eyebrow">TEAMS & DELIVERY</div><h1>Projects</h1><p>Assign each project to a department and an accountable manager.</p></div></div>
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {canManage && <details className="management-card"><summary>Create project</summary><button type="button" className="quiet" onClick={() => void refreshManagers()}>Refresh manager list</button><form className="people-form" onSubmit={create}>
      <label>Project code<input name="code" required maxLength={30}/></label>
      <label>Project name<input name="name" required maxLength={120}/></label>
      <label>Description<input name="description" maxLength={500}/></label>
      <label>Department<select name="departmentId" required defaultValue=""><option value="" disabled>Select department</option>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>
      <label>Project manager<select name="managerId" defaultValue=""><option value="">Unassigned</option>{managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName} · {manager.departmentName}</option>)}</select></label>
      <button className="submit" disabled={busy || departments.length === 0}>{busy ? "Saving…" : "Create project"}</button>
    </form></details>}
    <div className="performance-list">{projects.map((project) => <article className="performance-item" key={project.id}>
      <div><strong>{project.name} · {project.code}</strong><p>{project.departmentName} · Manager: {project.managerName ?? "Unassigned"}</p>{project.description && <p>{project.description}</p>}</div>
      {canManage && <details><summary>Change assignments</summary><form className="people-form" onSubmit={(event) => { event.preventDefault(); void update(project, event.currentTarget); }}>
        <label>Department<select name="departmentId" defaultValue={project.departmentId}>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>
        <label>Project manager<select name="managerId" defaultValue={project.managerId ?? ""}><option value="">Unassigned</option>{managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName} · {manager.departmentName}</option>)}</select></label>
        <button className="quiet" disabled={busy}>Save assignments</button>
      </form></details>}
    </article>)}{!projects.length && <p className="muted">No projects created yet.</p>}</div>
  </section>;
}
