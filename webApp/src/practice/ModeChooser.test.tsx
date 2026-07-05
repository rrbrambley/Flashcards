import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom';
import { ModeChooser } from './ModeChooser';
import { PRACTICE_MODES } from './modes';

function RunnerStub() {
  const [params] = useSearchParams();
  return (
    <div>
      <span>mode={params.get('mode')}</span>
      <span>shuffle={params.get('shuffle')}</span>
    </div>
  );
}

function renderChooser() {
  render(
    <MemoryRouter initialEntries={['/decks/5/practice/choose']}>
      <Routes>
        <Route path="/decks/:id/practice/choose" element={<ModeChooser deckId={5} />} />
        <Route path="/decks/:id/practice" element={<RunnerStub />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ModeChooser', () => {
  it('lists the registered modes and routes the choice with ?mode= and shuffle on by default', async () => {
    renderChooser();

    // Every registered mode is offered.
    for (const mode of PRACTICE_MODES) {
      expect(screen.getByText(mode.label)).toBeInTheDocument();
    }

    // Shuffle defaults On (FLA-200): picking a mode routes with shuffle=1.
    await userEvent.click(screen.getByText(PRACTICE_MODES[0].label));
    expect(await screen.findByText(`mode=${PRACTICE_MODES[0].key}`)).toBeInTheDocument();
    expect(screen.getByText('shuffle=1')).toBeInTheDocument();
  });

  it('routes with shuffle=0 when the toggle is turned off', async () => {
    renderChooser();

    await userEvent.click(screen.getByRole('checkbox', { name: /Shuffle/ }));
    await userEvent.click(screen.getByText(PRACTICE_MODES[0].label));

    expect(await screen.findByText('shuffle=0')).toBeInTheDocument();
  });
});
