<script setup lang="ts">
import AdminPanel from '@/components/admin/AdminPanel.vue'

withDefaults(defineProps<{
  title?: string
  description?: string
  className?: string
  maxWidthClass?: string
}>(), {
  title: '',
  description: '',
  className: 'mt-6',
  maxWidthClass: undefined,
})
</script>

<template>
  <AdminPanel :class="className" padding="sm" :max-width-class="maxWidthClass">
    <div v-if="title || description" class="mb-5">
      <h3 v-if="title" class="text-lg font-medium leading-6 nv-title">{{ title }}</h3>
      <p v-if="description" class="mt-2 text-sm nv-text-subtle">{{ description }}</p>
    </div>
    <slot />
  </AdminPanel>
</template>
