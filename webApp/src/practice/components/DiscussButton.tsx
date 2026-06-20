/** The 💬 control each mode shows once a card's answer is revealed, opening its discussion (FLA-116). */
export function DiscussButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="link-btn discuss-btn" onClick={onClick}>
      💬 Discuss this card
    </button>
  );
}
