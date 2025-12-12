<template>
  <BaseModal :isOpen="isOpen" title="Sanction User" @close="$emit('close')">
    <form @submit.prevent="submitSanction" class="space-y-4">
      <div>
        <label class="block text-sm font-medium text-gray-700">User</label>
        <p class="mt-1 text-sm text-gray-900">{{ user?.nickname }} ({{ user?.email }})</p>
      </div>

      <div>
        <BaseSelect id="reason" v-model="form.reason" label="Reason">
          <option value="SPAM">Spam</option>
          <option value="ABUSIVE_LANGUAGE">Abusive Language</option>
          <option value="INAPPROPRIATE_CONTENT">Inappropriate Content</option>
          <option value="OTHER">Other</option>
        </BaseSelect>
      </div>

      <div>
        <BaseTextarea id="description" v-model="form.description" label="Description" rows="3"
          placeholder="Additional details..." />
      </div>

      <div>
        <BaseInput id="duration" v-model="form.duration" type="number" label="Duration (Days)" min="1" />
        <p class="mt-1 text-xs text-gray-500">Leave empty for permanent ban (9999 days).</p>
      </div>

      <div class="flex justify-end space-x-3 mt-5">
        <BaseButton type="button" variant="secondary" @click="$emit('close')">Cancel</BaseButton>
        <BaseButton type="submit" variant="danger" :disabled="loading">
          {{ loading ? 'Processing...' : 'Sanction' }}
        </BaseButton>
      </div>
    </form>
  </BaseModal>
</template>

<script setup>
import { ref, reactive } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseSelect from '@/components/common/BaseSelect.vue'
import BaseTextarea from '@/components/common/BaseTextarea.vue'
import { useAdmin } from '@/composables/useAdmin'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'

const props = defineProps({
  isOpen: Boolean,
  user: Object
})

const emit = defineEmits(['close', 'sanctioned'])

const toastStore = useToastStore()
const { useSanctionUser } = useAdmin()
const { mutateAsync: sanctionUser, isLoading: loading } = useSanctionUser()

const form = reactive({
  reason: 'SPAM',
  description: '',
  duration: 7
})

const submitSanction = async () => {
  if (!props.user) return

  try {
    await sanctionUser({
      userId: props.user.userId || props.user.id,
      ...form
    })

    toastStore.addToast(`User ${props.user.nickname || props.user.name} has been sanctioned.`, 'success')
    emit('sanctioned')
    emit('close')
  } catch (error) {
    // Error handled globally
  }
}
</script>
