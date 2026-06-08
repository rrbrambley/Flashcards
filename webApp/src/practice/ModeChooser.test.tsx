import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom';
import { ModeChooser } from './ModeChooser';
import { PRACTICE_MODES } from './modes';

function RunnerStub() {
  const [params] = useSearchParams();
  return <div>runner mode={params.get('mode')}</div>;
}

describe('ModeChooser', () => {
  it('lists the registered modes and routes the choice with ?mode=', async () => {
    render(
      <MemoryRouter initialEntries={['/decks/5/practice/choose']}>
        <Routes>
          <Route path="/decks/:id/practice/choose" element={<ModeChooser deckId={5} />} />
          <Route path="/decks/:id/practice" element={<RunnerStub />} />
        </Routes>
      </MemoryRouter>,
    );

    // Every registered mode is offered.
    for (const mode of PRACTICE_MODES) {
      expect(screen.getByText(mode.label)).toBeInTheDocument();
    }

    // Picking the first mode routes to the runner carrying that mode key.
    await userEvent.click(screen.getByText(PRACTICE_MODES[0].label));
    expect(await screen.findByText(`runner mode=${PRACTICE_MODES[0].key}`)).toBeInTheDocument();
  });
});
