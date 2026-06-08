import { Link } from 'react-router-dom';

export function BackHeader({
  title,
  backTo = '/library',
  backLabel = 'Library',
}: {
  title: string;
  backTo?: string;
  backLabel?: string;
}) {
  return (
    <header className="app-header">
      <Link to={backTo} className="link-btn">
        ← {backLabel}
      </Link>
      <h1>{title}</h1>
      <span className="header-spacer" />
    </header>
  );
}
