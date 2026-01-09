import { fileURLToPath, URL } from 'node:url'
import { execSync } from 'child_process'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { visualizer } from 'rollup-plugin-visualizer'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
    // Load env file based on `mode` in the current working directory.
    // Set the third parameter to '' to load all env regardless of the `VITE_` prefix.
    const env = loadEnv(mode, process.cwd(), '')

    const apiBaseUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'
    const isProduction = mode === 'production'

    return {
        plugins: [
            vue(),
            vueDevTools(),
            // 빌드 분석 도구 (프로덕션 빌드 시에만 활성화)
            ...(isProduction ? [
                visualizer({
                    filename: 'dist/stats.html',
                    open: false,
                    gzipSize: true,
                    brotliSize: true,
                    template: 'treemap' // 'sunburst' | 'treemap' | 'network'
                })
            ] : [])
        ],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url))
            },
        },
        define: {
            __COMMIT_HASH__: JSON.stringify(execSync('git rev-parse --short HEAD').toString().trim())
        },
        build: {
            // 번들 크기 최적화
            target: 'esnext',
            minify: 'esbuild',
            cssMinify: true,
            sourcemap: !isProduction, // 프로덕션에서는 소스맵 비활성화
            rollupOptions: {
                output: {
                    // 수동 청크 분할로 번들 크기 최적화
                    manualChunks: (id) => {
                        // node_modules 의존성 분리
                        if (id.includes('node_modules')) {
                            // Vue 코어 라이브러리
                            if (id.includes('vue') || id.includes('vue-router') || id.includes('pinia')) {
                                return 'vendor-vue'
                            }
                            // TanStack Query
                            if (id.includes('@tanstack')) {
                                return 'vendor-query'
                            }
                            // 에디터 라이브러리 (큰 라이브러리)
                            if (id.includes('vue-quill') || id.includes('quill')) {
                                return 'vendor-editor'
                            }
                            // 아이콘 라이브러리
                            if (id.includes('lucide-vue-next')) {
                                return 'vendor-icons'
                            }
                            // 날짜 라이브러리
                            if (id.includes('date-fns')) {
                                return 'vendor-date'
                            }
                            // 드래그 앤 드롭
                            if (id.includes('vuedraggable')) {
                                return 'vendor-drag'
                            }
                            // i18n
                            if (id.includes('vue-i18n')) {
                                return 'vendor-i18n'
                            }
                            // Axios
                            if (id.includes('axios')) {
                                return 'vendor-http'
                            }
                            // 기타 node_modules
                            return 'vendor'
                        }
                        // Admin 관련 페이지는 별도 청크로 분리 (사용 빈도 낮음)
                        if (id.includes('/views/admin/') || id.includes('/components/admin/')) {
                            return 'admin'
                        }
                    },
                    // 청크 파일명 포맷
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
                    }
                }
            },
            // 청크 크기 경고 임계값 (500KB)
            chunkSizeWarningLimit: 500
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
