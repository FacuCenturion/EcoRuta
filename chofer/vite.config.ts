import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// La app del chofer corre en otro puerto que el panel (5173)
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true },
  preview: { port: 5174 },
  test: {
    environment: 'node',
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
