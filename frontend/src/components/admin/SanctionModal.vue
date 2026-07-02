<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.sanction.title')" @close="$emit('close')">
    <form @submit.prevent="submitSanction" class="space-y-4">
      <div>
        <label class="block text-sm font-medium nv-text-muted">{{ t('admin.sanction.userLabel') }}</label>
        <p class="mt-1 text-sm nv-text">
          {{ sanctionTargetName }}<span v-if="user?.email"> ({{ user.email }})</span>
        </p>
      </div>

      <div>
        <BaseSelect id="reason" v-model="form.reason" :label="t('admin.sanction.reason')">
          <option value="SPAM">{{ t('admin.sanction.reasons.SPAM') }}</option>
          <option value="ABUSIVE_LANGUAGE">{{ t('admin.sanction.reasons.ABUSIVE_LANGUAGE') }}</option>
          <option value="INAPPROPRIATE_CONTENT">{{ t('admin.sanction.reasons.INAPPROPRIATE_CONTENT') }}</option>
          <option value="OTHER">{{ t('admin.sanction.reasons.OTHER') }}</option>
        </BaseSelect>
      </div>

      <div>
        <BaseTextarea id="description" v-model="form.description" :label="t('admin.sanction.description')" rows="3"
          :placeholder="t('admin.sanction.descriptionPlaceholder')" />
      </div>

      <div>
        <BaseInput id="duration" v-model="form.duration" type="number" :label="t('admin.sanction.duration')" min="1" />
        <p class="mt-1 text-xs nv-text-subtle">{{ t('admin.sanction.durationHint') }}</p>
      </div>

      <AdminModalActions class-name="mt-5">
        <BaseButton type="button" variant="secondary" @click="$emit('close')">{{ t('admin.sanction.cancel') }}</BaseButton>
        <BaseButton type="submit" variant="danger" :disabled="loading">
          {{ loading ? t('admin.sanction.processing') : t('admin.sanction.submit') }}
        </BaseButton>
      </AdminModalActions>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()

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
  } | null
}>()

const emit = defineEmits<{ (e: 'close'): void; (e: 'sanctioned'): void }>()

const toastStore = useToastStore()
const { useSanctionUser } = useAdmin()
const { mutateAsync: sanctionUser, isPending: loading } = useSanctionUser()

const sanctionTargetName = computed(() => props.user?.displayName || props.user?.nickname || props.user?.name || t('common.messages.unknown'))

const form = reactive({
  reason: 'SPAM',
  description: '',
  duration: 7
})

async function submitSanction() {
  if (!props.user) return

  try {
    const userId = props.user.userId ?? props.user.id ?? 0
    const description = form.description.trim()
    await sanctionUser({
      targetUserId: userId,
      type: 'BAN',
      remark: description || form.reason,
      endDate: resolveEndDate(),
      contentId: props.user.sanctionContentId,
      contentType: props.user.sanctionContentType
    })

    const name = props.user.displayName || props.user.nickname || props.user.name || t('common.messages.unknown')
    toastStore.addToast(t('admin.sanction.success', { name }), 'success')
    emit('sanctioned')
    emit('close')
  } catch {
    // Error handled globally
  }
}

function resolveEndDate() {
  const durationDays = Number(form.duration)
  if (!Number.isFinite(durationDays) || durationDays <= 0) {
    return undefined
  }

  const endDate = new Date()
  endDate.setDate(endDate.getDate() + durationDays)
  return formatLocalDateTime(endDate)
}

function formatLocalDateTime(date: Date) {
  const pad = (value: number) => value.toString().padStart(2, '0')

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>
