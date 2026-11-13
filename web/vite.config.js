import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig(({ mode }) => {
  // Vite 不会把 .env* merge 进 vite.config 里的 process.env，须用 loadEnv（与 web 目录下 .env.development 对齐）
  const env = loadEnv(mode, __dirname, '');
  const proxyTarget =
    env.VITE_PROXY_TARGET?.trim() || 'http://127.0.0.1:18888';

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          secure: proxyTarget.startsWith('https'),
        },
      },
    },
  };
});
