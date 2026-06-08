import { useState } from 'react';

/**
 * A presentational answer field: a text input + submit (Enter submits). It holds no grading or
 * correctness logic, so any mode that needs typed input (Test, and a future Learn mode) can reuse it.
 */
export function TextAnswerInput({
  onSubmit,
  autoFocus = true,
}: {
  onSubmit: (value: string) => void;
  autoFocus?: boolean;
}) {
  const [value, setValue] = useState('');
  return (
    <form
      className="text-answer"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(value);
      }}
    >
      <input
        className="text-answer-input"
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Type your answer…"
        aria-label="Your answer"
        autoComplete="off"
        autoFocus={autoFocus}
      />
      <button type="submit">Check</button>
    </form>
  );
}
