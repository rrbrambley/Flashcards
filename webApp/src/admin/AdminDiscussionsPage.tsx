import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../api/client';
import type { ReportedMessage } from '../api/types';
import { useAuth } from '../auth/auth-context';

/**
 * The discussion moderation queue (FLA-118): open reports, newest first, with the reported message
 * inline. A moderator can delete the offending message (which resolves its reports) or dismiss the
 * report. Gated on `manage_discussions` — non-admins are bounced; the API enforces the same gate.
 */
export function AdminDiscussionsPage() {
  const { signOut, can } = useAuth();
  const [reports, setReports] = useState<ReportedMessage[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // reportIds with a delete/dismiss request in flight (their buttons disable).
  const [pending, setPending] = useState<Set<number>>(new Set());

  const loadPage = useCallback(async (cursorToken: string | undefined, reset: boolean) => {
    try {
      const page = await api.getDiscussionReports(cursorToken ? { cursor: cursorToken } : {});
      setReports((prev) => (reset ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load reports.');
    }
  }, []);

  useEffect(() => {
    // loadPage only writes state after its await, but this lint rule is conservative about effects
    // that call a state-setting function.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPage(undefined, true).finally(() => setLoading(false));
  }, [loadPage]);

  const isAdmin = can('manage_discussions');
  if (!isAdmin) return <Navigate to="/library" replace />;

  const loadMore = async () => {
    if (!cursor) return;
    setLoadingMore(true);
    await loadPage(cursor, false);
    setLoadingMore(false);
  };

  const withPending = async (reportId: number, action: () => Promise<void>) => {
    setPending((s) => new Set(s).add(reportId));
    try {
      await action();
      setReports((prev) => prev.filter((r) => r.reportId !== reportId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed. Please try again.');
    } finally {
      setPending((s) => {
        const next = new Set(s);
        next.delete(reportId);
        return next;
      });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Discussion reports</h1>
        <nav className="app-header-nav">
          <Link to="/library" className="link-btn">
            Library
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : reports.length === 0 ? (
        <p className="muted">No open reports. 🎉</p>
      ) : (
        <ul className="report-list">
          {reports.map((report) => (
            <li key={report.reportId} className="report-card">
              <div className="report-message">
                <div className="discussion-message-meta">
                  <span className="discussion-author">{report.authorDisplayName}</span>
                  <span className="muted discussion-time">{formatTime(report.messageCreatedAtMillis)}</span>
                </div>
                <p className="discussion-content">
                  {report.deleted ? <span className="muted discussion-removed">[already removed]</span> : report.content}
                </p>
              </div>
              <p className="report-meta muted">
                Reported by {report.reporterDisplayName} · {formatTime(report.reportedAtMillis)}
                {report.reason ? ` · “${report.reason}”` : ''}
              </p>
              <div className="report-actions">
                <button
                  className="danger"
                  disabled={pending.has(report.reportId) || report.deleted}
                  onClick={() => withPending(report.reportId, () => api.deleteDiscussionMessage(report.messageId).then(() => {}))}
                >
                  Delete message
                </button>
                <button
                  className="secondary"
                  disabled={pending.has(report.reportId)}
                  onClick={() => withPending(report.reportId, () => api.dismissDiscussionReport(report.reportId))}
                >
                  Dismiss
                </button>
              </div>
            </li>
          ))}
          {cursor && (
            <li>
              <button className="secondary" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Loading…' : 'Load more'}
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}

function formatTime(millis: number): string {
  return new Date(millis).toLocaleString();
}
