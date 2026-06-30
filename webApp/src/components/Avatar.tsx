interface AvatarProps {
  /** The avatar image URL. When null/absent, an initials monogram is rendered instead. */
  url?: string | null;
  /** The display name — used for the monogram initials and the image's alt text. */
  name?: string | null;
  /** Diameter in px (the monogram font scales with it). Defaults to 32. */
  size?: number;
}

/**
 * A user's avatar (FLA-162): the curated CDN image in a circle, or — when no avatar is set or the
 * CDN is unconfigured — a fallback initials monogram on a color hashed from the name (so a given
 * author keeps a stable color). Sizing is inline so the same component works at any diameter.
 */
export function Avatar({ url, name, size = 32 }: AvatarProps) {
  const label = name?.trim() ? name.trim() : null;
  const dimensions = { width: size, height: size };

  if (url) {
    return (
      <img
        className="avatar"
        src={url}
        alt={label ? `${label}'s avatar` : 'avatar'}
        style={dimensions}
        loading="lazy"
      />
    );
  }

  return (
    <span
      className="avatar avatar-monogram"
      style={{ ...dimensions, fontSize: Math.round(size * 0.42), background: monogramColor(label) }}
      aria-label={label ? `${label}'s avatar` : 'avatar'}
      role="img"
    >
      {initials(label)}
    </span>
  );
}

/** Up to two uppercase initials from the name; '?' when there's no name. */
function initials(name: string | null): string {
  if (!name) return '?';
  const words = name.split(/\s+/).filter(Boolean);
  const letters = (words[0]?.[0] ?? '') + (words.length > 1 ? (words[words.length - 1]?.[0] ?? '') : '');
  return letters.toUpperCase() || '?';
}

/** A stable, pleasant background color derived from the name (HSL with a hashed hue). */
function monogramColor(name: string | null): string {
  const seed = name ?? '';
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 360;
  }
  return `hsl(${hash}, 45%, 45%)`;
}
