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
import { ref } from 'vue'
import { messageApi } from '@/api/message'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const toastStore = useToastStore()

const props = defineProps<{
    isOpen: boolean
    userId: number
    displayName: string
}>()

const emit = defineEmits(['close'])

const messageContent = ref('')
const isSendingMessage = ref(false)

const handleSendMessage = async () => {
    if (!messageContent.value.trim()) {
        toastStore.addToast(t('user.message.inputContent'), 'warning')
        return
    }
    isSendingMessage.value = true
    try {
        const { data } = await messageApi.sendMessage(props.userId, messageContent.value)
        if (data.success) {
            toastStore.addToast(t('user.message.sendSuccess'), 'success')
            messageContent.value = ''
            emit('close')
        }
    } catch (error) {
        logger.error('Failed to send message:', error)
        toastStore.addToast(t('user.message.sendFailed'), 'error')
    } finally {
        isSendingMessage.value = false
    }
}
</script>
