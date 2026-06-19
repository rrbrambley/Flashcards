import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SettingsPage } from './SettingsPage';
import { api } from '../api/client';
import type { MeResponse } from '../api/types';

vi.mock('../api/client', () => ({ api: { getMe: vi.fn(), updateProfile: vi.fn() } }));

const me = (over: Partial<MeResponse> = {}): MeResponse => ({
  userId: 1,
  email: 'rob@example.com',
  roles: [],
  permissions: [],
  displayName: null,
  ...over,
});

describe('SettingsPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('loads the profile (blank name placeholders the email prefix) and saves a new display name', async () => {
    vi.mocked(api.getMe).mockResolvedValue(me());
    vi.mocked(api.updateProfile).mockResolvedValue(me({ displayName: 'Rob B' }));
    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    );

    const input = await screen.findByLabelText(/Display name/);
    expect(input).toHaveValue('');
    expect(input).toHaveAttribute('placeholder', 'rob');

    await userEvent.type(input, 'Rob B');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith('Rob B'));
    expect(await screen.findByText('Saved.')).toBeInTheDocument();
  });

  it('pre-fills an existing display name', async () => {
    vi.mocked(api.getMe).mockResolvedValue(me({ displayName: 'Existing Name' }));
    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText(/Display name/)).toHaveValue('Existing Name');
  });
});
