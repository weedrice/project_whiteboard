<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.sanction.title')"
    :close-on-backdrop="!isLocked" :close-on-escape="!isLocked" @close="requestClose">
    <form @submit.prevent="submitSanction">
      <fieldset :disabled="isLocked" :inert="isLocked ? true : undefined" class="space-y-4 border-0 p-0">
      <div>
        <label class="block text-sm font-medium nv-text-muted">{{ t('admin.sanction.userLabel') }}</label>
        <p class="mt-1 text-sm nv-text">
          {{ sanctionTargetName }}<span v-if="user?.email"> ({{ user.email }})</span>
        </p>
      </div>

      <div>
        <BaseSelect id="sanction-type" v-model="form.type" :label="t('admin.sanction.type')"
          :options="sanctionTypeOptions" :disabled="isLocked || !sanctionTypesReady || !hasSanctionTypes"
          :error="sanctionTypesError ? t('common.messages.loadFailed') : sanctionValidation.visibleError('type')"
          @blur="sanctionValidation.touchField('type', sanctionValidationValues)" />
      </div>

      <div>
        <BaseSelect id="reason" v-model="form.reason" :label="t('admin.sanction.reason')" :disabled="isLocked"
          :error="sanctionValidation.visibleError('reason')" @blur="sanctionValidation.touchField('reason', sanctionValidationValues)">
          <option value="SPAM">{{ t('admin.sanction.reasons.SPAM') }}</option>
          <option value="ABUSIVE_LANGUAGE">{{ t('admin.sanction.reasons.ABUSIVE_LANGUAGE') }}</option>
          <option value="INAPPROPRIATE_CONTENT">{{ t('admin.sanction.reasons.INAPPROPRIATE_CONTENT') }}</option>
          <option value="OTHER">{{ t('admin.sanction.reasons.OTHER') }}</option>
        </BaseSelect>
      </div>

      <div>
        <BaseTextarea id="description" v-model="form.description" :label="t('admin.sanction.description')" rows="3"
          :placeholder="t('admin.sanction.descriptionPlaceholder')" maxlength="255" :disabled="isLocked"
          :error="sanctionValidation.visibleError('description')"
          @blur="sanctionValidation.touchField('description', sanctionValidationValues)" />
      </div>

      <div v-if="form.type !== 'WARNING'">
        <BaseInput id="duration" v-model="form.duration" type="number" :label="t('admin.sanction.duration')" min="1" step="1" :disabled="isLocked"
          :error="sanctionValidation.visibleError('duration')" @blur="sanctionValidation.touchField('duration', sanctionValidationValues)" />
        <p class="mt-1 text-xs nv-text-subtle">
          {{ form.type === 'MUTE' ? t('admin.sanction.durationHintMute') : t('admin.sanction.durationHintBan') }}
        </p>
      </div>

      <AdminModalActions class-name="mt-5">
        <BaseButton type="button" variant="secondary" :disabled="isLocked" @click="requestClose">{{ t('admin.sanction.cancel') }}</BaseButton>
        <BaseButton type="submit" variant="danger"
          :disabled="isSubmitting || loading || !sanctionTypesReady || !hasSanctionTypes">
          {{ isSubmitting || loading ? t('admin.sanction.processing') : t('admin.sanction.submit') }}
        </BaseButton>
      </AdminModalActions>
      </fieldset>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import { useAdmin } from '@/features/admin/useAdmin'
import { useToastStore } from '@/stores/toast'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { useAuthStore } from '@/stores/auth'
import type { SanctionData } from '@/types'
import {
  COMMON_CODE_TYPES,
  useStrictSupportedCommonCodeValues,
} from '@/composables/useCommonCodeDetails'

type SanctionType = SanctionData['type']

const props = defineProps<{
  isOpen: boolean
  user: {
    userId?: number
    id?: number
    displayName?: string
    nickname?: string
    name?: string
    email?: string
    sanctionContentId?: number
    sanctionContentType?: 'POST' | 'COMMENT' | 'USER'
    reportId?: number
    modalRevision?: number
    sessionGeneration?: number
  } | null
}>()

const { t } = useI18n()
const SUPPORTED_SANCTION_TYPES: SanctionType[] = ['WARNING', 'MUTE', 'BAN']
const {
  values: activeSanctionTypes,
  isReady: sanctionTypesReady,
  isError: sanctionTypesError,
} = useStrictSupportedCommonCodeValues(
  COMMON_CODE_TYPES.SANCTION_TYPE,
  SUPPORTED_SANCTION_TYPES,
  { enabled: computed(() => props.isOpen) },
)
const sanctionTypeOptions = computed(() => activeSanctionTypes.value.map((value) => ({
  value,
  label: t(`admin.sanction.types.${value}`),
})))
const hasSanctionTypes = computed(() => sanctionTypeOptions.value.length > 0)
const defaultSanctionType = (): SanctionType => activeSanctionTypes.value.includes('WARNING')
  ? 'WARNING'
  : activeSanctionTypes.value[0] ?? 'WARNING'

interface SanctionCompletedIntent {
  targetUserId: number
  reportId?: number
  modalRevision?: number
  sessionGeneration: number
}

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'sanctioned', intent: SanctionCompletedIntent): void
}>()

const toastStore = useToastStore()
const authStore = useAuthStore()
const { useSanctionUser } = useAdmin()
const { mutateAsync: sanctionUser, isPending: loading } = useSanctionUser()
const isSubmitting = ref(false)
const isLocked = computed(() => isSubmitting.value || loading.value)

const sanctionTargetName = computed(() => props.user?.displayName || props.user?.nickname || props.user?.name || t('common.messages.unknown'))

const form = reactive<{
  type: SanctionType
  reason: string
  description: string
  duration: number | ''
}>({
  type: defaultSanctionType(),
  reason: 'SPAM',
  description: '',
  duration: ''
})
type SanctionField = 'type' | 'reason' | 'description' | 'duration'
const sanctionValidation = useFieldValidation<SanctionField>({
  validators: {
    type: (values) => activeSanctionTypes.value.includes(values.type as SanctionType)
      ? ''
      : t('admin.sanction.typeRequired'),
    reason: (values) => String(values.reason ?? '').trim() ? '' : t('admin.sanction.reason'),
    description: (values) => String(values.description ?? '').trim().length <= 255 ? '' : t('admin.sanction.description'),
    duration: (values) => {
      if (values.type === 'WARNING') return ''
      if (values.type === 'BAN' && values.duration === '') return ''
      const duration = Number(values.duration)
      return Number.isInteger(duration) && duration > 0 ? '' : t('admin.sanction.durationRequired')
    },
  },
  fieldIds: { type: 'sanction-type', reason: 'reason', duration: 'duration' },
})
const sanctionValidationValues = computed(() => ({
  type: form.type,
  reason: form.reason,
  description: form.description,
  duration: form.duration,
}))

function resetForm() {
  form.type = defaultSanctionType()
  form.reason = 'SPAM'
  form.description = ''
  form.duration = ''
  sanctionValidation.clearValidation()
}

watch(() => form.type, (type, previousType) => {
  if (type === 'WARNING') {
    form.duration = ''
  } else if (type === 'MUTE' && (previousType === 'WARNING' || form.duration === '')) {
    form.duration = 7
  }
  sanctionValidation.errors.duration = ''
})

watch(activeSanctionTypes, (types) => {
  if (types.length > 0 && !types.includes(form.type)) {
    form.type = defaultSanctionType()
  }
}, { immediate: true })

watch(
  () => [props.isOpen, props.user?.userId ?? props.user?.id, props.user?.reportId, props.user?.modalRevision] as const,
  ([isOpen]) => {
    if (isOpen) resetForm()
  },
  { flush: 'sync' },
)

async function submitSanction() {
  if (!props.user || isSubmitting.value) return
  if (!sanctionTypesReady.value) return
  if (!sanctionValidation.validateAll(sanctionValidationValues.value)) return

  const targetUserId = props.user.userId ?? props.user.id ?? 0
  if (!targetUserId) return
  const openSessionGeneration = props.user.sessionGeneration ?? authStore.sessionGeneration
  if (openSessionGeneration !== authStore.sessionGeneration) return
  const intent: SanctionCompletedIntent = {
    targetUserId,
    reportId: props.user.reportId,
    modalRevision: props.user.modalRevision,
    sessionGeneration: openSessionGeneration,
  }
  const targetName = props.user.displayName || props.user.nickname || props.user.name || t('common.messages.unknown')
  const contentId = props.user.sanctionContentId
  const contentType = props.user.sanctionContentType
  isSubmitting.value = true

  try {
    const description = form.description.trim()
    await sanctionUser({
      targetUserId,
      type: form.type,
      remark: description || form.reason,
      endDate: form.type === 'WARNING' ? undefined : resolveEndDate(),
      contentId,
      contentType
    })

    const isCurrentIntent = props.isOpen
      && authStore.sessionGeneration === intent.sessionGeneration
      && (props.user?.userId ?? props.user?.id) === intent.targetUserId
      && props.user?.reportId === intent.reportId
      && props.user?.modalRevision === intent.modalRevision
    if (!isCurrentIntent) return

    toastStore.addToast(t('admin.sanction.success', { name: targetName }), 'success')
    emit('sanctioned', intent)
    emit('close')
  } catch {
    // Error handled globally
  } finally {
    isSubmitting.value = false
  }
}

function requestClose() {
  if (isLocked.value) return
  emit('close')
}

function resolveEndDate() {
  if (form.duration === '') return undefined
  const durationDays = Number(form.duration)
  if (!Number.isInteger(durationDays) || durationDays <= 0) {
    return undefined
  }

  const endDate = new Date()
  endDate.setDate(endDate.getDate() + durationDays)
  return formatServerDateTime(endDate)
}

/**
 * 제재 종료 시각을 서버 기준(KST) 벽시계로 만든다.
 *
 * 브라우저 로컬 벽시계를 그대로 보내면 서버가 그것을 KST로 읽어, 한국 밖 관리자가 건
 * 7일 정지가 실제로는 시차만큼 일찍 풀린다. 서버가 읽는 기준에 맞춰 만든다.
 */
function formatServerDateTime(date: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)

  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? ''
  const hour = get('hour') === '24' ? '00' : get('hour')

  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}:${get('second')}`
}
</script>
