import { createI18n } from 'vue-i18n'
import type { LocaleMessage } from '@intlify/core-base'
import type { VueMessageType } from 'vue-i18n'
import messages from './locales'
import type { Messages, SupportedLocale } from './locales/types'

type I18nMessages = Partial<Record<SupportedLocale, LocaleMessage<VueMessageType>>>

const rawI18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: messages as I18nMessages,
})

type AppI18n = Omit<typeof rawI18n, 'global'> & {
    global: typeof rawI18n.global & {
        t: (key: string, ...args: unknown[]) => string
        locale: { value: SupportedLocale }
        availableLocales: SupportedLocale[]
        setLocaleMessage: (locale: SupportedLocale, message: Messages) => void
    }
}

const i18n = rawI18n as AppI18n
let englishMessagesPromise: Promise<Messages> | null = null

export async function ensureLocaleMessages(locale: SupportedLocale): Promise<void> {
    if (locale === 'ko' || i18n.global.availableLocales.includes(locale)) {
        return
    }

    englishMessagesPromise ??= import('./locales/en').then((module) => module.default)
    try {
        i18n.global.setLocaleMessage('en', await englishMessagesPromise)
    } catch (error) {
        englishMessagesPromise = null
        throw error
    }
}

export async function setAppLocale(locale: SupportedLocale): Promise<boolean> {
    const currentLocale = i18n.global.locale.value
    try {
        await ensureLocaleMessages(locale)
        i18n.global.locale.value = locale
        document.documentElement.lang = locale
        return true
    } catch {
        i18n.global.locale.value = currentLocale
        return false
    }
}

export default i18n
