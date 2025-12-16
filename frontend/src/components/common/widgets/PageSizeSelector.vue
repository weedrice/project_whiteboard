<template>
  <div class="flex items-center space-x-2">
    <BaseSelect id="page-size" :modelValue="modelValue" @update:modelValue="handleUpdate" :label="$t('common.pageSize')"
      :options="selectOptions" inputClass="w-19 py-1" hideLabel />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'

const props = withDefaults(defineProps<{
  modelValue: number
  options?: number[]
}>(), {
  options: () => [15, 30, 50]
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
  (e: 'change'): void
}>()

const selectOptions = computed(() => {
  return props.options.map(opt => ({ value: opt, label: opt.toString() }))
})

const handleUpdate = (value: string | number) => {
  emit('update:modelValue', Number(value))
  emit('change')
}
</script>

