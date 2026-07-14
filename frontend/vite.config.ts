import { fileURLToPath, URL } from 'node:url'
import { execSync } from 'child_process'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { VitePWA } from 'vite-plugin-pwa'
import { visualizer } from 'rollup-plugin-visualizer'

function getCommitHash(): string {
    if (process.env.VITE_COMMIT_HASH) {
        return process.env.VITE_COMMIT_HASH
    }

    try {
        return execSync('git rev-parse --short HEAD').toString().trim()
    } catch {
        return 'unknown'
    }
}

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const apiBaseUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'
    const isProduction = mode === 'production' || mode === 'analyze'
    const shouldAnalyzeBundle = mode === 'analyze' || (isProduction && env.VITE_ANALYZE === 'true')

    return {
        plugins: [
            vue(),
            VitePWA({
                strategies: 'injectManifest',
                srcDir: 'src',
                filename: 'service-worker.ts',
                // pwa.ts applies waiting updates automatically after active form guards clear.
                registerType: 'prompt',
                includeAssets: [
                    'favicon.ico',
                    'favicon_dark.ico',
                    'offline.html',
                    'pwa-192x192.png',
                    'pwa-512x512.png',
                    'pwa-maskable-512x512.png',
                ],
                manifest: {
                    name: 'NoviIs',
                    short_name: 'NoviIs',
                    description: 'NoviIs community platform',
                    theme_color: '#ffffff',
                    background_color: '#ffffff',
                    display: 'standalone',
                    start_url: '/',
                    scope: '/',
                    icons: [
                        {
                            src: '/favicon.ico',
                            sizes: '48x48',
                            type: 'image/x-icon',
                        },
                        {
                            src: '/pwa-192x192.png',
                            sizes: '192x192',
                            type: 'image/png',
                        },
                        {
                            src: '/pwa-512x512.png',
                            sizes: '512x512',
                            type: 'image/png',
                        },
                        {
                            src: '/pwa-maskable-512x512.png',
                            sizes: '512x512',
                            type: 'image/png',
                            purpose: 'maskable',
                        },
                    ],
                },
                injectManifest: {
                    globPatterns: [
                        'index.html',
                        'assets/index-*.css',
                        'assets/HomeFeed-*.css',
                        'assets/EmptyState-*.css',
                        'assets/PullToRefresh-*.css',
                        'js/index-*.js',
                        'js/HomeFeed-*.js',
                        'js/BaseSegmentedControl.*.js',
                        'js/BaseSkeleton.*.js',
                        'js/EmptyState-*.js',
                        'js/PullToRefresh-*.js',
                        'js/postViewModel-*.js',
                        'js/sanitize-*.js',
                        'js/useAttendance-*.js',
                        'js/vendor-vue-*.js',
                        'js/vendor-query-*.js',
                        'js/vendor-i18n-*.js',
                        'js/vendor-http-*.js',
                        'js/vendor-icons-*.js',
                        'js/vendor-core-*.js',
                    ],
                },
            }),
            ...(!isProduction ? [vueDevTools()] : []),
            ...(shouldAnalyzeBundle
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
            target: 'baseline-widely-available',
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
                                normalizedId.includes('/node_modules/lowlight/')
                                || normalizedId.includes('/node_modules/highlight.js/')
                            ) {
                                return 'vendor-highlight'
                            }
                            if (
                                normalizedId.includes('/node_modules/@tiptap/')
                                || normalizedId.includes('/node_modules/prosemirror-')
                                || normalizedId.includes('/node_modules/prosemirror/')
                                || normalizedId.includes('/node_modules/linkifyjs/')
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
                            return 'vendor-core'
                        }
                    },
                    chunkFileNames: 'js/[name]-[hash].js',
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
            chunkSizeWarningLimit: 450,
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
