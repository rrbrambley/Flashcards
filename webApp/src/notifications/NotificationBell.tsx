import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { NotificationDto } from '../api/types';

const POLL_MS = 60_000;

/**
 * Per-type display copy from the notification's `data` payload (#321). English literals — the web app
 * has no i18n framework yet (see #315 for the mobile catalogs). Unknown types get a generic fallback.
 */
function notificationText(n: NotificationDto): string {
  switch (n.type) {
    case 'discussion_reply':
      return `${n.data.replierDisplayName ?? 'Someone'} replied to your comment`;
    default:
      return 'You have a new notification';
  }
}

/** Compact relative time: "just now", "5m", "3h", "2d", else an absolute date. */
function relativeTime(millis: number): string {
  const secs = Math.max(0, Math.floor((Date.now() - millis) / 1000));
  if (secs < 60) return 'just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d`;
  return new Date(millis).toLocaleDateString();
}

/**
 * The in-app notifications center (#321): a bell with an unread badge that opens a dropdown of the
 * caller's notifications (newest first, cursor "Load more"), with per-item + mark-all read. Gated by
 * the caller on the `notifications` feature flag. Deep-linking to a notification's source is a future
 * enhancement (the web app has no standalone discussion route yet); tapping an item marks it read.
 */
export function NotificationBell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationDto[] | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const refreshCount = useCallback(async () => {
    try {
      setUnread((await api.getUnreadNotificationCount()).count);
    } catch {
      // best-effort; keep the last known count
    }
  }, []);

  // Poll the unread count for the badge. Both the initial fetch and the interval run in timer
  // callbacks (not synchronously in the effect body) — the count updates as external state changes.
  useEffect(() => {
    const tick = () => void refreshCount();
    const first = setTimeout(tick, 0);
    const id = setInterval(tick, POLL_MS);
    return () => {
      clearTimeout(first);
      clearInterval(id);
    };
  }, [refreshCount]);

  // Close the panel on an outside click.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const loadFirstPage = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.getNotifications();
      setItems(page.items);
      setCursor(page.nextCursor ?? null);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && items === null) void loadFirstPage();
  };

  const loadMore = async () => {
    if (!cursor) return;
    setLoading(true);
    try {
      const page = await api.getNotifications(cursor);
      setItems((prev) => [...(prev ?? []), ...page.items]);
      setCursor(page.nextCursor ?? null);
    } finally {
      setLoading(false);
    }
  };

  const markRead = async (n: NotificationDto) => {
    if (n.isRead) return;
    setItems((prev) => (prev ?? []).map((it) => (it.id === n.id ? { ...it, isRead: true } : it)));
    setUnread((c) => Math.max(0, c - 1));
    try {
      await api.markNotificationRead(n.id);
    } catch {
      // optimistic; a later poll reconciles
    }
  };

  const markAllRead = async () => {
    setItems((prev) => (prev ?? []).map((it) => ({ ...it, isRead: true })));
    setUnread(0);
    try {
      await api.markAllNotificationsRead();
    } catch {
      // optimistic
    }
  };

  const hasUnread = items?.some((n) => !n.isRead) ?? false;

  return (
    <div className="notif" ref={containerRef}>
      <button
        className="link-btn notif-bell"
        onClick={toggle}
        aria-label={unread > 0 ? `Notifications (${unread} unread)` : 'Notifications'}
        aria-haspopup="true"
        aria-expanded={open}
      >
        🔔
        {unread > 0 && (
          <span className="notif-badge" aria-hidden="true">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="notif-panel" role="dialog" aria-label="Notifications">
          <div className="notif-panel-head">
            <span>Notifications</span>
            {hasUnread && (
              <button className="link-btn" onClick={() => void markAllRead()}>
                Mark all read
              </button>
            )}
          </div>
          {items === null || (loading && items.length === 0) ? (
            <p className="notif-empty muted">Loading…</p>
          ) : items.length === 0 ? (
            <p className="notif-empty muted">No notifications yet.</p>
          ) : (
            <ul className="notif-list">
              {items.map((n) => (
                <li key={n.id}>
                  <button className={`notif-item${n.isRead ? '' : ' unread'}`} onClick={() => void markRead(n)}>
                    <span className="notif-text">{notificationText(n)}</span>
                    <span className="notif-time muted">{relativeTime(n.createdAtMillis)}</span>
                  </button>
                </li>
              ))}
              {cursor && (
                <li>
                  <button className="link-btn notif-more" onClick={() => void loadMore()} disabled={loading}>
                    {loading ? 'Loading…' : 'Load more'}
                  </button>
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
