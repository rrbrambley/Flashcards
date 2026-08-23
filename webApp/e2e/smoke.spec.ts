import { test, expect } from '@playwright/test';

/**
 * E2E smoke test (#343): the core happy-path journey over the real UI + a live backend, exercising
 * the wiring that layer-isolated unit tests can't — routing, auth token flow, the API↔UI contract,
 * and persistence. Deliberately one thin journey (register → create a deck → practice it), not
 * exhaustive coverage.
 *
 * Data hygiene: each run registers a fresh, uniquely-named user so runs never collide.
 */
test('register → create a deck → it appears in the library → practice it to completion', async ({ page }) => {
  const stamp = Date.now();
  const email = `e2e-${stamp}@example.test`;
  const password = 'e2e-password-123';
  const deckTitle = `E2E Smoke Deck ${stamp}`;

  // --- Register (auth token flow) ---
  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  // Success flips auth state and the router redirects to the signed-in Home.
  await expect(page.getByRole('link', { name: 'Library' })).toBeVisible();

  // --- Create a deck (persistence) ---
  await page.getByRole('link', { name: 'Library' }).click();
  await page.getByRole('button', { name: '+ Create deck' }).click();

  await page.getByLabel('Deck title').fill(deckTitle);
  await page.getByLabel('Term').first().fill('Capital of France?');
  await page.getByLabel('Definition').first().fill('Paris');
  await page.getByRole('button', { name: 'Create deck' }).click();

  // --- It appears in the library (API ↔ UI contract, round-tripped through the backend) ---
  // The library also lists the seeded global catalog decks, so scope to our deck's row by its
  // unique title rather than matching the (many) "Practice" buttons globally.
  await expect(page).toHaveURL(/\/library$/);
  const deckRow = page.locator('li.deck-row', { hasText: deckTitle });
  await expect(deckRow).toBeVisible();

  // --- Practice it end to end (prompt → answer → grade → completion) ---
  await deckRow.getByRole('button', { name: 'Practice' }).click();

  // No mode in the URL → the mode chooser. Use Test mode: it's the most literal
  // prompt → type-an-answer → grade → completion path.
  await expect(page.getByRole('heading', { name: 'Choose a mode' })).toBeVisible();
  // Picking a mode advances to that mode's settings step, where Start lives (#410).
  await page.getByRole('button', { name: 'Test' }).click();
  await page.getByRole('button', { name: 'Start practice' }).click();

  // Answer the single card correctly, see it graded, then advance to completion.
  await page.getByLabel('Your answer').fill('Paris');
  await page.getByRole('button', { name: 'Check' }).click();
  await page.getByRole('button', { name: 'Next' }).click();

  await expect(page.getByRole('heading', { name: 'Practice complete' })).toBeVisible();
});
