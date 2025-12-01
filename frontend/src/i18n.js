import { createI18n } from 'vue-i18n'
import messages from './locales/messages'

const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages,
})

export default i18n
