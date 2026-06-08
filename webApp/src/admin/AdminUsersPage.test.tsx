import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminUsersPage } from './AdminUsersPage';
import { api } from '../api/client';
import type { AdminUserDto } from '../api/types';

const authState = vi.hoisted(() => ({ canManageRoles: false }));

vi.mock('../api/client', () => ({
  api: { getAdminUsers: vi.fn(), getRoles: vi.fn(), grantRole: vi.fn(), revokeRole: vi.fn() },
}));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ signOut: vi.fn(), can: (p: string) => p === 'manage_roles' && authState.canManageRoles }),
}));

const ROLES = [
  { key: 'admin', description: 'Full administrative access', permissions: ['manage_global_decks', 'manage_roles'] },
  { key: 'user', description: 'A standard, non-privileged user', permissions: [] },
];

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/admin/users']}>
      <Routes>
        <Route path="/admin/users" element={<AdminUsersPage />} />
        <Route path="/library" element={<div>Personal library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function rowFor(email: string) {
  return within(screen.getByText(email).closest('li') as HTMLElement);
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageRoles = false;
    vi.mocked(api.getRoles).mockResolvedValue(ROLES);
  });

  it('redirects non-admins to the library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
    expect(api.getAdminUsers).not.toHaveBeenCalled();
  });

  it('lists users with their roles reflected in the chips', async () => {
    authState.canManageRoles = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({
      items: [
        { id: 1, email: 'admin@x.com', roles: ['admin'] },
        { id: 2, email: 'plain@x.com', roles: [] },
      ],
      nextCursor: null,
    });
    renderPage();

    expect(await screen.findByText('admin@x.com')).toBeInTheDocument();
    // The admin user's "admin" chip shows as granted (pressed); the plain user's does not.
    expect(rowFor('admin@x.com').getByRole('button', { name: /admin/ })).toHaveAttribute('aria-pressed', 'true');
    expect(rowFor('plain@x.com').getByRole('button', { name: /admin/ })).toHaveAttribute('aria-pressed', 'false');
  });

  it('grants a role and reflects the update in place', async () => {
    authState.canManageRoles = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({
      items: [{ id: 2, email: 'plain@x.com', roles: [] }],
      nextCursor: null,
    });
    const updated: AdminUserDto = { id: 2, email: 'plain@x.com', roles: ['admin'] };
    vi.mocked(api.grantRole).mockResolvedValue(updated);
    renderPage();

    await screen.findByText('plain@x.com');
    await userEvent.click(rowFor('plain@x.com').getByRole('button', { name: /admin/ }));

    expect(api.grantRole).toHaveBeenCalledWith(2, 'admin');
    await waitFor(() =>
      expect(rowFor('plain@x.com').getByRole('button', { name: /admin/ })).toHaveAttribute('aria-pressed', 'true'),
    );
  });

  it('revokes a granted role', async () => {
    authState.canManageRoles = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({
      items: [{ id: 1, email: 'admin@x.com', roles: ['admin'] }],
      nextCursor: null,
    });
    vi.mocked(api.revokeRole).mockResolvedValue({ id: 1, email: 'admin@x.com', roles: [] });
    renderPage();

    await screen.findByText('admin@x.com');
    await userEvent.click(rowFor('admin@x.com').getByRole('button', { name: /admin/ }));

    expect(api.revokeRole).toHaveBeenCalledWith(1, 'admin');
    await waitFor(() =>
      expect(rowFor('admin@x.com').getByRole('button', { name: /admin/ })).toHaveAttribute('aria-pressed', 'false'),
    );
  });

  it('surfaces a failed role change (e.g. last-admin lockout)', async () => {
    authState.canManageRoles = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({
      items: [{ id: 1, email: 'admin@x.com', roles: ['admin'] }],
      nextCursor: null,
    });
    vi.mocked(api.revokeRole).mockRejectedValue(new Error('Cannot revoke the last admin'));
    renderPage();

    await screen.findByText('admin@x.com');
    await userEvent.click(rowFor('admin@x.com').getByRole('button', { name: /admin/ }));

    expect(await screen.findByText('Cannot revoke the last admin')).toBeInTheDocument();
  });

  it('searches users by email on submit', async () => {
    authState.canManageRoles = true;
    vi.mocked(api.getAdminUsers).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();

    await screen.findByText(/No users/);
    expect(api.getAdminUsers).toHaveBeenLastCalledWith({ q: undefined, cursor: undefined });

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search users' }), 'bob');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(api.getAdminUsers).toHaveBeenLastCalledWith({ q: 'bob', cursor: undefined }));
  });
});
