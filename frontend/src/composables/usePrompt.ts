import { usePromptStore } from '@/stores/prompt'
import { useI18n } from 'vue-i18n'

export function usePrompt() {
    const promptStore = usePromptStore()
    const { t } = useI18n()

    const prompt = (
        message: string,
        title: string = t('common.input'),
        placeholder: string = '',
        confirmText: string = t('common.confirm'),
        cancelText: string = t('common.cancel')
    ): Promise<string | null> => {
        return promptStore.open(message, title, placeholder, confirmText, cancelText)
    }

    return {
        prompt
    }
}
