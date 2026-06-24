import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminDiscussionsPage } from './AdminDiscussionsPage';
import { api } from '../api/client';
import type { ReportedMessage } from '../api/types';

const authState = vi.hoisted(() => ({ canManageDiscussions: false, permissionsReady: true }));

vi.mock('../api/client', () => ({
  api: { getDiscussionReports: vi.fn(), deleteDiscussionMessage: vi.fn(), dismissDiscussionReport: vi.fn() },
}));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({
    signOut: vi.fn(),
    can: (p: string) => p === 'manage_discussions' && authState.canManageDiscussions,
    permissionsReady: authState.permissionsReady,
  }),
}));

const report = (over: Partial<ReportedMessage> = {}): ReportedMessage => ({
  reportId: 1,
  reason: 'spam',
  status: 'open',
  reportedAtMillis: 1000,
  reporterDisplayName: 'Reporter',
  messageId: 10,
  cardUid: 'c1',
  authorDisplayName: 'Author',
  content: 'the reported message',
  deleted: false,
  messageCreatedAtMillis: 500,
  ...over,
});

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/admin/discussions']}>
      <Routes>
        <Route path="/admin/discussions" element={<AdminDiscussionsPage />} />
        <Route path="/library" element={<div>Personal library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminDiscussionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageDiscussions = false;
    authState.permissionsReady = true;
  });

  it('redirects non-admins to the library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
  });

  it('shows a loading state (not a redirect) while permissions are still hydrating', () => {
    authState.permissionsReady = false; // cold load: /auth/me not resolved yet
    renderPage();
    expect(screen.getByText('Loading…')).toBeInTheDocument();
    expect(screen.queryByText('Personal library')).not.toBeInTheDocument();
  });

  it('lists open reports with the reported message and reason', async () => {
    authState.canManageDiscussions = true;
    vi.mocked(api.getDiscussionReports).mockResolvedValue({ items: [report()], nextCursor: null });
    renderPage();

    expect(await screen.findByText('the reported message')).toBeInTheDocument();
    expect(screen.getByText(/Reported by Reporter/)).toBeInTheDocument();
    expect(screen.getByText(/spam/)).toBeInTheDocument();
  });

  it('deletes the reported message and removes the row', async () => {
    authState.canManageDiscussions = true;
    vi.mocked(api.getDiscussionReports).mockResolvedValue({ items: [report()], nextCursor: null });
    vi.mocked(api.deleteDiscussionMessage).mockResolvedValue({
      id: 10,
      authorDisplayName: 'Author',
      content: '',
      createdAtMillis: 500,
      deleted: true,
    });
    renderPage();
    const row = within((await screen.findByText('the reported message')).closest('li') as HTMLElement);

    await userEvent.click(row.getByRole('button', { name: 'Delete message' }));

    await waitFor(() => expect(api.deleteDiscussionMessage).toHaveBeenCalledWith(10));
    await waitFor(() => expect(screen.queryByText('the reported message')).not.toBeInTheDocument());
  });

  it('dismisses a report and removes the row', async () => {
    authState.canManageDiscussions = true;
    vi.mocked(api.getDiscussionReports).mockResolvedValue({ items: [report()], nextCursor: null });
    vi.mocked(api.dismissDiscussionReport).mockResolvedValue(undefined);
    renderPage();
    const row = within((await screen.findByText('the reported message')).closest('li') as HTMLElement);

    await userEvent.click(row.getByRole('button', { name: 'Dismiss' }));

    await waitFor(() => expect(api.dismissDiscussionReport).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('the reported message')).not.toBeInTheDocument());
  });
});
