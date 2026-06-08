import { useEffect } from 'react';

/**
 * Presentational multiple-choice options: renders the buttons, supports keyboard 1–N, and reports a
 * pick via [onSelect]. After a pick the parent passes [selectedIndex]/[correctIndex] to colour the
 * right (green) and wrong (red) options, and [disabled] to lock the buttons. No grading logic of its
 * own, so it's reusable by other modes (e.g. a future Learn mode).
 */
export function MultipleChoice({
  options,
  onSelect,
  selectedIndex = null,
  correctIndex = null,
  disabled = false,
}: {
  options: string[];
  onSelect: (index: number) => void;
  selectedIndex?: number | null;
  correctIndex?: number | null;
  disabled?: boolean;
}) {
  useEffect(() => {
    if (disabled) return;
    const onKey = (e: KeyboardEvent) => {
      const n = Number(e.key);
      if (Number.isInteger(n) && n >= 1 && n <= options.length) {
        e.preventDefault();
        onSelect(n - 1);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [disabled, options.length, onSelect]);

  return (
    <ul className="mc-list">
      {options.map((option, i) => {
        const revealed = selectedIndex !== null;
        const state = !revealed ? '' : i === correctIndex ? ' correct' : i === selectedIndex ? ' incorrect' : '';
        return (
          <li key={i}>
            <button className={`mc-option${state}`} disabled={disabled} onClick={() => onSelect(i)}>
              <span className="mc-key">{i + 1}</span>
              <span className="mc-text">{option}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
