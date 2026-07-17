import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import vitest from '@vitest/eslint-plugin'
import vueParser from 'vue-eslint-parser'

const hasKoreanText = (value) => /[가-힣]/.test(value)
const hasSuspiciousMojibake = (value) => /�/.test(value)
const hasBareEnglishSentence = (value) => /\b[A-Za-z]+(?:\s+[A-Za-z]+){2,}[.!?]/.test(value)
const hasBareEnglishAccessibleName = (value) => /\b[A-Za-z][A-Za-z'-]*(?:\s+[A-Za-z][A-Za-z'-]*)+\b/.test(value)
const legalDocumentFiles = new Set([
    'src/views/PrivacyPolicy.vue',
])

const hasStaticClass = (node, className) => {
    const classAttribute = node.startTag?.attributes.find(
        (attribute) => !attribute.directive && attribute.key.name === 'class',
    )
    const classValue = classAttribute?.value?.value
    return typeof classValue === 'string' && classValue.split(/\s+/).includes(className)
}

const isAllowedLegalDocumentCopy = (node, relativeFilename) => {
    if (!legalDocumentFiles.has(relativeFilename)) return false

    let current = node.parent
    while (current) {
        if (current.type === 'VElement') {
            if (hasStaticClass(current, 'nv-rich-content')) return true
            if (current.name === 'h1' && hasStaticClass(current, 'nv-title')) return true
        }
        current = current.parent
    }
    return false
}

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
                    bareEnglish: 'Move English display sentences into locale messages and render them through $t()/t().',
                    mojibake: 'Replace suspicious mojibake with a valid character, icon, or locale message.',
                },
            },
            create(context) {
                const filename = context.filename.replaceAll('\\', '/')
                const relativeFilename = filename.slice(filename.lastIndexOf('/src/') + 1)

                const templateVisitor = {
                    VText(node) {
                        const value = node.value.trim()
                        if (hasSuspiciousMojibake(value)) {
                            context.report({ node, messageId: 'mojibake' })
                        } else if (hasBareEnglishSentence(value)) {
                            context.report({ node, messageId: 'bareEnglish' })
                        } else if (hasKoreanText(value)
                            && !isAllowedLegalDocumentCopy(node, relativeFilename)) {
                            context.report({ node, messageId: 'bareText' })
                        }
                    },
                    'VAttribute[directive=false]'(node) {
                        const value = node.value?.value
                        const attributeName = node.key.name
                        if (typeof value === 'string' && hasKoreanText(value)
                            && !isAllowedLegalDocumentCopy(node, relativeFilename)) {
                            context.report({ node, messageId: 'bareText' })
                        } else if (typeof value === 'string'
                            && ['aria-label', 'title', 'placeholder'].includes(attributeName)
                            && hasBareEnglishAccessibleName(value)) {
                            context.report({ node, messageId: 'bareEnglish' })
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
            'no-restricted-imports': ['error', {
                paths: [{
                    name: 'loglevel',
                    message: 'Import the shared @/utils/logger wrapper so Axios errors are sanitized before logging.',
                }],
            }],
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
            'no-restricted-imports': ['error', {
                paths: [{
                    name: 'loglevel',
                    message: 'Import the shared @/utils/logger wrapper so Axios errors are sanitized before logging.',
                }],
            }],
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
        files: ['src/utils/logger.ts'],
        rules: {
            'no-restricted-imports': 'off',
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
