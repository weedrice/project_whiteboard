<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.sanction.title')" @close="$emit('close')">
    <form @submit.prevent="submitSanction" class="space-y-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('admin.sanction.userLabel') }}</label>
        <p class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ user?.displayName || user?.nickname }} ({{ user?.email }})</p>
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
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('admin.sanction.durationHint') }}</p>
      </div>

      <div class="flex justify-end space-x-3 mt-5">
        <BaseButton type="button" variant="secondary" @click="$emit('close')">{{ t('admin.sanction.cancel') }}</BaseButton>
        <BaseButton type="submit" variant="danger" :disabled="loading">
          {{ loading ? t('admin.sanction.processing') : t('admin.sanction.submit') }}
        </BaseButton>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()

const props = defineProps<{
  isOpen: boolean
  user: { userId?: number; id?: number; displayName?: string; nickname?: string; name?: string; email?: string } | null
}>()

const emit = defineEmits<{ (e: 'close'): void; (e: 'sanctioned'): void }>()

const toastStore = useToastStore()
const { useSanctionUser } = useAdmin()
const { mutateAsync: sanctionUser, isPending: loading } = useSanctionUser()

const form = reactive({
  reason: 'SPAM',
  description: '',
  duration: 7
})

async function submitSanction() {
  if (!props.user) return

  try {
    const userId = props.user.userId ?? props.user.id ?? 0
    await sanctionUser({
      userId,
      type: 'BAN',
      reason: form.description || form.reason
    })

    const name = props.user.displayName || props.user.nickname || props.user.name || '해당 사용자'
    toastStore.addToast(t('admin.sanction.success', { name }), 'success')
    emit('sanctioned')
    emit('close')
  } catch {
    // Error handled globally
  }
}
</script>

