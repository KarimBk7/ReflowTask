import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy to the Spring Boot backend so the browser sees one origin in dev and no CORS
    // configuration is needed on the server.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
