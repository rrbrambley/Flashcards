import { useEffect } from 'react';

/**
 * Warns before leaving the page while [active] — an in-progress single-sitting practice run whose
 * progress isn't saved (#307). Covers both ways off the page: a native `beforeunload` prompt for tab
 * close / reload / external navigation, and a Back-button trap for SPA history pops (the app uses a
 * plain BrowserRouter, which has no navigation blocker) that confirms with [message] before leaving.
 * Inert when [active] is false, so a completed run (or a normal, resumable run) exits freely.
 */
export function useExitGuard(active: boolean, message: string): void {
  useEffect(() => {
    if (!active) return;

    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);

    // Push a decoy history entry so the first Back press pops it (staying on the page); confirm before
    // actually leaving. Re-push on cancel to keep the trap armed.
    window.history.pushState(null, '', window.location.href);
    const onPopState = () => {
      if (window.confirm(message)) {
        window.removeEventListener('popstate', onPopState);
        window.history.back();
      } else {
        window.history.pushState(null, '', window.location.href);
      }
    };
    window.addEventListener('popstate', onPopState);

    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      window.removeEventListener('popstate', onPopState);
    };
  }, [active, message]);
}
