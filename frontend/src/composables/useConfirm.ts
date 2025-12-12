import { useConfirmStore } from '@/stores/confirm'
import { useI18n } from 'vue-i18n'

export function useConfirm() {
    const confirmStore = useConfirmStore()
    const { t } = useI18n()

    const confirm = (
        message: string,
        title: string = t('common.confirm'),
        confirmText: string = t('common.yes'),
        cancelText: string = t('common.no')
    ): Promise<boolean> => {
        return confirmStore.open(message, title, confirmText, cancelText)
    }

    return {
        confirm
    }
}
