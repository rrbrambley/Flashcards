import { useEffect, useRef } from 'react';

interface PromptImageProps {
  src: string;
  alt: string;
  className?: string;
  /**
   * Called once when the image has settled — loaded or errored. A timed run uses this to resume its
   * countdown (paused while the prompt image loads, #317). Both outcomes report ready: a broken image
   * shouldn't strand the timer paused forever.
   */
  onReady?: () => void;
}

/**
 * The card's prompt image, with load reporting for the timed-practice "pause while loading" flow
 * (#317). Fires [onReady] exactly once, on load or error — and handles the **already-cached** case:
 * a cached image can be `complete` before React attaches the `onLoad` handler, so `onLoad` never
 * fires; we check `img.complete` on mount and report immediately (the web analog of the Android
 * cached-image gotcha, #309/#310).
 */
export function PromptImage({ src, alt, className, onReady }: PromptImageProps) {
  const ref = useRef<HTMLImageElement>(null);
  const reported = useRef(false);

  const report = () => {
    if (reported.current) return;
    reported.current = true;
    onReady?.();
  };

  useEffect(() => {
    reported.current = false;
    // `complete` is true once the fetch settles either way (cached success or cached failure), so a
    // cached image that beat the handler still resumes the timer.
    if (ref.current?.complete) report();
    // report is stable enough for this one-shot; re-run only when the source changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src]);

  return <img ref={ref} src={src} alt={alt} className={className} onLoad={report} onError={report} />;
}
