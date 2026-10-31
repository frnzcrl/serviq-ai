import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Development-only reverse proxy avoids CORS and keeps API paths relative.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
});
