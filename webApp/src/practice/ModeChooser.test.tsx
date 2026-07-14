import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { ModeChooser } from './ModeChooser';
import { PRACTICE_MODES } from './modes';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  api: { getDeck: vi.fn(), getCatalogDeck: vi.fn() },
}));

let mockToken: string | null = 'test-token';
// Practice-mode flags default on (like a fresh signed-in user); a test opts a mode out by setting false.
let mockFlags: Record<string, boolean> = {};
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ token: mockToken, isEnabled: (key: string) => mockFlags[key] !== false }),
}));

function RunnerStub() {
  const [params] = useSearchParams();
  const from = (useLocation().state as { from?: string } | null)?.from;
  return (
    <div>
      <span>mode={params.get('mode')}</span>
      <span>shuffle={params.get('shuffle')}</span>
      <span>from={from ?? ''}</span>
    </div>
  );
}

function renderChooser(from?: string) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/decks/5/practice/choose', state: from ? { from } : undefined }]}>
      <Routes>
        <Route path="/decks/:id/practice/choose" element={<ModeChooser deckId={5} />} />
        <Route path="/decks/:id/practice" element={<RunnerStub />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ModeChooser', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockToken = 'test-token';
    mockFlags = {};
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: [] });
    vi.mocked(api.getCatalogDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: false, flashcards: [] });
  });

  it('titles the header with the deck name and lists the registered modes', async () => {
    renderChooser();

    expect(await screen.findByText('Practice Spanish')).toBeInTheDocument();
    for (const mode of PRACTICE_MODES) {
      expect(screen.getByText(mode.label)).toBeInTheDocument();
    }
  });

  it('does not start until a mode is selected, then routes with the mode + shuffle on by default', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    // Start is disabled until a mode is picked (selecting a mode does not auto-start).
    const start = screen.getByRole('button', { name: 'Start practice' });
    expect(start).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: new RegExp(PRACTICE_MODES[0].label) }));
    expect(start).toBeEnabled();
    // Still on the config screen (no auto-navigation on select).
    expect(screen.queryByText(/^mode=/)).not.toBeInTheDocument();

    await userEvent.click(start);
    // Shuffle defaults On (FLA-200) → shuffle=1.
    expect(await screen.findByText(`mode=${PRACTICE_MODES[0].key}`)).toBeInTheDocument();
    expect(screen.getByText('shuffle=1')).toBeInTheDocument();
  });

  it('routes with shuffle=0 when the toggle is turned off', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('radio', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Shuffle/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('shuffle=0')).toBeInTheDocument();
  });

  it('hides a mode whose feature flag is disabled (FLA-213)', async () => {
    mockFlags = { practice_mode_test: false };
    renderChooser();
    await screen.findByText('Practice Spanish');

    expect(screen.queryByText('Test')).not.toBeInTheDocument();
    expect(screen.getByText('Classic')).toBeInTheDocument();
    expect(screen.getByText('Multiple Choice')).toBeInTheDocument();
  });

  it('shows every mode to a guest, who carries no flags (FLA-213)', async () => {
    mockToken = null;
    mockFlags = { practice_mode_test: false }; // ignored for guests
    renderChooser();
    await screen.findByText('Practice Spanish');

    for (const mode of PRACTICE_MODES) {
      expect(screen.getByText(mode.label)).toBeInTheDocument();
    }
  });

  it('shows an empty note and keeps Start disabled when all modes are disabled (FLA-213)', async () => {
    mockFlags = { practice_mode_classic: false, practice_mode_test: false, practice_mode_multiple_choice: false };
    renderChooser();
    await screen.findByText('Practice Spanish');

    expect(screen.getByText('No practice modes are available right now.')).toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start practice' })).toBeDisabled();
  });

  it('forwards the practice origin to the runner so "back" returns there (FLA-168)', async () => {
    renderChooser('/library');
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('radio', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('from=/library')).toBeInTheDocument();
  });
});
