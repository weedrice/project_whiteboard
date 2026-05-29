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
import { useMessageSubmit } from '@/composables/useMessageSubmit'

const props = defineProps<{
    isOpen: boolean
    userId: number
    displayName: string
}>()

const emit = defineEmits(['close'])

const {
    content: messageContent,
    isSending: isSendingMessage,
    send: handleSendMessage
} = useMessageSubmit({
    getReceiverId: () => props.userId,
    logMessage: 'Failed to send message:',
    onSuccess: () => {
        emit('close')
    }
})
</script>
