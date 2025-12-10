<template>
  <div class="flex flex-col gap-1">
    <label v-if="label" :for="id" class="text-sm font-medium text-gray-700 dark:text-gray-200" :class="labelClass">
      {{ label }}
    </label>
    <input
      :id="id"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      class="input-base disabled:bg-gray-100 disabled:text-gray-500 dark:disabled:bg-gray-600 dark:disabled:text-gray-400"
      :class="{ 'border-red-500 focus:border-red-500 focus:ring-red-500': error }"
      @input="updateValue"
      @blur="$emit('blur', $event)"
    />
    <p v-if="error" class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  id?: string
  label?: string
  type?: string
  modelValue?: string | number
  placeholder?: string
  disabled?: boolean
  error?: string
  labelClass?: string
}>(), {
  id: () => `input-${Math.random().toString(36).substr(2, 9)}`,
  label: '',
  type: 'text',
  modelValue: '',
  placeholder: '',
  disabled: false,
  error: '',
  labelClass: ''
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
  (e: 'blur', event: FocusEvent): void
}>()

const updateValue = (event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>
