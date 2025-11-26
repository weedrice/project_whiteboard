<template>
  <button
    :type="type"
    :class="[
      'px-4 py-2 rounded-md font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2',
      variantClasses,
      disabled ? 'opacity-50 cursor-not-allowed' : ''
    ]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot></slot>
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'button'
  },
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'secondary', 'danger', 'ghost'].includes(value)
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

defineEmits(['click'])

const variantClasses = computed(() => {
  switch (props.variant) {
    case 'primary':
      return 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500'
    case 'secondary':
      return 'bg-gray-200 text-gray-700 hover:bg-gray-300 focus:ring-gray-500'
    case 'danger':
      return 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500'
    case 'ghost':
      return 'bg-transparent text-gray-600 hover:bg-gray-100 focus:ring-gray-500'
    default:
      return 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500'
  }
})
</script>
