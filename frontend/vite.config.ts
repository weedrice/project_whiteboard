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
                integration: {
                    configureCustomSWViteBuild(config) {
                        if (!config.build) return

                        // vite-plugin-pwa 1.3.0 still emits Rollup's deprecated single-chunk option under Vite 8.
                        const output = config.build.rollupOptions?.output
                        if (output && !Array.isArray(output)) {
                            delete output.inlineDynamicImports
                            output.codeSplitting = false
                        }
                        config.build.rolldownOptions = config.build.rollupOptions
                    },
                },
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
                        'assets/{index,HomeFeed,EmptyState,PullToRefresh}-*.css',
                        'js/{index,HomeFeed,auth,authSessionIntent,BaseButton,BaseSegmentedControl,BaseSkeleton,BaseSpinner,comment,EmptyState,emoticon,imageFallback,keyboard,logger,message,notification,PullToRefresh,postViewModel,rolldown-runtime,sanitize,SanitizedHtmlView,theme,useAttendance,useEventListener,useFocusTrap,useNotification,vendor-vue,vendor-query,vendor-i18n,vendor-http,vendor-icons,vendor-core}-*.js',
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
            rolldownOptions: {
                output: {
                    codeSplitting: {
                        groups: [
                            { name: 'vendor-prosemirror', test: /node_modules[\\/]prosemirror(?:-|[\\/])/, priority: 30 },
                            { name: 'vendor-vue', test: /node_modules[\\/](?:vue|vue-router|pinia)[\\/]/, priority: 20 },
                            { name: 'vendor-query', test: /node_modules[\\/]@tanstack[\\/]/, priority: 20 },
                            { name: 'vendor-highlight', test: /node_modules[\\/](?:lowlight|highlight\.js)[\\/]/, priority: 20 },
                            { name: 'vendor-editor', test: /node_modules[\\/](?:@tiptap|linkifyjs)[\\/]/, priority: 20 },
                            { name: 'vendor-icons', test: /node_modules[\\/]lucide-vue-next[\\/]/, priority: 20 },
                            { name: 'vendor-date', test: /node_modules[\\/]date-fns[\\/]/, priority: 20 },
                            { name: 'vendor-drag', test: /node_modules[\\/]vuedraggable[\\/]/, priority: 20 },
                            { name: 'vendor-i18n', test: /node_modules[\\/]vue-i18n[\\/]/, priority: 20 },
                            { name: 'vendor-http', test: /node_modules[\\/]axios[\\/]/, priority: 20 },
                            { name: 'vendor-core', test: /node_modules[\\/]/, priority: 10 },
                        ],
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
