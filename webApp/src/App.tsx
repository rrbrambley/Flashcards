import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthForm } from './auth/AuthForm';
import { useAuth } from './auth/auth-context';
import { HomePage } from './home/HomePage';
import { LibraryPage } from './decks/LibraryPage';
import { GlobalLibraryPage } from './decks/GlobalLibraryPage';
import { AdminUsersPage } from './admin/AdminUsersPage';
import { CreateDeckPage } from './decks/CreateDeckPage';
import { EditDeckPage } from './decks/EditDeckPage';
import { PracticePage } from './practice/PracticePage';

export default function App() {
  const { token } = useAuth();

  if (!token) {
    return (
      <Routes>
        <Route path="/login" element={<AuthForm mode="login" />} />
        <Route path="/register" element={<AuthForm mode="register" />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/library" element={<LibraryPage />} />
      <Route path="/library/global" element={<GlobalLibraryPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="/create" element={<CreateDeckPage />} />
      <Route path="/decks/:id/edit" element={<EditDeckPage />} />
      <Route path="/decks/:id/practice" element={<PracticePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
