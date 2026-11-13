import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * 后台独立构建：部署时将产物目录挂到站点路径 /admin0522/（与 base 一致）。
 * 开发：npm run dev:admin → 访问 http://127.0.0.1:5174/admin0522/
 *
 * 开发时代理：默认连本机测试后端 http://127.0.0.1:18888（与主站 npm run dev 的 VITE_PROXY_TARGET 无关）。
 * 覆盖方式：在 web/.env.development 或 web/.env.local 中设置 VITE_ADMIN_PROXY_TARGET=...
 */
/** 开发时访问根路径 / 会提示 base 不一致，重定向到 /admin0522/（须 pre 先于 Vite 内部处理） */
function adminBaseRedirectPlugin() {
  return {
    name: 'admin-base-redirect',
    enforce: 'pre',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url || '';
        if (url === '/' || url === '/index.html' || url.startsWith('/?')) {
          res.writeHead(302, { Location: '/admin0522/' });
          res.end();
          return;
        }
        next();
      });
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '');
  const proxyTarget =
    env.VITE_ADMIN_PROXY_TARGET?.trim() || 'http://127.0.0.1:18888';

  return {
    plugins: [vue(), adminBaseRedirectPlugin()],
    root: path.resolve(__dirname, 'admin'),
    base: '/admin0522/',
    publicDir: path.resolve(__dirname, 'public'),
    build: {
      outDir: path.resolve(__dirname, 'dist-admin0522'),
      emptyOutDir: true,
    },
    server: {
      port: 5174,
      strictPort: false,
      /** 启动后打开带 base 的地址，避免只打开根路径 */
      open: '/admin0522/',
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          secure: proxyTarget.startsWith('https'),
        },
      },
    },
    resolve: {
      alias: {
        '@admin': path.resolve(__dirname, 'admin/src'),
      },
    },
  };
});
