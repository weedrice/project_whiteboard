<script setup lang="ts">
import PageHeader from '@/components/common/ui/PageHeader.vue'

defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div>
    <PageHeader :title="title" :description="description" size="admin">
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
    </PageHeader>

    <slot name="filters" />
    <slot name="toolbar" />
    <slot />
    <slot name="footer" />
  </div>
</template>
