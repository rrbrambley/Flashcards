import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminFlagsPage } from './AdminFlagsPage';
import { api } from '../api/client';
import type { AdminFlagDto } from '../api/types';

const authState = vi.hoisted(() => ({ canManageFlags: false, permissionsReady: true }));

vi.mock('../api/client', () => ({
  api: {
    getAdminFlags: vi.fn(),
    getRoles: vi.fn(),
    setFlagGlobal: vi.fn(),
    setFlagRoleOverride: vi.fn(),
    clearFlagRoleOverride: vi.fn(),
    setFlagUserOverride: vi.fn(),
    clearFlagUserOverride: vi.fn(),
    getAdminUsers: vi.fn(),
  },
}));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({
    signOut: vi.fn(),
    can: (p: string) => p === 'manage_feature_flags' && authState.canManageFlags,
    permissionsReady: authState.permissionsReady,
  }),
}));

const ROLES = [
  { key: 'admin', description: 'Full administrative access', permissions: ['manage_feature_flags'] },
  { key: 'user', description: 'A standard, non-privileged user', permissions: [] },
];

const flag = (over: Partial<AdminFlagDto> = {}): AdminFlagDto => ({
  key: 'streak_calendar',
  description: 'Show the practice-activity streak calendar',
  enabled: false,
  userOverrides: [],
  roleOverrides: [],
  ...over,
});

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/admin/flags']}>
      <Routes>
        <Route path="/admin/flags" element={<AdminFlagsPage />} />
        <Route path="/library" element={<div>Personal library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminFlagsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageFlags = false;
    authState.permissionsReady = true;
    vi.mocked(api.getRoles).mockResolvedValue(ROLES);
    vi.mocked(api.getAdminFlags).mockResolvedValue([flag()]);
  });

  it('redirects non-admins to the library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
    expect(api.getAdminFlags).not.toHaveBeenCalled();
  });

  it('lists flags with their description and global state', async () => {
    authState.canManageFlags = true;
    renderPage();

    expect(await screen.findByText('streak_calendar')).toBeInTheDocument();
    expect(screen.getByText('Show the practice-activity streak calendar')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'streak_calendar global' })).not.toBeChecked();
  });

  it('toggles the global state', async () => {
    authState.canManageFlags = true;
    vi.mocked(api.setFlagGlobal).mockResolvedValue(flag({ enabled: true }));
    renderPage();

    await userEvent.click(await screen.findByRole('switch', { name: 'streak_calendar global' }));

    expect(api.setFlagGlobal).toHaveBeenCalledWith('streak_calendar', true);
    await waitFor(() => expect(screen.getByRole('switch', { name: 'streak_calendar global' })).toBeChecked());
  });

  it('sets and clears a role override', async () => {
    authState.canManageFlags = true;
    vi.mocked(api.setFlagRoleOverride).mockResolvedValue(
      flag({ roleOverrides: [{ roleKey: 'admin', enabled: true }] }),
    );
    vi.mocked(api.clearFlagRoleOverride).mockResolvedValue(flag());
    renderPage();

    const adminGroup = within(await screen.findByRole('group', { name: 'admin override' }));

    await userEvent.click(adminGroup.getByRole('button', { name: 'On' }));
    expect(api.setFlagRoleOverride).toHaveBeenCalledWith('streak_calendar', 'admin', true);
    await waitFor(() => expect(adminGroup.getByRole('button', { name: 'On' })).toHaveClass('active'));

    await userEvent.click(adminGroup.getByRole('button', { name: 'Default' }));
    expect(api.clearFlagRoleOverride).toHaveBeenCalledWith('streak_calendar', 'admin');
  });

  it('adds a user override by email (resolving the id via user search)', async () => {
    authState.canManageFlags = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({
      items: [{ id: 7, email: 'bob@x.com', roles: [] }],
      nextCursor: null,
    });
    vi.mocked(api.setFlagUserOverride).mockResolvedValue(
      flag({ userOverrides: [{ userId: 7, email: 'bob@x.com', enabled: true }] }),
    );
    renderPage();

    await screen.findByText('streak_calendar');
    await userEvent.type(screen.getByLabelText('Add user override for streak_calendar'), 'bob@x.com');
    await userEvent.click(screen.getByRole('button', { name: '+ On' }));

    await waitFor(() => expect(api.getAdminUsers).toHaveBeenCalledWith({ q: 'bob@x.com' }));
    await waitFor(() => expect(api.setFlagUserOverride).toHaveBeenCalledWith('streak_calendar', 7, true));
    expect(await screen.findByText('bob@x.com')).toBeInTheDocument();
  });
});
