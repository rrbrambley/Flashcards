import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthForm } from './auth/AuthForm';
import { useAuth } from './auth/auth-context';
import { HomePage } from './home/HomePage';
import { LibraryPage } from './decks/LibraryPage';
import { GlobalLibraryPage } from './decks/GlobalLibraryPage';
import { CatalogPage } from './decks/CatalogPage';
import { AdminUsersPage } from './admin/AdminUsersPage';
import { AdminDiscussionsPage } from './admin/AdminDiscussionsPage';
import { AdminSuggestionsPage } from './admin/AdminSuggestionsPage';
import { AdminFlagsPage } from './admin/AdminFlagsPage';
import { CreateDeckPage } from './decks/CreateDeckPage';
import { EditDeckPage } from './decks/EditDeckPage';
import { PracticePage } from './practice/PracticePage';
import { SettingsPage } from './settings/SettingsPage';

export default function App() {
  const { token } = useAuth();

  // Guest mode (FLA-101): no account → browse the public catalog and practice a deck (session-less),
  // with sign-in/register available. Deck creation, the library, home feed, etc. stay sign-in gated.
  if (!token) {
    return (
      <Routes>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/decks/:id/practice" element={<PracticePage />} />
        <Route path="/login" element={<AuthForm mode="login" />} />
        <Route path="/register" element={<AuthForm mode="register" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/library" element={<LibraryPage />} />
      <Route path="/library/global" element={<GlobalLibraryPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="/admin/discussions" element={<AdminDiscussionsPage />} />
      <Route path="/admin/suggestions" element={<AdminSuggestionsPage />} />
      <Route path="/admin/flags" element={<AdminFlagsPage />} />
      <Route path="/create" element={<CreateDeckPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/decks/:id/edit" element={<EditDeckPage />} />
      <Route path="/decks/:id/practice" element={<PracticePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
