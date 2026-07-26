import { fileURLToPath } from 'node:url'
import { defineConfig, configDefaults } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [
        vue(),
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
            'lowlight': fileURLToPath(new URL('./src/test/mocks/lowlight.ts', import.meta.url)),
            'virtual:pwa-register': fileURLToPath(new URL('./src/test/mocks/pwaRegister.ts', import.meta.url))
        },
    },
    define: {
        __COMMIT_HASH__: JSON.stringify('test-hash')
    },
    test: {
        environment: 'jsdom',

        // 시각 포맷 테스트가 실행 지역에 따라 갈리지 않도록 고정한다.

        // 고정하지 않으면 CI(UTC)는 초록불인데 KST 개발 머신에서만 빨간불이 뜬다.

        env: { TZ: 'UTC' },
        exclude: [...configDefaults.exclude, 'e2e/**'],
        root: fileURLToPath(new URL('./', import.meta.url)),
        setupFiles: ['./src/test/setup.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html'],
            thresholds: {
                statements: 75,
                branches: 65,
                functions: 70,
                lines: 75
            },
            exclude: [
                'src/main.ts',
                'src/env.d.ts',
                '**/*.d.ts',
                '**/*.config.ts',
                '**/__tests__/**'
            ]
        }
    }
})
