<template>
    <BaseModal :isOpen="isOpen" :title="$t('user.message.title')" @close="closeModal">
        <div class="nv-dialog-stack">
            <div class="nv-dialog-readonly">
                <span class="nv-dialog-readonly-label">{{ $t('user.message.receiver') }}</span>
                <span class="nv-dialog-readonly-value">{{ displayName }}</span>
            </div>
            <BaseTextarea
                id="messageContent"
                v-model="messageContent"
                :label="$t('user.message.content')"
                :maxlength="MESSAGE_CONTENT_MAX_LENGTH"
                :error="messageContentError"
                :disabled="isSendingMessage"
                rows="4"
            />
            <p class="nv-dialog-meta">
                {{ $t('user.message.contentLength', {
                    current: messageContent.length,
                    max: MESSAGE_CONTENT_MAX_LENGTH,
                }) }}
            </p>
        </div>
        <template #footer>
            <BaseButton @click="closeModal" variant="secondary" :disabled="isSendingMessage">{{ $t('common.cancel') }}</BaseButton>
            <BaseButton @click="handleSendMessage" :disabled="isSendingMessage">
                {{ isSendingMessage ? $t('common.messages.sending') : $t('common.send') }}
            </BaseButton>
        </template>
    </BaseModal>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
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
    if (isSendingMessage.value) return
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
