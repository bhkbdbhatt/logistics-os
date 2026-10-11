import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxyTarget = env.VITE_DEV_PROXY || process.env.VITE_DEV_PROXY || 'http://localhost:8088';

  return {
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
  };
});
