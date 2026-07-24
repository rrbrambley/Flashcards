import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PromptImage } from './PromptImage';

describe('PromptImage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reports ready when the image loads', () => {
    const onReady = vi.fn();
    render(<PromptImage src="flag.svg" alt="a flag" onReady={onReady} />);
    fireEvent.load(screen.getByAltText('a flag'));
    expect(onReady).toHaveBeenCalledTimes(1);
  });

  it('reports ready on error too — a broken image must not strand the timer paused', () => {
    const onReady = vi.fn();
    render(<PromptImage src="broken.svg" alt="a flag" onReady={onReady} />);
    fireEvent.error(screen.getByAltText('a flag'));
    expect(onReady).toHaveBeenCalledTimes(1);
  });

  it('reports ready at most once', () => {
    const onReady = vi.fn();
    render(<PromptImage src="flag.svg" alt="a flag" onReady={onReady} />);
    const img = screen.getByAltText('a flag');
    fireEvent.load(img);
    fireEvent.load(img);
    fireEvent.error(img);
    expect(onReady).toHaveBeenCalledTimes(1);
  });

  it('reports ready immediately for an already-cached (complete) image', () => {
    const onReady = vi.fn();
    // Simulate a cached image: `complete` is already true before React attaches onLoad, so onLoad
    // never fires — the mount check must catch it (the web analog of the Android cached-image bug).
    const desc = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'complete');
    Object.defineProperty(HTMLImageElement.prototype, 'complete', { configurable: true, get: () => true });
    try {
      render(<PromptImage src="cached.svg" alt="a flag" onReady={onReady} />);
      expect(onReady).toHaveBeenCalledTimes(1);
    } finally {
      if (desc) Object.defineProperty(HTMLImageElement.prototype, 'complete', desc);
    }
  });
});
