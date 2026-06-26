<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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
const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col items-end gap-2">
    <div v-if="isSubmitting && uploadProgress.total > 0" class="text-sm nv-text-muted">
      {{ t('emoticon.upload.progress', { current: uploadProgress.current, total: uploadProgress.total }) }}
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
