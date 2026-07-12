<template>
    <BaseModal :isOpen="isOpen" :title="title" @close="emit('close')">
        <div class="p-4">
            <BaseInput :label="$t('report.target')" :modelValue="targetText" :disabled="true" class="mb-4" />
            <BaseTextarea
                id="reportReason"
                v-model="reportReason"
                :label="$t('report.reason')"
                rows="4"
                :placeholder="$t('report.inputReason')"
                :error="validation.visibleError('reason')"
                @blur="validation.touchField('reason', validationValues)"
            />
            <div class="mt-4 flex justify-end">
                <BaseButton @click="emit('close')" variant="secondary" class="mr-2">
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
import { useModalSubmit } from '@/composables/useModalSubmit'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { computed } from 'vue'
import { useFieldValidation } from '@/composables/useFieldValidation'

const { t } = useI18n()
const toastStore = useToastStore()

const props = withDefaults(defineProps<{
    isOpen: boolean
    targetText: string
    title?: string
    submitLabel?: string
    pendingLabel?: string
    submitVariant?: 'primary' | 'secondary' | 'danger' | 'ghost'
    submit: (reason: string) => boolean | Promise<boolean>
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

const {
    value: reportReason,
    isSubmitting: isReporting,
    submit: handleReport,
} = useModalSubmit({
    initialValue: '',
    isValid: (reason) => reason.trim().length > 0,
    onInvalid: () => toastStore.addToast(t('report.inputReason'), 'warning'),
    onSubmit: async (reason) => props.submit(reason.trim()),
    onSuccess: () => {
        emit('close')
    },
})

const validation = useFieldValidation<'reason'>({
    validators: { reason: (values) => String(values.reason ?? '').trim() ? '' : t('report.inputReason') },
    fieldIds: { reason: 'reportReason' },
})
const validationValues = computed(() => ({ reason: reportReason.value }))
const handleValidatedReport = () => {
    if (!validation.validateAll(validationValues.value)) {
        toastStore.addToast(t('report.inputReason'), 'warning')
        return
    }
    void handleReport()
}
</script>
