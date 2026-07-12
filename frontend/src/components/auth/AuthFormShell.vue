<script setup lang="ts">
import { ChevronLeft } from 'lucide-vue-next'

withDefaults(defineProps<{
  title?: string
  description?: string
  backTo?: string
  contentWidthClass?: string
}>(), {
  title: '',
  description: '',
  backTo: '',
  contentWidthClass: 'w-full sm:w-[80%]',
})
</script>

<template>
  <div class="relative flex h-full flex-col justify-center p-5 sm:p-8">
    <div v-if="backTo" class="absolute left-4 top-4">
      <router-link
        :to="backTo"
        class="nv-focus-ring flex min-h-11 items-center rounded-md px-1 nv-text-subtle transition-colors hover:text-[var(--nv-text)]"
      >
        <ChevronLeft class="mr-1 h-5 w-5" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>

    <div v-if="title || description || $slots.header" class="mb-8 text-center">
      <slot name="header">
        <h1 class="text-2xl font-bold nv-title">{{ title }}</h1>
        <p v-if="description" class="mt-2 whitespace-pre-line text-sm nv-text-muted">{{ description }}</p>
      </slot>
    </div>

    <div :class="[contentWidthClass, 'mx-auto max-w-full']">
      <slot />
    </div>
  </div>
</template>
