<template>
  <div class="flex items-center space-x-2">
    <label for="page-size" class="text-sm text-gray-500">{{ $t('common.pageSize') }}</label>
    <select
      id="page-size"
      :value="modelValue"
      @change="$emit('update:modelValue', Number($event.target.value)); $emit('change')"
      class="block w-19 pl-3 pr-6 py-1 text-base border border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
    >
      <option v-for="option in options" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Number,
    required: true
  },
  options: {
    type: Array,
    default: () => [15, 30, 50]
  }
})

defineEmits(['update:modelValue', 'change'])
</script>
