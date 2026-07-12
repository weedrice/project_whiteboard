<template>
  <div class="flex flex-col gap-1" :class="$attrs.class as string" :style="$attrs.style as StyleValue">
    <label
      v-if="label"
      :for="inputId"
      :class="hideLabel ? 'sr-only' : ['text-xs sm:text-sm font-medium nv-text', labelClass]"
    >
      {{ label }}
    </label>
    <div class="relative">
      <div v-if="$slots.prefix" class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none" aria-hidden="true">
        <slot name="prefix"></slot>
      </div>
      <input v-bind="{ ...$attrs, class: undefined, style: undefined }" 
        :id="inputId" 
        :type="type" 
        :value="modelValue"
        :placeholder="placeholder" 
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="input-base"
        :class="[
          { 'is-invalid': error },
          { 'pl-10': $slots.prefix },
          { 'pr-10': $slots.suffix },
          inputClass
        ]" 
        @input="updateValue" 
        @blur="$emit('blur', $event)" />
      <div v-if="$slots.suffix" class="absolute inset-y-0 right-0 pr-3 flex items-center">
        <slot name="suffix"></slot>
      </div>
    </div>
    <p v-if="error && !hideErrorText" :id="`${inputId}-error`" class="text-xs sm:text-sm nv-form-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, useAttrs, useId, type StyleValue } from 'vue'

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
  /** Applies the invalid input state without rendering inline error text. */
  hideErrorText?: boolean
}>(), {
  label: '',
  type: 'text',
  modelValue: '',
  placeholder: '',
  disabled: false,
  error: '',
  labelClass: '',
  inputClass: '',
  hideLabel: false,
  hideErrorText: false
})

const generatedId = useId()
const inputId = computed(() => props.id ?? generatedId)
const attrs = useAttrs()
const describedBy = computed(() => [
  attrs['aria-describedby'],
  props.error && !props.hideErrorText ? `${inputId.value}-error` : undefined,
].filter(Boolean).join(' ') || undefined)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
  (e: 'blur', event: FocusEvent): void
}>()

const updateValue = (event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>
