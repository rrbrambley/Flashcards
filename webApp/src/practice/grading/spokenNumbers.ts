/**
 * Renders spoken number words as digits, so a correctly *spoken* numeric answer can be graded
 * against a card whose answer is written in digits (#390).
 *
 * The problem this solves: `gradeTextAnswer` scores character-level edit distance, so "nineteen
 * eighty" against "1980" scores ≈0.07 on a 0.85 threshold. Test mode marks a genuinely correct
 * spoken answer wrong every time the answer is numeric.
 *
 * **These are additional candidates, never a replacement.** Callers grade the original transcript
 * first and only fall back to these, which is what makes the conversion safe to be eager: a card
 * answered "one piece" still grades against the words, because the original wins before any variant
 * is tried. Nothing here loosens a threshold or changes how any string is graded — it only changes
 * *which* strings are offered, exactly like the n-best rescoring it sits beside.
 *
 * Two readings are emitted because English says numbers both ways and the transcript doesn't say
 * which is meant:
 *  - **arithmetic** — "twenty three" → 23, "one hundred and five" → 105, "two thousand" → 2000
 *  - **concatenated** — the year/digit-string reading: "nineteen eighty" → 1980 (not 99),
 *    "twenty twenty four" → 2024, "one two three" → 123
 *
 * Whichever grades correct wins; if neither does, the caller keeps the original transcript. Scale
 * words ("hundred", "thousand") rule out the concatenated reading, since "two thousand" is never
 * "2" followed by "1000".
 *
 * Mirrors shared Kotlin `practice/grading/SpokenNumbers.kt` and is pinned by the `spokenNumbers`
 * section of the golden fixture, so the two can't drift (FLA-81).
 */

const UNITS: Record<string, number> = {
  zero: 0, oh: 0, one: 1, two: 2, three: 3, four: 4,
  five: 5, six: 6, seven: 7, eight: 8, nine: 9,
};

const TEENS: Record<string, number> = {
  ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14,
  fifteen: 15, sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19,
};

const TENS: Record<string, number> = {
  twenty: 20, thirty: 30, forty: 40, fifty: 50,
  sixty: 60, seventy: 70, eighty: 80, ninety: 90,
};

const SCALES: Record<string, number> = {
  hundred: 100, thousand: 1_000, million: 1_000_000, billion: 1_000_000_000,
};

function valueOfWord(token: string): number | undefined {
  if (token in UNITS) return UNITS[token];
  if (token in TEENS) return TEENS[token];
  if (token in TENS) return TENS[token];
  return undefined;
}

function isNumberWord(token: string): boolean {
  return valueOfWord(token) !== undefined || token in SCALES;
}

/** Strips surrounding punctuation so "nineteen eighty," still reads as a number word. */
function key(token: string): string {
  return token.toLowerCase().replace(/^[.,!?;:"']+|[.,!?;:"']+$/g, '');
}

/**
 * Alternative renderings of `transcript` with spoken numbers written as digits. Empty when the
 * transcript contains no number words, or when every rendering equals the original.
 */
export function spokenNumberVariants(transcript: string): string[] {
  const tokens = transcript.split(' ').filter((t) => t !== '');
  if (!tokens.some((t) => isNumberWord(key(t)))) return [];

  const runs = numberRuns(tokens);
  if (runs.length === 0) return [];

  const arithmetic = render(tokens, runs, (run) => String(arithmeticValue(run)));
  const concatenated = render(tokens, runs, (run) => concatenatedValue(run) ?? String(arithmeticValue(run)));

  return [...new Set([arithmetic, concatenated])].filter((v) => v !== transcript && v.trim() !== '');
}

interface Run {
  start: number;
  end: number; // inclusive
}

/** Maximal runs of number words, with a joining "and" absorbed when flanked by them. */
function numberRuns(tokens: string[]): Run[] {
  const runs: Run[] = [];
  let start = -1;
  tokens.forEach((raw, i) => {
    const token = key(raw);
    const joins =
      token === 'and' && start >= 0 && i + 1 < tokens.length && isNumberWord(key(tokens[i + 1]));
    if (isNumberWord(token)) {
      if (start < 0) start = i;
    } else if (!joins) {
      if (start >= 0) runs.push({ start, end: i - 1 });
      start = -1;
    }
  });
  if (start >= 0) runs.push({ start, end: tokens.length - 1 });
  // A trailing "and" can't end a run — "five and" is five, then a word.
  return runs.map(({ start: s, end }) => {
    let last = end;
    while (last > s && key(tokens[last]) === 'and') last--;
    return { start: s, end: last };
  });
}

/** Rebuilds the sentence with each run replaced by `renderRun`'s digits. */
function render(tokens: string[], runs: Run[], renderRun: (run: string[]) => string): string {
  const out: string[] = [];
  let i = 0;
  while (i < tokens.length) {
    const run = runs.find((r) => r.start === i);
    if (run) {
      out.push(renderRun(tokens.slice(run.start, run.end + 1).map(key)));
      i = run.end + 1;
    } else {
      out.push(tokens[i]);
      i++;
    }
  }
  return out.join(' ');
}

/** Standard accumulation: tens+units add, scales multiply. "one hundred and five" → 105. */
function arithmeticValue(run: string[]): number {
  let total = 0;
  let current = 0;
  for (const token of run) {
    if (token === 'and') continue;
    const word = valueOfWord(token);
    if (word !== undefined) {
      current += word;
      continue;
    }
    const scale = SCALES[token];
    if (scale === 100) {
      current = (current === 0 ? 1 : current) * 100;
    } else {
      total += (current === 0 ? 1 : current) * scale;
      current = 0;
    }
  }
  return total + current;
}

/**
 * The year/digit-string reading: split the run into the groups a speaker would pause between and
 * concatenate their digits. "nineteen eighty" → "1980", "nineteen oh five" → "1905".
 *
 * Null when the run contains a scale word — "two thousand" is 2000, never "2" then "1000".
 */
function concatenatedValue(run: string[]): string | null {
  if (run.some((t) => t in SCALES)) return null;
  const groups: number[] = [];
  let i = 0;
  while (i < run.length) {
    const token = run[i];
    if (token === 'and') {
      i++;
      continue;
    }
    const value = valueOfWord(token);
    if (value === undefined) return null;
    // A tens word swallows a following unit: "twenty three" is one group, not two.
    if (token in TENS && i + 1 < run.length) {
      const next = UNITS[run[i + 1]];
      if (next !== undefined && next !== 0) {
        groups.push(value + next);
        i += 2;
        continue;
      }
    }
    groups.push(value);
    i++;
  }
  if (groups.length < 2) return null; // one group reads the same as arithmetic
  return groups.map(String).join('');
}
