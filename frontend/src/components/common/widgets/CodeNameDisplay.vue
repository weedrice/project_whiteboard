<template>
  <span>{{ displayValue }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useCodeService } from '@/composables/useCodeService'
import type { Code } from '@/types'

const props = defineProps<{
  code: string
  value: string
}>()

const { getCodes } = useCodeService()
const { data: codes } = getCodes(props.code)

const displayValue = computed(() => {
  if (!codes.value || !codes.value.success || !codes.value.data) return props.value
  const found = codes.value.data.find((c: Code) => (c.value || c.code) === props.value)
  return found ? found.name : props.value
})
</script>
