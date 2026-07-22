import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * The detail-screen header with a back affordance and optional trailing slot. By default the back
 * control is a [Link] to [backTo]; pass [onBack] to intercept it instead (e.g. to confirm before
 * leaving an unsaved guest practice session).
 */
export function BackHeader({
  title,
  backTo = '/library',
  backLabel = 'Library',
  onBack,
  hideBack = false,
  right,
}: {
  title: string;
  backTo?: string;
  backLabel?: string;
  onBack?: () => void;
  // Omit the back control entirely — e.g. an in-progress single-sitting practice run that shouldn't
  // offer a casual exit (#307). A leading spacer keeps the title centered.
  hideBack?: boolean;
  right?: ReactNode;
}) {
  return (
    <header className="app-header">
      {hideBack ? (
        <span className="header-spacer" />
      ) : onBack ? (
        <button type="button" className="link-btn" onClick={onBack}>
          ← {backLabel}
        </button>
      ) : (
        <Link to={backTo} className="link-btn">
          ← {backLabel}
        </Link>
      )}
      <h1>{title}</h1>
      {right ?? <span className="header-spacer" />}
    </header>
  );
}
