<template>
  <BaseButton :variant="isBlocked ? 'secondary' : 'danger'" size="sm" @click="toggleBlock" :disabled="loading">
    {{ isBlocked ? $t('user.block.unblock') : $t('user.block.blockButton') }}
  </BaseButton>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { userApi } from '@/api/user'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
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
  const isConfirmed = await confirm(isBlocked.value ? t('user.block.unblockConfirm') : t('user.block.blockConfirm'))
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
  } catch (error: unknown) {
    logger.error('Failed to toggle block:', error)
    toastStore.addToast(t('user.block.processFailed'), 'error')
  } finally {
    loading.value = false
  }
}
</script>
