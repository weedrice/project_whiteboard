<template>
  <div class="flex flex-col gap-1" :class="$attrs.class as string" :style="$attrs.style as any">
    <label v-if="label && !hideLabel" :for="id" class="text-sm font-medium text-gray-700 dark:text-gray-200"
      :class="labelClass">
      {{ label }}
    </label>
    <div class="relative">
      <div v-if="$slots.prefix" class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none" aria-hidden="true">
        <slot name="prefix"></slot>
      </div>
      <input v-bind="{ ...$attrs, class: undefined, style: undefined }" 
        :id="id" 
        :type="type" 
        :value="modelValue"
        :placeholder="placeholder" 
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? `${id}-error` : undefined"
        class="input-base disabled:bg-gray-100 disabled:text-gray-500 dark:disabled:bg-gray-600 dark:disabled:text-gray-400"
        :class="[
          { 'border-red-500 focus:border-red-500 focus:ring-red-500': error },
          { 'pl-10': $slots.prefix },
          { 'pr-10': $slots.suffix },
          inputClass
        ]" 
        @input="updateValue" 
        @blur="$emit('blur', $event)" />
      <div v-if="$slots.suffix" class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none" aria-hidden="true">
        <slot name="suffix"></slot>
      </div>
    </div>
    <p v-if="error" :id="`${id}-error`" class="text-sm text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  inheritAttrs: false
})

const props = withDefaults(defineProps<{
  id?: string
  label?: string
  type?: string
  modelValue?: string | number
  placeholder?: string
  disabled?: boolean
  error?: string
  labelClass?: string
  inputClass?: string
  hideLabel?: boolean
}>(), {
  id: () => `input-${Math.random().toString(36).substr(2, 9)}`,
  label: '',
  type: 'text',
  modelValue: '',
  placeholder: '',
  disabled: false,
  error: '',
  labelClass: '',
  inputClass: '',
  hideLabel: false
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
