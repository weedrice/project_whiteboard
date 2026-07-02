import { createI18n } from 'vue-i18n'
import type { LocaleMessage } from '@intlify/core-base'
import type { VueMessageType } from 'vue-i18n'
import messages from './locales'
import type { SupportedLocale } from './locales/types'

type I18nMessages = Record<keyof typeof messages, LocaleMessage<VueMessageType>>

const i18nMessages = messages as I18nMessages

const rawI18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: i18nMessages,
})

type AppI18n = Omit<typeof rawI18n, 'global'> & {
    global: typeof rawI18n.global & {
        t: (key: string, ...args: unknown[]) => string
        locale: { value: SupportedLocale }
    }
}

const i18n = rawI18n as AppI18n

export default i18n
