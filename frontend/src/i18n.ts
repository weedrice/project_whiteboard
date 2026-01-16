import { createI18n } from 'vue-i18n'
import messages from './locales'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: messages as any,
})

export default i18n
