<template>
  <BaseButton :variant="isBlocked ? 'secondary' : 'danger'" size="sm" @click="toggleBlock" :disabled="loading">
    {{ isBlocked ? 'Unblock' : 'Block' }}
  </BaseButton>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { userApi } from '@/api/user'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'

const toastStore = useToastStore()
const { confirm } = useConfirm()

const props = withDefaults(defineProps<{
  userId: string | number
  initialBlocked?: boolean
}>(), {
  initialBlocked: false
})

const emit = defineEmits<{
  (e: 'block-change', isBlocked: boolean): void
}>()

const isBlocked = ref(props.initialBlocked)
const loading = ref(false)

const toggleBlock = async () => {
  const isConfirmed = await confirm(isBlocked.value ? 'Unblock this user?' : 'Block this user?')
  if (!isConfirmed) return

  loading.value = true
  try {
    if (isBlocked.value) {
      await userApi.unblockUser(props.userId)
      isBlocked.value = false
    } else {
      await userApi.blockUser(props.userId)
      isBlocked.value = true
    }
    emit('block-change', isBlocked.value)
  } catch (error) {
    logger.error('Failed to toggle block:', error)
    toastStore.addToast('Failed to process request', 'error')
  } finally {
    loading.value = false
  }
}
</script>

