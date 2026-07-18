<template>
    <BaseModal :isOpen="isOpen" :title="$t('user.message.title')" @close="closeModal">
        <div class="p-4">
            <BaseInput :label="$t('user.message.receiver')" :modelValue="displayName" :disabled="true" class="mb-4" />
            <BaseTextarea
                id="messageContent"
                v-model="messageContent"
                :label="$t('user.message.content')"
                :maxlength="MESSAGE_CONTENT_MAX_LENGTH"
                :error="messageContentError"
                :disabled="isSendingMessage"
                rows="4"
            />
            <p class="mt-1 text-right text-xs nv-text-muted">
                {{ $t('user.message.contentLength', {
                    current: messageContent.length,
                    max: MESSAGE_CONTENT_MAX_LENGTH,
                }) }}
            </p>
            <div class="mt-4 flex justify-end">
                <BaseButton @click="closeModal" variant="secondary" class="mr-2">{{ $t('common.cancel') }}
                </BaseButton>
                <BaseButton @click="handleSendMessage" :disabled="isSendingMessage">
                    {{ isSendingMessage ? $t('common.messages.sending') : $t('common.send') }}
                </BaseButton>
            </div>
        </div>
    </BaseModal>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useMessageSubmit } from '@/features/user/messages/useMessageSubmit'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import { isMessageContentTooLong, MESSAGE_CONTENT_MAX_LENGTH } from '@/utils/messageValidation'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
    isOpen: boolean
    userId: number
    displayName: string
}>()

const emit = defineEmits(['close'])
const { t } = useI18n()

const {
    content: messageContent,
    isSending: isSendingMessage,
    send: handleSendMessage,
    reset: resetMessage,
    cancel: cancelMessage,
} = useMessageSubmit({
    getReceiverId: () => props.userId,
    logMessage: 'Failed to send message:',
    onSuccess: () => {
        emit('close')
    }
})

const resetMessageSession = () => {
    cancelMessage()
    resetMessage()
}

const closeModal = () => {
    resetMessageSession()
    emit('close')
}

watch(
    [() => props.isOpen, () => props.userId, () => props.displayName],
    resetMessageSession,
    { flush: 'sync' },
)

const messageContentError = computed(() => isMessageContentTooLong(messageContent.value)
    ? t('user.message.contentTooLong', { max: MESSAGE_CONTENT_MAX_LENGTH })
    : '')

usePwaReloadBlocker(computed(() => props.isOpen && messageContent.value.trim().length > 0))
</script>
