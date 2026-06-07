<template>
  <BaseButton :variant="isBlocked ? 'secondary' : 'danger'" size="sm" @click="toggleBlock" :disabled="loading">
    {{ isBlocked ? $t('user.block.unblock') : $t('user.block.blockButton') }}
  </BaseButton>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useI18n } from 'vue-i18n'
import { useUser } from '@/composables/useUser'
import { useUserBlockAction } from '@/composables/useUserBlockAction'

const { t } = useI18n()
const { useBlockUser, useUnblockUser } = useUser()
const { mutateAsync: blockUser, isPending: isBlocking } = useBlockUser()
const { mutateAsync: unblockUser, isPending: isUnblocking } = useUnblockUser()
const { runUserBlockAction } = useUserBlockAction()

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
const isSubmitting = ref(false)
const loading = computed(() => isSubmitting.value || isBlocking.value || isUnblocking.value)

const toggleBlock = async () => {
  isSubmitting.value = true
  try {
    await runUserBlockAction({
      confirmMessage: isBlocked.value ? t('user.block.unblockConfirm') : t('user.block.blockConfirm'),
      failureMessage: t('user.block.processFailed'),
      logMessage: 'Failed to toggle block:',
      action: () => isBlocked.value ? unblockUser(props.userId) : blockUser(props.userId),
      onSuccess: () => {
        isBlocked.value = !isBlocked.value
        emit('block-change', isBlocked.value)
      },
    })
  } finally {
    isSubmitting.value = false
  }
}
</script>
