import type { AxiosError } from 'axios'
import { useI18n } from 'vue-i18n'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { useModalSubmit } from '@/composables/useModalSubmit'
import { useToastStore } from '@/stores/toast'
import { extractErrorResponse } from '@/utils/errorHandler'
import { hasMessageContent } from '@/utils/messageValidation'
import logger from '@/utils/logger'

type MessageReceiverId = string | number | null | undefined

interface UseMessageSubmitOptions {
    getReceiverId: () => MessageReceiverId
    onSuccess?: () => void
    logMessage: string
}

export function useMessageSubmit({
    getReceiverId,
    onSuccess,
    logMessage,
}: UseMessageSubmitOptions) {
    const { t } = useI18n()
    const toastStore = useToastStore()

    const {
        value: content,
        isSubmitting: isSending,
        submit: send,
        reset,
    } = useModalSubmit({
        initialValue: '',
        isValid: hasMessageContent,
        onInvalid: () => toastStore.addToast(t('user.message.inputContent'), 'warning'),
        onSubmit: async (messageContent) => {
            const receiverId = getReceiverId()
            if (receiverId == null) return false
            const trimmedContent = messageContent.trim()

            try {
                const { data } = await messageApi.sendMessage(receiverId, trimmedContent, { skipGlobalErrorHandler: true })
                if (!data.success) return false

                toastStore.addToast(t('user.message.sendSuccess'), 'success')
                return true
            } catch (error) {
                logger.error(logMessage, error)
                const errRes = extractErrorResponse(error as AxiosError)
                const toastMessage = errRes?.code === BLOCKED_BY_USER_CODE
                    ? t('user.message.blockedByUser')
                    : t('user.message.sendFailed')
                toastStore.addToast(toastMessage, 'error')
                return false
            }
        },
        onSuccess: () => {
            onSuccess?.()
        },
    })

    return {
        content,
        isSending,
        send,
        reset,
    }
}
