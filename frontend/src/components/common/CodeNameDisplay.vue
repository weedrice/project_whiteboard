<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { useCodeService } from '@/composables/useCodeService'

const props = defineProps({
  code: {
    type: String,
    required: true
  },
  value: {
    type: String,
    required: true
  }
})

const { getCodes } = useCodeService()
const { data: codes } = getCodes(props.code)

const displayValue = computed(() => {
  if (!codes.value) return props.value
  const found = codes.value.find(c => c.value === props.value)
  return found ? found.name : props.value
})
</script>
