import { useState } from 'react';

/**
 * A presentational answer field: a text input + submit (Enter submits). It holds no grading or
 * correctness logic, so any mode that needs typed input (Test, and a future Learn mode) can reuse it.
 *
 * With `confirmBlankSubmit`, submitting an empty (trimmed) answer — via Enter or the Check button —
 * shows an inline "skip this one?" confirm instead of submitting immediately, guarding against an
 * accidental early Enter that would otherwise be graded wrong (FLA-179). Intentional blank submission
 * stays possible via the Confirm action, so the Check button is never disabled; resuming typing
 * dismisses the prompt.
 */
export function TextAnswerInput({
  onSubmit,
  autoFocus = true,
  confirmBlankSubmit = false,
}: {
  onSubmit: (value: string) => void;
  autoFocus?: boolean;
  confirmBlankSubmit?: boolean;
}) {
  const [value, setValue] = useState('');
  const [confirmingBlank, setConfirmingBlank] = useState(false);

  const submit = () => {
    if (confirmBlankSubmit && value.trim() === '') {
      // Guard the accidental empty Enter/Check: confirm the skip instead of grading it wrong.
      setConfirmingBlank(true);
      return;
    }
    onSubmit(value);
  };

  return (
    <>
      <form
        className="text-answer"
        onSubmit={(e) => {
          e.preventDefault();
          // While the confirm is up, a second Enter must not re-trigger it (or submit).
          if (confirmingBlank) return;
          submit();
        }}
      >
        <input
          className="text-answer-input"
          type="text"
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            // Typing means they didn't want to skip after all — dismiss the prompt.
            if (confirmingBlank) setConfirmingBlank(false);
          }}
          placeholder="Type your answer…"
          aria-label="Your answer"
          autoComplete="off"
          autoFocus={autoFocus}
        />
        <button type="submit">Check</button>
      </form>

      {confirmingBlank && (
        <div className="text-answer-confirm" role="alertdialog" aria-label="Skip this one?">
          <p>You haven't typed an answer — skip this one?</p>
          <div className="text-answer-confirm-actions">
            <button
              type="button"
              onClick={() => {
                setConfirmingBlank(false);
                onSubmit(value);
              }}
            >
              Confirm
            </button>
          </div>
        </div>
      )}
    </>
  );
}
