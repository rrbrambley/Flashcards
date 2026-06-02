import { Link } from 'react-router-dom';

export function BackHeader({ title }: { title: string }) {
  return (
    <header className="app-header">
      <Link to="/library" className="link-btn">
        ← Library
      </Link>
      <h1>{title}</h1>
      <span className="header-spacer" />
    </header>
  );
}
