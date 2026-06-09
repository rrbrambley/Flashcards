import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { BackHeader } from './BackHeader';
import { DeckForm } from './DeckForm';

export function CreateDeckPage() {
  const navigate = useNavigate();
  // `?global=1` creates an ownerless catalog deck (admins only; the button is gated, the API enforces).
  const isGlobal = useSearchParams()[0].get('global') === '1';

  return (
    <div className="app">
      <BackHeader
        title={isGlobal ? 'New global deck' : 'Create deck'}
        backTo={isGlobal ? '/library/global' : '/library'}
        backLabel={isGlobal ? 'Global decks' : 'Library'}
      />
      <main className="container">
        <DeckForm
          submitLabel={isGlobal ? 'Create global deck' : 'Create deck'}
          onSubmit={async (title, flashcards, tags) => {
            await (isGlobal
              ? api.createGlobalDeck({ title, flashcards, tags })
              : api.createDeck({ title, flashcards, tags }));
            navigate(isGlobal ? '/library/global' : '/library');
          }}
        />
      </main>
    </div>
  );
}
