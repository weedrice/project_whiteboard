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
import { ref } from 'vue'
import { reportApi } from '@/api/report'
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

const reportReason = ref('')
const isReporting = ref(false)

const handleReportUser = async () => {
    if (!reportReason.value.trim()) {
        toastStore.addToast(t('report.inputReason'), 'warning')
        return
    }
    isReporting.value = true
    try {
        const { data } = await reportApi.reportUser(props.userId, reportReason.value, '')
        if (data.success) {
            toastStore.addToast(t('report.reportSuccess'), 'success')
            reportReason.value = ''
            emit('close')
        }
    } catch (error) {
        logger.error('Failed to report user:', error)
        toastStore.addToast(t('report.reportFailed'), 'error')
    } finally {
        isReporting.value = false
    }
}
</script>
