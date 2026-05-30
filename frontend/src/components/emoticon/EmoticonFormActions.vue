<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  isSubmitting: boolean
  isFormValid: boolean
  uploadProgress: {
    current: number
    total: number
  }
  submitText: string
  submittingText: string
}>()
</script>

<template>
  <div class="flex flex-col items-end gap-2">
    <div v-if="isSubmitting && uploadProgress.total > 0" class="text-sm nv-text-muted">
      이미지 업로드 중... ({{ uploadProgress.current }}/{{ uploadProgress.total }})
    </div>
    <div class="flex gap-3">
      <slot name="before-submit" />
      <BaseButton
        type="submit"
        :disabled="!isFormValid || isSubmitting"
        variant="primary"
        size="lg"
      >
        {{ isSubmitting ? submittingText : submitText }}
      </BaseButton>
    </div>
  </div>
</template>
