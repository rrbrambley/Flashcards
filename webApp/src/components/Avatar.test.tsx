import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Avatar } from './Avatar';

describe('Avatar', () => {
  it('renders the image when a url is given', () => {
    render(<Avatar url="https://cdn.test/avatars/dragon.png" name="Rob Brambley" />);
    const img = screen.getByRole('img', { name: "Rob Brambley's avatar" });
    expect(img).toHaveAttribute('src', 'https://cdn.test/avatars/dragon.png');
    expect(img.tagName).toBe('IMG');
  });

  it('falls back to an initials monogram when there is no url', () => {
    render(<Avatar url={null} name="Rob Brambley" />);
    const monogram = screen.getByRole('img', { name: "Rob Brambley's avatar" });
    expect(monogram.tagName).toBe('SPAN');
    expect(monogram).toHaveTextContent('RB');
  });

  it('shows a placeholder glyph when there is neither url nor name', () => {
    render(<Avatar url={null} name={null} />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('gives the same name a stable monogram color', () => {
    const { container: a } = render(<Avatar url={null} name="Yeti Fan" />);
    const { container: b } = render(<Avatar url={null} name="Yeti Fan" />);
    const colorOf = (c: HTMLElement) => (c.querySelector('.avatar-monogram') as HTMLElement).style.background;
    expect(colorOf(a)).toBeTruthy();
    expect(colorOf(a)).toBe(colorOf(b));
  });
});
