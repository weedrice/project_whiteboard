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
type LazyLocale = Exclude<SupportedLocale, 'ko'>
const localeLoaders: Record<LazyLocale, () => Promise<Messages>> = {
    en: () => import('./locales/en').then((module) => module.default),
    ja: () => import('./locales/ja').then((module) => module.default),
}
const localeMessagePromises: Partial<Record<LazyLocale, Promise<Messages>>> = {}

export async function ensureLocaleMessages(locale: SupportedLocale): Promise<void> {
    if (locale === 'ko' || i18n.global.availableLocales.includes(locale)) {
        return
    }

    localeMessagePromises[locale] ??= localeLoaders[locale]()
    try {
        i18n.global.setLocaleMessage(locale, await localeMessagePromises[locale])
    } catch (error) {
        delete localeMessagePromises[locale]
        throw error
    }
}

export async function setAppLocale(
    locale: SupportedLocale,
    canCommit: () => boolean = () => true,
): Promise<boolean> {
    const currentLocale = i18n.global.locale.value
    try {
        await ensureLocaleMessages(locale)
        if (!canCommit()) return true
        i18n.global.locale.value = locale
        document.documentElement.lang = locale
        return true
    } catch {
        i18n.global.locale.value = currentLocale
        return false
    }
}

export default i18n
