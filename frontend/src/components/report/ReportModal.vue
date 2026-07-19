<template>
    <BaseModal :isOpen="isOpen" :title="title" @close="closeModal">
        <div class="p-4">
            <BaseInput :label="$t('report.target')" :modelValue="targetText" :disabled="true" class="mb-4" />
            <BaseSelect
                v-model="reportReasonType"
                :label="$t('report.reasonType')"
                :options="reasonTypeOptions"
                class="mb-4"
                :disabled="isReporting"
            />
            <BaseTextarea
                id="reportReason"
                v-model="reportReason"
                :label="$t('report.reason')"
                rows="4"
                :placeholder="$t('report.inputReason')"
                :error="validation.visibleError('reason')"
                :maxlength="REPORT_REASON_MAX_LENGTH"
                :disabled="isReporting"
                @blur="validation.touchField('reason', validationValues)"
            />
            <div class="mt-4 flex justify-end">
                <BaseButton @click="closeModal" variant="secondary" class="mr-2">
                    {{ $t('common.cancel') }}
                </BaseButton>
                <BaseButton @click="handleValidatedReport" :disabled="isReporting" :variant="submitVariant">
                    {{ isReporting ? pendingLabel : submitLabel }}
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
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { useModalSubmit } from '@/composables/useModalSubmit'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { computed, ref, watch } from 'vue'
import type { ReportReasonType } from '@/types'
import { useFieldValidation } from '@/composables/useFieldValidation'
import {
    isReportReasonTooLong,
    isValidReportReason,
    REPORT_REASON_MAX_LENGTH,
} from '@/utils/reportValidation'

const { t } = useI18n()
const toastStore = useToastStore()

const props = withDefaults(defineProps<{
    isOpen: boolean
    targetIdentity: string | number
    targetText: string
    title?: string
    submitLabel?: string
    pendingLabel?: string
    submitVariant?: 'primary' | 'secondary' | 'danger' | 'ghost'
    submit: (reason: string, reasonType: ReportReasonType) => boolean | Promise<boolean>
}>(), {
    title: undefined,
    submitLabel: undefined,
    pendingLabel: undefined,
    submitVariant: 'primary',
})

const emit = defineEmits<{
    close: []
}>()

const title = props.title ?? t('report.title')
const submitLabel = props.submitLabel ?? t('common.report')
const pendingLabel = props.pendingLabel ?? t('common.messages.reporting')
const reportReasonType = ref<ReportReasonType>('ETC')
const reasonTypeOptions = computed(() => (['SPAM', 'ABUSE', 'ADULT', 'ETC'] as const).map((value) => ({
    value,
    label: t(`report.reasonTypes.${value}`),
})))

const {
    value: reportReason,
    isSubmitting: isReporting,
    submit: handleReport,
    reset: resetReport,
} = useModalSubmit({
    initialValue: '',
    isValid: isValidReportReason,
    onInvalid: (reason) => toastStore.addToast(
        isReportReasonTooLong(reason)
            ? t('report.reasonTooLong', { max: REPORT_REASON_MAX_LENGTH })
            : t('report.inputReason'),
        'warning',
    ),
    onSubmit: async (reason) => props.submit(reason.trim(), reportReasonType.value),
    onSuccess: () => {
        emit('close')
    },
})

const validation = useFieldValidation<'reason'>({
    validators: {
        reason: (values) => {
            const reason = String(values.reason ?? '')
            if (!reason.trim()) return t('report.inputReason')
            return isReportReasonTooLong(reason)
                ? t('report.reasonTooLong', { max: REPORT_REASON_MAX_LENGTH })
                : ''
        },
    },
    fieldIds: { reason: 'reportReason' },
})
const validationValues = computed(() => ({ reason: reportReason.value }))
const resetReportSession = () => {
    resetReport()
    reportReasonType.value = 'ETC'
    validation.clearValidation()
}
const closeModal = () => {
    resetReportSession()
    emit('close')
}
const handleValidatedReport = () => {
    if (!validation.validateAll(validationValues.value)) {
        toastStore.addToast(
            isReportReasonTooLong(reportReason.value)
                ? t('report.reasonTooLong', { max: REPORT_REASON_MAX_LENGTH })
                : t('report.inputReason'),
            'warning',
        )
        return
    }
    void handleReport()
}

watch(
    [() => props.isOpen, () => props.targetIdentity, () => props.targetText],
    resetReportSession,
    { flush: 'sync' },
)
</script>
