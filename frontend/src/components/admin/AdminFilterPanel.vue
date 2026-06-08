<script setup lang="ts">
import AdminPanel from '@/components/admin/AdminPanel.vue'

withDefaults(defineProps<{
  className?: string
}>(), {
  className: 'mt-6',
})
</script>

<template>
  <AdminPanel :class="className" padding="sm" :shadow="false">
    <slot />
  </AdminPanel>
</template>
