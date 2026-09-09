<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { DraftActionId, DraftPresentation } from '@/features/board/posts/draft/postDraftContracts'

withDefaults(defineProps<{
  presentation: DraftPresentation
  inline?: boolean
}>(), {
  inline: false,
})

defineEmits<{
  action: [action: DraftActionId]
}>()
</script>

<template>
  <div :class="inline ? 'contents' : 'flex flex-col gap-2'">
    <BaseButton
      v-for="action in presentation.actions"
      :key="action.id"
      type="button"
      :variant="action.variant"
      size="sm"
      :full-width="!inline"
      :class="inline ? 'min-h-[40px] flex-1' : 'min-h-[36px] w-full'"
      :disabled="action.disabled"
      :to="action.to"
      @click="$emit('action', action.id)"
    >
      {{ action.label }}
    </BaseButton>
  </div>
</template>
