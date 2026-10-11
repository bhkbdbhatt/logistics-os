import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const proxyTarget = process.env.VITE_DEV_PROXY ?? 'http://localhost:8088';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/ops': proxyTarget,
      '/tower': proxyTarget,
      '/auth': proxyTarget,
      '/mdm': proxyTarget,
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
