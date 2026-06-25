import { createI18n } from 'vue-i18n'
import type { LocaleMessage } from '@intlify/core-base'
import type { VueMessageType } from 'vue-i18n'
import messages from './locales'

const i18nMessages = messages as unknown as Record<keyof typeof messages, LocaleMessage<VueMessageType>>

const rawI18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: i18nMessages,
})

type AppI18n = Omit<typeof rawI18n, 'global'> & {
    global: typeof rawI18n.global & {
        t: (key: string, ...args: unknown[]) => string
        locale: { value: 'ko' | 'en' }
    }
}

const i18n = rawI18n as AppI18n

export default i18n
