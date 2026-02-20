import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/actuator': 'http://localhost:8080',
      '/vouchers': 'http://localhost:8080',
      '/admin': 'http://localhost:8080',
    },
  },
})