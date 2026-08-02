/**
 * Waits for the backend to be reachable before the E2E suite runs. The web app talks to the backend
 * at http://localhost:8080 (src/api/client.ts default), and the smoke journey registers + persists
 * real data — so a missing backend should fail fast with an actionable message, not a wall of UI
 * timeouts. Polls the public /health probe (200 once Postgres is up).
 */
const BACKEND_BASE_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';
const HEALTH_URL = `${BACKEND_BASE_URL}/health`;
const TIMEOUT_MS = 60_000;
const INTERVAL_MS = 1_000;

async function globalSetup() {
  const deadline = Date.now() + TIMEOUT_MS;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      const res = await fetch(HEALTH_URL);
      if (res.ok) return;
      lastError = `HTTP ${res.status}`;
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
    await new Promise((r) => setTimeout(r, INTERVAL_MS));
  }
  throw new Error(
    `Backend not reachable at ${HEALTH_URL} after ${TIMEOUT_MS / 1000}s (last: ${lastError}).\n` +
      `Start it before running E2E: \`make start\` (Postgres + backend), then \`npm run test:e2e\`.`,
  );
}

export default globalSetup;
