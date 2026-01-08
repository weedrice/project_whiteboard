import { fileURLToPath, URL } from 'node:url'
import { execSync } from 'child_process'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
    // Load env file based on `mode` in the current working directory.
    // Set the third parameter to '' to load all env regardless of the `VITE_` prefix.
    const env = loadEnv(mode, process.cwd(), '')

    const apiBaseUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'

    return {
        plugins: [
            vue(),
            vueDevTools(),
        ],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url))
            },
        },
        define: {
            __COMMIT_HASH__: JSON.stringify(execSync('git rev-parse --short HEAD').toString().trim())
        },
        server: {
            proxy: {
                '/api': {
                    target: apiBaseUrl,
                    changeOrigin: true,
                },
                '/oauth2': {
                    target: apiBaseUrl,
                    changeOrigin: true,
                }
            }
        }
    }
})
