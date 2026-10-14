import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // Forward /api calls to the Spring Boot backend, so no CORS setup is needed in development.
    proxy: {
      '/api': 'http://localhost:8080',
      // "Sign in with Google" (Spring Security OAuth2 login): start and callback URLs.
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
})
