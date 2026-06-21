import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { useAuth } from '../auth/auth-context';
import { BackHeader } from './BackHeader';
import { DeckForm } from './DeckForm';
import { DeckSettings } from './DeckSettings';

export function EditDeckPage() {
  const { id } = useParams();
  const deckId = Number(id);
  const navigate = useNavigate();
  const { can } = useAuth();
  const canManageGlobal = can('manage_global_decks');
  const canManageDiscussions = can('manage_discussions');
  // Return to whichever list we came from — the admin global catalog if that's the origin,
  // otherwise the personal library (also the default for deep links / a missing referrer).
  const fromGlobal = (useLocation().state as { from?: string } | null)?.from === '/library/global';
  const backTo = fromGlobal ? '/library/global' : '/library';
  const backLabel = fromGlobal ? 'Global decks' : 'Library';
  const [deck, setDeck] = useState<FlashcardDeckDto | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api
      .getDeck(deckId)
      .then((d) => {
        if (active) setDeck(d);
      })
      .catch((err) => {
        if (active) setLoadError(err instanceof Error ? err.message : 'Could not load the deck.');
      });
    return () => {
      active = false;
    };
  }, [deckId]);

  const handleDelete = async () => {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api.deleteDeck(deckId);
      navigate(backTo);
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Could not delete the deck.');
      setDeleting(false);
    }
  };

  // Delete only applies to decks the user owns; the global catalog deck is read-only/undeletable.
  const canDelete = deck != null && deck.editable !== false;

  return (
    <div className="app">
      <BackHeader title="Edit deck" backTo={backTo} backLabel={backLabel} />
      <main className="container">
        {loadError ? (
          <p className="error">{loadError}</p>
        ) : !deck ? (
          <p className="muted">Loading…</p>
        ) : (
          <>
            <DeckForm
              submitLabel="Save changes"
              initialTitle={deck.title}
              initialCategory={deck.tags?.[0] ?? ''}
              readOnly={deck.editable === false}
              settingsSlot={
                canManageGlobal || canManageDiscussions ? (
                  <DeckSettings
                    deck={deck}
                    canManageGlobal={canManageGlobal}
                    canManageDiscussions={canManageDiscussions}
                  />
                ) : undefined
              }
              initialCards={deck.flashcards.map((f) => ({
                term: f.question,
                definition: f.answer,
                imageUrl: f.imageUrl,
                alternativeAnswers: f.alternativeAnswers,
                cardUid: f.cardUid,
              }))}
              onSubmit={async (title, flashcards, tags) => {
                try {
                  await api.updateDeck(deckId, { title, flashcards, tags });
                } catch (err) {
                  // The seeded global deck isn't owned by the user, so the server rejects edits.
                  if (err instanceof ApiError && err.status === 404) {
                    throw new Error("This deck can't be edited.", { cause: err });
                  }
                  throw err;
                }
                navigate(backTo);
              }}
            />
            {canDelete && (
              <section className="danger-zone">
                {deleteError && <p className="error">{deleteError}</p>}
                {confirmingDelete ? (
                  <div className="confirm-delete">
                    <p>Delete "{deck.title}" and its cards? This can't be undone.</p>
                    <div className="confirm-delete-actions">
                      <button className="danger" onClick={handleDelete} disabled={deleting}>
                        {deleting ? 'Deleting…' : 'Delete deck'}
                      </button>
                      <button className="secondary" onClick={() => setConfirmingDelete(false)} disabled={deleting}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <button className="danger" onClick={() => setConfirmingDelete(true)}>
                    Delete deck
                  </button>
                )}
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}
