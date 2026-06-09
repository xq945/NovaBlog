import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
    // cpolar 内网穿透模式需要监听所有接口并放行 cpolar 域名
    const isCpolar = mode === 'cpolar'

    return {
        plugins: [vue()],
        server: {
            port: 3000,
            // 只有内网穿透模式才暴露到所有网络接口
            ...(isCpolar && {
                host: '0.0.0.0',
                allowedHosts: ['.cpolar.top', '.cpolar.cn']
            }),
            proxy: {
                '/api': {
                    target: 'http://localhost:8080',
                    changeOrigin: true,
                    rewrite: (path) => path.replace(/^\/api/, '')
                }
            }
        }
    }
})
