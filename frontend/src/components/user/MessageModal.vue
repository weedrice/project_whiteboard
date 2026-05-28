<template>
    <BaseModal :isOpen="isOpen" :title="$t('user.message.title')" @close="$emit('close')">
        <div class="p-4">
            <BaseInput :label="$t('user.message.receiver')" :modelValue="displayName" :disabled="true" class="mb-4" />
            <BaseTextarea id="messageContent" v-model="messageContent" :label="$t('user.message.content')" rows="4" />
            <div class="mt-4 flex justify-end">
                <BaseButton @click="$emit('close')" variant="secondary" class="mr-2">{{ $t('common.cancel') }}
                </BaseButton>
                <BaseButton @click="handleSendMessage" :disabled="isSendingMessage">
                    {{ isSendingMessage ? $t('common.messages.sending') : $t('common.send') }}
                </BaseButton>
            </div>
        </div>
    </BaseModal>
</template>

<script setup lang="ts">
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useModalSubmit } from '@/composables/useModalSubmit'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import type { AxiosError } from 'axios'
import { messageApi, BLOCKED_BY_USER_CODE } from '@/api/message'
import { extractErrorResponse } from '@/utils/errorHandler'
import { hasMessageContent } from '@/utils/messageValidation'

const { t } = useI18n()
const toastStore = useToastStore()

const props = defineProps<{
    isOpen: boolean
    userId: number
    displayName: string
}>()

const emit = defineEmits(['close'])

const {
    value: messageContent,
    isSubmitting: isSendingMessage,
    submit: handleSendMessage
} = useModalSubmit({
    initialValue: '',
    isValid: hasMessageContent,
    onInvalid: () => toastStore.addToast(t('user.message.inputContent'), 'warning'),
    onSubmit: async (content) => {
        try {
            const { data } = await messageApi.sendMessage(props.userId, content, { skipGlobalErrorHandler: true })
            if (!data.success) return false

            toastStore.addToast(t('user.message.sendSuccess'), 'success')
            return true
        } catch (error) {
            logger.error('Failed to send message:', error)
            const errRes = extractErrorResponse(error as AxiosError)
            const message = errRes?.code === BLOCKED_BY_USER_CODE
                ? t('user.message.blockedByUser')
                : t('user.message.sendFailed')
            toastStore.addToast(message, 'error')
            return false
        }
    },
    onSuccess: () => {
        emit('close')
    }
})
</script>
