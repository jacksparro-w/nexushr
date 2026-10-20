import { useCallback, useEffect, useState, type FormEvent } from "react";
import { notificationApi } from "../api";
import type { Announcement, AppNotification, User } from "../types";

export function NotificationWorkspace({ user }: { user: User }) {
  const [items, setItems] = useState<AppNotification[]>([]);
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const canPublish = user.roles.some((role) => role === "ADMIN" || role === "HR");

  const load = useCallback(async () => {
    try {
      const [notifications, count, news] = await Promise.all([
        notificationApi.list(), notificationApi.unreadCount(), notificationApi.announcements(),
      ]);
      setItems(notifications); setUnread(count.count); setAnnouncements(news); setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load notifications");
    }
  }, []);

  useEffect(() => {
    void load();
    return notificationApi.subscribe((notification) => {
      setItems((current) => [notification, ...current.filter((item) => item.id !== notification.id)].slice(0, 100));
      void notificationApi.unreadCount().then((count) => setUnread(count.count)).catch(() => undefined);
    });
  }, [load]);

  async function markRead(item: AppNotification) {
    if (item.readAt) return;
    try {
      const updated = await notificationApi.markRead(item.id);
      setItems((current) => current.map((entry) => entry.id === item.id ? updated : entry));
      setUnread((current) => Math.max(0, current - 1));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not mark notification as read"); }
  }

  async function markAllRead() {
    try {
      await notificationApi.markAllRead();
      setItems((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? new Date().toISOString() })));
      setUnread(0);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not mark notifications as read"); }
  }

  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    setBusy(true); setError(""); setNotice("");
    try {
      await notificationApi.publishAnnouncement(String(form.get("title")), String(form.get("body")));
      element.reset(); setNotice("Announcement sent to everyone."); await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not publish announcement"); }
    finally { setBusy(false); }
  }

  return <div className="notification-workspace">
    <button className="quiet notification-toggle" type="button" aria-expanded={open} onClick={() => { setOpen((value) => !value); if (!open) void load(); }}>
      Notifications <span className="notification-count" aria-label={`${unread} unread`}>{unread > 99 ? "99+" : unread}</span>
    </button>
    {open && <section className="notification-panel" aria-label="Notifications and announcements">
      <div className="notification-panel-heading"><div><div className="eyebrow">UPDATES</div><h2>Notifications</h2></div><button className="text-button" type="button" disabled={!unread} onClick={() => void markAllRead()}>Mark all read</button></div>
      {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
      <div className="notification-list">
        {items.map((item) => <article key={item.id} className={`notification-item ${item.readAt ? "read" : "unread"}`}>
          <div><strong>{item.title}</strong><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString()}</small></div>
          {!item.readAt && <button className="text-button" type="button" onClick={() => void markRead(item)}>Mark read</button>}
        </article>)}
        {!items.length && <p className="muted">You’re all caught up.</p>}
      </div>
      <div className="announcement-list"><h3>Announcements</h3>
        {announcements.map((item) => <article key={item.id} className="announcement-item"><strong>{item.title}</strong><p>{item.body}</p><small>{new Date(item.createdAt).toLocaleString()}</small></article>)}
        {!announcements.length && <p className="muted">No announcements yet.</p>}
      </div>
      {canPublish && <form className="announcement-form" onSubmit={publish}><h3>Publish announcement</h3><label>Title<input name="title" required maxLength={180}/></label><label>Message<textarea name="body" required maxLength={4000} rows={3}/></label><button className="quiet" disabled={busy}>{busy ? "Sending…" : "Send to everyone"}</button></form>}
      <p className="muted notification-realtime-note">Live updates reconnect automatically. Email delivery is queued for retry if the mail provider is unavailable.</p>
    </section>}
  </div>;
}
