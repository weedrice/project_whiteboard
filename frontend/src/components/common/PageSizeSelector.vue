<template>
  <div class="flex items-center space-x-2">
    <label for="page-size" class="text-sm text-gray-500 dark:text-gray-400">{{ $t('common.pageSize') }}</label>
    <select
      id="page-size"
      :value="modelValue"
      @change="handleChange"
      class="block w-19 pl-3 pr-6 py-1 text-base border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md transition-colors duration-200"
    >
      <option v-for="option in options" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  modelValue: number
  options?: number[]
}>(), {
  options: () => [15, 30, 50]
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
  (e: 'change'): void
}>()

const handleChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', Number(target.value))
  emit('change')
}
</script>
