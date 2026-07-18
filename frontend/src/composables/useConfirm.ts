import { useConfirmStore } from '@/stores/confirm'
import { useI18n } from 'vue-i18n'
import { usePromptStore } from '@/stores/prompt'

export function useConfirm() {
    const confirmStore = useConfirmStore()
    const promptStore = usePromptStore()
    const { t } = useI18n()

    const confirm = (
        message: string,
        title: string = t('common.confirm'),
        confirmText: string = t('common.yes'),
        cancelText: string = t('common.noValue')
    ): Promise<boolean> => {
        return confirmStore.open(message, title, confirmText, cancelText)
    }

    const confirmWithReason = (
        message: string,
        title: string = t('common.confirm'),
        placeholder: string = t('common.reason'),
        confirmText: string = t('common.yes'),
        cancelText: string = t('common.noValue'),
        options: { maxLength?: number } = {},
    ): Promise<string | null> => {
        return promptStore.open(message, title, placeholder, confirmText, cancelText, {
            required: true,
            errorMessage: placeholder,
            confirmVariant: 'danger',
            maxLength: options.maxLength,
        })
    }

    return {
        confirm,
        confirmWithReason
    }
}
