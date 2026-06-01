import { useEffect, useRef } from 'react';
import { useAuth } from './auth-context';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_WEB_CLIENT_ID;

// Minimal typing for the Google Identity Services global.
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

const GIS_SRC = 'https://accounts.google.com/gsi/client';

// `google.accounts.id.initialize()` is global and only keeps the last config, so it must
// run exactly once for the page. These module-level holders let a single init delegate to
// the current component's callbacks (which change across renders/remounts).
let gisInitialized = false;
let handleCredential: (idToken: string) => void = () => {};

export function GoogleButton({ onError }: { onError: (message: string) => void }) {
  const { googleSignIn } = useAuth();
  const containerRef = useRef<HTMLDivElement>(null);

  // Point the (single) global GIS callback at the latest handlers. Done in an effect
  // — not during render — since it mutates module-level state.
  useEffect(() => {
    handleCredential = (idToken: string) => {
      googleSignIn(idToken).catch((err: unknown) => {
        onError(err instanceof Error ? err.message : 'Google sign-in failed.');
      });
    };
  });

  useEffect(() => {
    const element = containerRef.current;
    if (!CLIENT_ID || !element) return;

    const render = () => {
      if (!window.google) return;
      if (!gisInitialized) {
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (response) => handleCredential(response.credential),
        });
        gisInitialized = true;
      }
      window.google.accounts.id.renderButton(element, { theme: 'outline', size: 'large', width: 320 });
    };

    if (window.google) {
      render();
      return;
    }

    const existing = document.getElementById('gis-script') as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', render, { once: true });
      return;
    }

    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.id = 'gis-script';
    script.onload = render;
    document.body.appendChild(script);
  }, []);

  if (!CLIENT_ID) {
    return <p className="muted">Google sign-in isn't configured.</p>;
  }
  return <div ref={containerRef} className="google-button" />;
}
