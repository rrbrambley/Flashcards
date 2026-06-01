import { useEffect, useRef } from 'react';
import { useAuth } from './AuthContext';

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

export function GoogleButton({ onError }: { onError: (message: string) => void }) {
  const { googleSignIn } = useAuth();
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!CLIENT_ID || !containerRef.current) return;

    const renderInto = (element: HTMLElement) => {
      if (!window.google) return;
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: (response) => {
          googleSignIn(response.credential).catch((err: unknown) => {
            onError(err instanceof Error ? err.message : 'Google sign-in failed.');
          });
        },
      });
      window.google.accounts.id.renderButton(element, { theme: 'outline', size: 'large', width: 320 });
    };

    const element = containerRef.current;
    if (window.google) {
      renderInto(element);
      return;
    }

    const existing = document.getElementById('gis-script') as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', () => renderInto(element), { once: true });
      return;
    }

    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.id = 'gis-script';
    script.onload = () => renderInto(element);
    document.body.appendChild(script);
  }, [googleSignIn, onError]);

  if (!CLIENT_ID) {
    return <p className="muted">Google sign-in isn't configured.</p>;
  }
  return <div ref={containerRef} className="google-button" />;
}
