<script setup lang="ts">
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'

defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div>
    <AdminPageHeader :title="title" :description="description">
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
    </AdminPageHeader>

    <slot name="filters" />
    <slot name="toolbar" />
    <slot />
    <slot name="footer" />
  </div>
</template>
