<template>
  <span>{{ displayValue }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useCodeService } from '@/composables/useCodeService'

const props = defineProps<{
  code: string
  value: string
}>()

const { getCodes } = useCodeService()
const { data: codes } = getCodes(props.code)

const displayValue = computed(() => {
  if (!codes.value) return props.value
  const found = codes.value.find((c: any) => c.value === props.value)
  return found ? found.name : props.value
})
</script>
