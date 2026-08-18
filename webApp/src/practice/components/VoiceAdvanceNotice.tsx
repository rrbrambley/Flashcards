/**
 * Stands in for the Next button while a voice run is advancing. Not a button: pressing it is the
 * thing being removed. Announced politely so a screen-reader user learns the card is about to
 * change rather than being surprised by it.
 */
export function VoiceAdvanceNotice() {
  return (
    <p className="test-advancing" aria-live="polite">
      Next question…
    </p>
  );
}
