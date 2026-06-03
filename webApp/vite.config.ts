import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Pin the dev server to 5173: that's the origin registered as an Authorized JavaScript origin
  // for the Google OAuth web client. strictPort fails loudly if 5173 is taken instead of silently
  // drifting to 5174 (which Google then rejects with `Error 400: origin_mismatch`).
  server: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: './src/test/setup.ts',
    css: false,
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'json-summary', 'html'],
      reportsDirectory: './coverage',
      include: ['src/**'],
      exclude: [
        'src/main.tsx',
        'src/**/*.d.ts',
        'src/vite-env.d.ts',
        'src/test/**',
        'src/**/*.test.{ts,tsx}',
      ],
    },
  },
})
