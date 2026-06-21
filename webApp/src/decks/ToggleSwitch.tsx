interface ToggleSwitchProps {
  checked: boolean;
  onChange: () => void;
  disabled?: boolean;
  /** Optional text shown next to the switch; omit when the row supplies its own label. */
  label?: string;
  /** Accessible name for the control (falls back to `label`). */
  ariaLabel?: string;
}

/**
 * A small on/off switch (the FLA-116 discussions-toggle styling), reused for admin per-deck
 * feature toggles. Stateless — the caller owns the checked state and persistence.
 */
export function ToggleSwitch({ checked, onChange, disabled = false, label, ariaLabel }: ToggleSwitchProps) {
  return (
    <label className="deck-discussions-toggle">
      <span className="toggle-switch">
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={onChange}
          aria-label={ariaLabel ?? label}
        />
        <span className="toggle-switch-track" aria-hidden="true" />
      </span>
      {label && <span className="toggle-switch-label">{label}</span>}
    </label>
  );
}
