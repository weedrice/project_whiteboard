<template>
    <BaseModal :isOpen="isOpen" :title="$t('report.title')" @close="$emit('close')">
        <div class="p-4">
            <BaseInput :label="$t('report.target')" :modelValue="displayName" :disabled="true" class="mb-4" />
            <BaseTextarea id="reportReason" v-model="reportReason" :label="$t('report.reason')" rows="4" />
            <div class="mt-4 flex justify-end">
                <BaseButton @click="$emit('close')" variant="secondary" class="mr-2">{{ $t('common.cancel') }}
                </BaseButton>
                <BaseButton @click="handleReportUser" :disabled="isReporting">
                    {{ isReporting ? $t('common.messages.reporting') : $t('common.report') }}
                </BaseButton>
            </div>
        </div>
    </BaseModal>
</template>

<script setup lang="ts">
import { reportApi } from '@/api/report'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useModalSubmit } from '@/composables/useModalSubmit'
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

const {
    value: reportReason,
    isSubmitting: isReporting,
    submit: handleReportUser
} = useModalSubmit({
    initialValue: '',
    isValid: (reason) => reason.trim().length > 0,
    onInvalid: () => toastStore.addToast(t('report.inputReason'), 'warning'),
    onSubmit: async (reason) => {
        try {
            const { data } = await reportApi.reportUser(props.userId, reason, '', { skipGlobalErrorHandler: true })
            if (!data.success) return false

            toastStore.addToast(t('report.reportSuccess'), 'success')
            return true
        } catch (error) {
            logger.error('Failed to report user:', error)
            toastStore.addToast(t('report.reportFailed'), 'error')
            return false
        }
    },
    onSuccess: () => {
        emit('close')
    }
})
</script>
