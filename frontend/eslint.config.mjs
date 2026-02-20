import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import vitest from 'eslint-plugin-vitest'
import vueParser from 'vue-eslint-parser'

export default [
    {
        ignores: ['dist/**', 'coverage/**', 'node_modules/**'],
    },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    ...pluginVue.configs['flat/essential'],
    {
        files: ['src/**/*.ts'],
        languageOptions: {
            parser: tseslint.parser,
            ecmaVersion: 'latest',
            sourceType: 'module',
            globals: {
                ...globals.browser,
                ...globals.node,
            },
        },
        rules: {
            'no-console': 'off',
            'no-useless-escape': 'off',
            'no-undef': 'off',
            'no-unused-vars': 'off',
            'no-case-declarations': 'off',
            '@typescript-eslint/no-explicit-any': 'off',
            '@typescript-eslint/no-empty-object-type': 'off',
            '@typescript-eslint/no-unused-vars': 'off',
        },
    },
    {
        files: ['src/**/*.vue'],
        languageOptions: {
            parser: vueParser,
            parserOptions: {
                parser: tseslint.parser,
                ecmaVersion: 'latest',
                sourceType: 'module',
                extraFileExtensions: ['.vue'],
            },
            globals: {
                ...globals.browser,
                ...globals.node,
            },
        },
        rules: {
            'no-console': 'off',
            'no-useless-escape': 'off',
            'no-undef': 'off',
            'no-unused-vars': 'off',
            'no-case-declarations': 'off',
            '@typescript-eslint/no-explicit-any': 'off',
            '@typescript-eslint/no-unused-vars': 'off',
            'vue/multi-word-component-names': 'off',
            'vue/no-unused-vars': 'off',
            'vue/valid-v-memo': 'off',
        },
    },
    {
        files: ['src/**/*.{spec,test}.ts', 'src/**/__tests__/**/*.{ts,vue}'],
        plugins: {
            vitest,
        },
        languageOptions: {
            globals: {
                ...vitest.environments.env.globals,
            },
        },
        rules: {
            ...vitest.configs.recommended.rules,
        },
    },
]
