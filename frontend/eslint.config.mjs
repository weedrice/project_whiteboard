import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import vitest from '@vitest/eslint-plugin'
import vueParser from 'vue-eslint-parser'

const hasKoreanText = (value) => /[가-힣]/.test(value)
const i18nGuardIgnoredFiles = new Set([
    'src/views/PrivacyPolicy.vue',
    'src/views/TermsOfService.vue',
])

const localI18nPlugin = {
    rules: {
        'no-bare-korean-in-template': {
            meta: {
                type: 'problem',
                docs: {
                    description: 'disallow hardcoded Korean text in Vue templates',
                },
                schema: [],
                messages: {
                    bareText: 'Move Korean display text into locale messages and render it through $t()/t().',
                },
            },
            create(context) {
                const filename = context.filename.replaceAll('\\', '/')
                const relativeFilename = filename.slice(filename.lastIndexOf('/src/') + 1)
                if (i18nGuardIgnoredFiles.has(relativeFilename)) {
                    return {}
                }

                const templateVisitor = {
                    VText(node) {
                        if (hasKoreanText(node.value.trim())) {
                            context.report({ node, messageId: 'bareText' })
                        }
                    },
                    'VAttribute[directive=false]'(node) {
                        const value = node.value?.value
                        if (typeof value === 'string' && hasKoreanText(value)) {
                            context.report({ node, messageId: 'bareText' })
                        }
                    },
                }

                return context.sourceCode.parserServices.defineTemplateBodyVisitor?.(templateVisitor) ?? {}
            },
        },
    },
}

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
            '@typescript-eslint/no-unused-vars': ['error', {
                argsIgnorePattern: '^_',
                varsIgnorePattern: '^_',
                caughtErrorsIgnorePattern: '^_',
            }],
        },
    },
    {
        files: ['src/**/*.vue'],
        plugins: {
            'local-i18n': localI18nPlugin,
        },
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
            '@typescript-eslint/no-unused-vars': ['error', {
                argsIgnorePattern: '^_',
                varsIgnorePattern: '^_',
                caughtErrorsIgnorePattern: '^_',
            }],
            'vue/multi-word-component-names': 'off',
            'vue/no-unused-vars': 'error',
            'vue/valid-v-memo': 'off',
            'local-i18n/no-bare-korean-in-template': 'error',
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
            '@typescript-eslint/no-explicit-any': 'off',
            '@typescript-eslint/no-unused-vars': 'off',
            'vue/no-unused-vars': 'off',
        },
    },
]
