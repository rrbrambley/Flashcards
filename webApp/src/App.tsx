import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthForm } from './auth/AuthForm';
import { useAuth } from './auth/AuthContext';
import { HomePage } from './decks/HomePage';

export default function App() {
  const { token } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/" replace /> : <AuthForm mode="login" />} />
      <Route path="/register" element={token ? <Navigate to="/" replace /> : <AuthForm mode="register" />} />
      <Route path="/" element={token ? <HomePage /> : <Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
