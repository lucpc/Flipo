import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Encaminha para o backend em dev, evitando configurar CORS lá
      // (frontend chama /api/... como caminho relativo).
      '/api': 'http://localhost:8080',
    },
  },
})
