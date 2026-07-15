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
