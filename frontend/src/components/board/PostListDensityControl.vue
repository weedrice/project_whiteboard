<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSegmentedControl, { type SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import type { PostListDensity } from '@/components/board/postListDensity'

defineProps<{
  modelValue: PostListDensity
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', density: PostListDensity): void
}>()

const { t } = useI18n()
const densityOptions = computed<SegmentedControlOption[]>(() => [
  { value: 'default', label: t('board.list.densityDefault') },
  { value: 'compact', label: t('board.list.densityCompact') },
])

function updateDensity(density: string) {
  if (density === 'default' || density === 'compact') {
    emit('update:modelValue', density)
  }
}
</script>

<template>
  <BaseSegmentedControl
    :model-value="modelValue"
    :options="densityOptions"
    :label="t('board.list.densityLabel')"
    variant="pill"
    @update:model-value="updateDensity"
  />
</template>
