import { useEffect, useState, type FormEvent } from "react";
import { authApi } from "../api";
import type { User } from "../types";
import { EmployeeWorkspace } from "./EmployeeWorkspace";
import { AttendanceWorkspace } from "./AttendanceWorkspace";
import { LeaveWorkspace } from "./LeaveWorkspace";
import { PayrollWorkspace } from "./PayrollWorkspace";
import { PerformanceWorkspace } from "./PerformanceWorkspace";
import { DashboardWorkspace } from "./DashboardWorkspace";
import { NotificationWorkspace } from "./NotificationWorkspace";
import { IntelligenceWorkspace } from "./IntelligenceWorkspace";
import { ProjectWorkspace } from "./ProjectWorkspace";

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const handleSessionExpired = () => {
      setUser(null);
      setPassword("");
      setError("Your session expired. Sign in again to view your profile.");
    };
    window.addEventListener("nexushr:session-expired", handleSessionExpired);
    authApi
      .restore()
      .then(setUser)
      .catch(() => undefined);
    return () => window.removeEventListener("nexushr:session-expired", handleSessionExpired);
  }, []);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      setUser(await authApi.login(email, password));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    await authApi.logout();
    setUser(null);
    setPassword("");
  }

  if (user)
    return (
      <main className="shell">
        <header className="topbar">
          <a className="brand" href="/">
            Nexus<span>HR</span>
          </a>
          <button className="quiet" onClick={logout}>
            Sign out
          </button>
        </header>
        <NotificationWorkspace user={user} />
        <DashboardWorkspace user={user} />
        <IntelligenceWorkspace user={user} />
        <AttendanceWorkspace user={user} />
        <LeaveWorkspace user={user} />
        <PayrollWorkspace user={user} />
        <PerformanceWorkspace user={user} />
        <EmployeeWorkspace user={user} />
        <ProjectWorkspace user={user} />
      </main>
    );

  return (
    <main className="login-layout">
      <section className="story">
        <a className="brand light" href="/">
          Nexus<span>HR</span>
        </a>
        <div className="story-copy">
          <div className="eyebrow">PEOPLE, IN FOCUS</div>
          <h1>
            Better work
            <br />
            starts with
            <br />
            <em>people.</em>
          </h1>
          <p>
            A thoughtful place to manage the moments that move your organization
            forward.
          </p>
        </div>
        <div className="story-foot">
          WORKFORCE INTELLIGENCE · BUILT FOR PEOPLE
        </div>
      </section>
      <section className="form-side">
        <div className="form-wrap">
          <div className="eyebrow">YOUR WORKSPACE</div>
          <h2>Welcome back</h2>
          <p className="subhead">Sign in to continue to NexusHR.</p>
          <form onSubmit={login}>
            <label htmlFor="email">Work email</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              placeholder="you@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <div className="password-label">
              <label htmlFor="password">Password</label>
            </div>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            {error && (
              <div className="error" role="alert">
                {error}
              </div>
            )}
            <button className="submit" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
              <span aria-hidden="true">→</span>
            </button>
          </form>
          <div className="secure-note">
            <span>◈</span> Your account is protected with secure authentication.
          </div>
        </div>
        <footer>
          © 2026 NexusHR <span>·</span> Human resources, thoughtfully managed
        </footer>
      </section>
    </main>
  );
}
