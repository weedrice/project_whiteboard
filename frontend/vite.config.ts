import { fileURLToPath, URL } from 'node:url'
import { execSync } from 'child_process'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { visualizer } from 'rollup-plugin-visualizer'

function getCommitHash(): string {
    try {
        return execSync('git rev-parse --short HEAD').toString().trim()
    } catch {
        return process.env.VITE_COMMIT_HASH || 'unknown'
    }
}

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const apiBaseUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'
    const isProduction = mode === 'production'

    return {
        plugins: [
            vue(),
            ...(!isProduction ? [vueDevTools()] : []),
            ...(isProduction
                ? [
                    visualizer({
                        filename: 'dist/stats.html',
                        open: false,
                        gzipSize: true,
                        brotliSize: true,
                        template: 'treemap',
                    }),
                ]
                : []),
        ],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url)),
            },
        },
        define: {
            __COMMIT_HASH__: JSON.stringify(getCommitHash()),
        },
        build: {
            target: 'esnext',
            minify: 'esbuild',
            cssMinify: true,
            sourcemap: !isProduction,
            rollupOptions: {
                output: {
                    manualChunks: (id) => {
                        const normalizedId = id.replace(/\\/g, '/')

                        if (normalizedId.includes('/node_modules/')) {
                            if (
                                normalizedId.includes('/node_modules/vue/')
                                || normalizedId.includes('/node_modules/vue-router/')
                                || normalizedId.includes('/node_modules/pinia/')
                            ) {
                                return 'vendor-vue'
                            }
                            if (normalizedId.includes('/node_modules/@tanstack/')) {
                                return 'vendor-query'
                            }
                            if (
                                normalizedId.includes('/node_modules/@tiptap/')
                                || normalizedId.includes('/node_modules/prosemirror-')
                                || normalizedId.includes('/node_modules/prosemirror/')
                                || normalizedId.includes('/node_modules/linkifyjs/')
                                || normalizedId.includes('/node_modules/vue-quill/')
                                || normalizedId.includes('/node_modules/quill/')
                            ) {
                                return 'vendor-editor'
                            }
                            if (normalizedId.includes('/node_modules/lucide-vue-next/')) {
                                return 'vendor-icons'
                            }
                            if (normalizedId.includes('/node_modules/date-fns/')) {
                                return 'vendor-date'
                            }
                            if (normalizedId.includes('/node_modules/vuedraggable/')) {
                                return 'vendor-drag'
                            }
                            if (normalizedId.includes('/node_modules/vue-i18n/')) {
                                return 'vendor-i18n'
                            }
                            if (normalizedId.includes('/node_modules/axios/')) {
                                return 'vendor-http'
                            }
                            return 'vendor'
                        }

                        if (normalizedId.includes('/views/admin/') || normalizedId.includes('/components/admin/')) {
                            return 'admin'
                        }
                    },
                    chunkFileNames: (chunkInfo) => {
                        const facadeModuleId = chunkInfo.facadeModuleId
                            ? chunkInfo.facadeModuleId.split('/').pop()
                            : 'chunk'
                        return `js/${facadeModuleId}-[hash].js`
                    },
                    entryFileNames: 'js/[name]-[hash].js',
                    assetFileNames: (assetInfo) => {
                        const info = assetInfo.name.split('.')
                        const ext = info[info.length - 1]
                        if (/png|jpe?g|svg|gif|tiff|bmp|ico/i.test(ext)) {
                            return `img/[name]-[hash][extname]`
                        }
                        if (/woff2?|eot|ttf|otf/i.test(ext)) {
                            return `fonts/[name]-[hash][extname]`
                        }
                        return `assets/[name]-[hash][extname]`
                    },
                },
            },
            chunkSizeWarningLimit: 500,
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
                },
            },
        },
    }
})
