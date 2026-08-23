import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { ModeChooser } from './ModeChooser';
import { PRACTICE_MODES } from './modes';
import { api } from '../api/client';
import { installFakeSpeechRecognition } from '../test/fakeSpeechRecognition';
import { VOICE_INPUT_KEY } from './voice/preference';

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
      <span>questions={params.get('questions') ?? ''}</span>
      <span>gradeAtEnd={params.get('gradeAtEnd') ?? ''}</span>
      <span>timeLimit={params.get('timeLimit') ?? ''}</span>
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

  it('picks a mode, then starts from that mode\'s settings step with shuffle on by default', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    // Step 1 is only the modes: no settings, and no disabled Start to explain (#410).
    expect(screen.queryByRole('button', { name: 'Start practice' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Shuffle/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));

    // Picking a mode advances rather than starting — the run is still configurable.
    expect(screen.getByRole('checkbox', { name: /Shuffle/ })).toBeInTheDocument();
    expect(screen.queryByText(/^mode=/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));
    // Shuffle defaults On (FLA-200) → shuffle=1.
    expect(await screen.findByText(`mode=${PRACTICE_MODES[0].key}`)).toBeInTheDocument();
    expect(screen.getByText('shuffle=1')).toBeInTheDocument();
  });

  it('goes back to the mode list without starting, so a wrong pick is recoverable', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('button', { name: /Choose a different mode/ }));

    expect(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[1].label) })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start practice' })).not.toBeInTheDocument();
    expect(screen.queryByText(/^mode=/)).not.toBeInTheDocument();
  });

  it('routes with shuffle=0 when the toggle is turned off', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Shuffle/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('shuffle=0')).toBeInTheDocument();
  });

  it('shows the Questions field with the deck max and routes a subset (FLA-219)', async () => {
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      flashcards: Array.from({ length: 10 }, () => ({ question: 'q', answer: 'a' })),
    } as never);
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));

    // Defaults to the deck's card count (practice everything) with the max in the label.
    const field = screen.getByLabelText('Questions (max 10)');
    expect(field).toHaveValue(10);

    // Ask for a subset of 4, then start.
    await userEvent.clear(field);
    await userEvent.type(field, '4');
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('questions=4')).toBeInTheDocument();
  });

  it('omits the questions param when the whole deck is chosen (FLA-219)', async () => {
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      flashcards: Array.from({ length: 10 }, () => ({ question: 'q', answer: 'a' })),
    } as never);
    renderChooser();
    await screen.findByText('Practice Spanish');

    // Leave the field at its max (10) → no subset → no questions param.
    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText(`mode=${PRACTICE_MODES[0].key}`)).toBeInTheDocument();
    expect(screen.getByText('questions=')).toBeInTheDocument(); // empty → absent
  });

  it('hides the Questions field when its flag is disabled (FLA-219)', async () => {
    mockFlags = { practice_question_count: false };
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      flashcards: Array.from({ length: 10 }, () => ({ question: 'q', answer: 'a' })),
    } as never);
    renderChooser();
    await screen.findByText('Practice Spanish');

    expect(screen.queryByLabelText(/Questions/)).not.toBeInTheDocument();
  });

  it('offers Grade-at-the-end for Test/Multiple Choice and routes gradeAtEnd=1 (#293)', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: /Test/ }));

    // On Test's step it's simply present — no disabled state to reason about (#410).
    const toggle = screen.getByRole('checkbox', { name: /Grade at the end/ });
    await userEvent.click(toggle);
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('gradeAtEnd=1')).toBeInTheDocument();
  });

  it('omits Grade-at-the-end from Classic, rather than showing it disabled (#293, #410)', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: /Classic/ }));
    // Classic is a self-graded flip, so there's nothing to defer — and nothing to grey out.
    expect(screen.queryByRole('checkbox', { name: /Grade at the end/ })).not.toBeInTheDocument();
  });

  it('hides Grade-at-the-end when its flag is disabled (#293)', async () => {
    mockFlags = { practice_grade_at_end: false };
    renderChooser();
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: /Test/ }));
    expect(screen.queryByRole('checkbox', { name: /Grade at the end/ })).not.toBeInTheDocument();
  });

  it('offers a Timed toggle + mm:ss field and routes timeLimit=N (#289)', async () => {
    renderChooser();
    await screen.findByText('Practice Spanish');
    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));

    // The mm:ss field is revealed only once Timed is on.
    expect(screen.queryByLabelText('Minutes')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /Timed/ }));

    const minutes = screen.getByLabelText('Minutes');
    const seconds = screen.getByLabelText('Seconds');
    await userEvent.clear(minutes);
    await userEvent.type(minutes, '1');
    await userEvent.clear(seconds);
    await userEvent.type(seconds, '30');
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    // 1:30 → 90 total seconds.
    expect(await screen.findByText('timeLimit=90')).toBeInTheDocument();
  });

  it('hides the Timed toggle when its flag is disabled (#289)', async () => {
    mockFlags = { practice_timer: false };
    renderChooser();
    await screen.findByText('Practice Spanish');

    expect(screen.queryByRole('checkbox', { name: /Timed/ })).not.toBeInTheDocument();
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

  it('shows an empty note and offers no way forward when all modes are disabled (FLA-213)', async () => {
    mockFlags = { practice_mode_classic: false, practice_mode_test: false, practice_mode_multiple_choice: false };
    renderChooser();
    await screen.findByText('Practice Spanish');

    expect(screen.getByText('No practice modes are available right now.')).toBeInTheDocument();
    // Nothing to pick, so there's no way forward — and no disabled Start to explain.
    expect(screen.queryByRole('button', { name: 'Start practice' })).not.toBeInTheDocument();
  });

  describe('answering by voice (#387)', () => {
    let uninstallVoice: () => void;
    beforeEach(() => {
      uninstallVoice = installFakeSpeechRecognition();
      localStorage.removeItem(VOICE_INPUT_KEY);
      mockFlags = { practice_voice_input: true };
    });
    afterEach(() => uninstallVoice());

    const voiceToggle = () => screen.getByRole('checkbox', { name: /Answer by voice/ });

    it('is offered once a mode that takes an answer is picked, and remembers the choice', async () => {
      renderChooser();
      await screen.findByText('Practice Spanish');

      await userEvent.click(screen.getByRole('button', { name: /Test/ }));
      expect(voiceToggle()).toBeEnabled();

      await userEvent.click(voiceToggle());
      expect(voiceToggle()).toBeChecked();
      // The preference is local, not on the session — so it has to outlive this screen (#387).
      expect(localStorage.getItem(VOICE_INPUT_KEY)).toBe('true');
    });

    it('is absent from Classic, which is a self-graded flip with nothing to say', async () => {
      renderChooser();
      await screen.findByText('Practice Spanish');

      await userEvent.click(screen.getByRole('button', { name: /Classic/ }));
      // No longer needs scoping by accessible name: the caption it collided with is gone, because
      // neither control is rendered on a step where it doesn't apply (#410).
      expect(screen.queryByRole('checkbox', { name: /Answer by voice/ })).not.toBeInTheDocument();
    });

    it('is offered for Multiple Choice too, which also has an answer to speak', async () => {
      renderChooser();
      await screen.findByText('Practice Spanish');

      await userEvent.click(screen.getByRole('button', { name: /Multiple Choice/ }));
      expect(voiceToggle()).toBeEnabled();
    });

    it('says so rather than hiding when the browser has no recogniser (Firefox)', async () => {
      uninstallVoice();
      renderChooser();
      await screen.findByText('Practice Spanish');

      await userEvent.click(screen.getByRole('button', { name: /Test/ }));
      expect(voiceToggle()).toBeDisabled();
      expect(voiceToggle()).toHaveAccessibleName(/Not supported in this browser/);
    });

    it('is absent when the flag is off', async () => {
      mockFlags = { practice_voice_input: false };
      renderChooser();
      await screen.findByText('Practice Spanish');

      expect(screen.queryByRole('checkbox', { name: /Answer by voice/ })).not.toBeInTheDocument();
    });

    /**
     * Guests carry no flags, so the `isGuest ||` idiom the other settings use would ship this
     * default-OFF feature to every signed-out visitor while hiding it from signed-in users.
     */
    it('is absent for guests, unlike the default-on kill switches', async () => {
      mockToken = null;
      renderChooser();
      await screen.findByText('Practice Spanish');
      await userEvent.click(screen.getByRole('button', { name: /Test/ }));

      // On a settings step that does carry other toggles, so this is a real absence, not an
      // empty screen.
      expect(screen.getByText('Shuffle cards')).toBeInTheDocument();
      expect(screen.queryByRole('checkbox', { name: /Answer by voice/ })).not.toBeInTheDocument();
    });
  });

  it('forwards the practice origin to the runner so "back" returns there (FLA-168)', async () => {
    renderChooser('/library');
    await screen.findByText('Practice Spanish');

    await userEvent.click(screen.getByRole('button', { name: new RegExp(PRACTICE_MODES[0].label) }));
    await userEvent.click(screen.getByRole('button', { name: 'Start practice' }));

    expect(await screen.findByText('from=/library')).toBeInTheDocument();
  });
});
