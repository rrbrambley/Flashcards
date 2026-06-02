import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { BackHeader } from './BackHeader';
import { DeckForm } from './DeckForm';

export function CreateDeckPage() {
  const navigate = useNavigate();
  return (
    <div className="app">
      <BackHeader title="Create deck" />
      <main className="container">
        <DeckForm
          submitLabel="Create deck"
          onSubmit={async (title, flashcards) => {
            await api.createDeck({ title, flashcards });
            navigate('/library');
          }}
        />
      </main>
    </div>
  );
}
