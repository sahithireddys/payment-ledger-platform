import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on 5173 (Vite's default). The backend's CORS config
// (see WebConfig.java) explicitly allows this origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
});
