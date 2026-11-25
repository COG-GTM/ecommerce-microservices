/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The till always talks to the API gateway through /api (same origin): Vite proxies it in dev,
// nginx proxies it in the container (see nginx.conf).
const gateway = process.env.TILL_GATEWAY_URL ?? 'http://localhost:8181'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: { '/api': { target: gateway, changeOrigin: true } },
  },
  preview: {
    port: 5174,
    proxy: { '/api': { target: gateway, changeOrigin: true } },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
