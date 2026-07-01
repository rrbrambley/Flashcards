import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SettingsPage } from './SettingsPage';
import { api } from '../api/client';
import type { AvatarOption, MeResponse } from '../api/types';

vi.mock('../api/client', () => ({
  api: { getMe: vi.fn(), updateProfile: vi.fn(), getAvatars: vi.fn(), getStreakCalendar: vi.fn() },
}));

const setProfile = vi.fn();
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ setProfile }) }));

const me = (over: Partial<MeResponse> = {}): MeResponse => ({
  userId: 1,
  email: 'rob@example.com',
  roles: [],
  permissions: [],
  displayName: null,
  avatarKey: null,
  avatarUrl: null,
  ...over,
});

const catalog: AvatarOption[] = [
  { key: 'dragon', url: 'https://cdn.test/avatars/dragon.png' },
  { key: 'yeti', url: 'https://cdn.test/avatars/yeti.png' },
];

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getAvatars).mockResolvedValue([]);
    // SettingsPage embeds the StreakCalendar, which fetches on mount.
    vi.mocked(api.getStreakCalendar).mockResolvedValue({ month: '2026-07', activeDays: [], current: 0, longest: 0 });
  });

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

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({ displayName: 'Rob B' }));
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

  it('lists the avatar catalog and PATCHes the chosen key', async () => {
    vi.mocked(api.getMe).mockResolvedValue(me());
    vi.mocked(api.getAvatars).mockResolvedValue(catalog);
    vi.mocked(api.updateProfile).mockResolvedValue(
      me({ avatarKey: 'dragon', avatarUrl: 'https://cdn.test/avatars/dragon.png' }),
    );
    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByRole('radio', { name: 'dragon' }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({ avatarKey: 'dragon' }));
    // The chosen option becomes selected and the in-memory profile is updated for the header.
    await waitFor(() => expect(screen.getByRole('radio', { name: 'dragon' })).toBeChecked());
    expect(setProfile).toHaveBeenCalledWith({
      displayName: null,
      avatarUrl: 'https://cdn.test/avatars/dragon.png',
    });
  });

  it('clears the avatar via remove and hides the picker when the catalog is empty', async () => {
    vi.mocked(api.getMe).mockResolvedValue(
      me({ avatarKey: 'dragon', avatarUrl: 'https://cdn.test/avatars/dragon.png' }),
    );
    vi.mocked(api.getAvatars).mockResolvedValue([]); // no CDN → empty catalog
    vi.mocked(api.updateProfile).mockResolvedValue(me());
    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    );

    const remove = await screen.findByRole('button', { name: 'Remove avatar' });
    // Picker is hidden without a catalog, but the user can still clear an existing avatar.
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();

    await userEvent.click(remove);
    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({ avatarKey: '' }));
  });
});
