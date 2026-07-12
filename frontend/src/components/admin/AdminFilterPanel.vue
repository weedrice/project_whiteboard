<script setup lang="ts">
import { ref } from 'vue'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import { SlidersHorizontal, ChevronDown } from 'lucide-vue-next'

withDefaults(defineProps<{
  className?: string
  collapsible?: boolean
  title?: string
}>(), {
  className: 'mt-6',
  collapsible: false,
  title: '',
})

const open = ref(true)
</script>

<template>
  <AdminPanel :class="className" padding="sm" :shadow="false">
    <button
      v-if="collapsible"
      type="button"
      class="mb-3 flex w-full items-center justify-between gap-3 text-left sm:hidden"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="flex items-center gap-2 text-sm font-semibold nv-title">
        <SlidersHorizontal class="h-4 w-4" aria-hidden="true" />
        {{ title }}
      </span>
      <ChevronDown class="h-4 w-4 transition-transform" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
    </button>
    <div :class="collapsible && !open ? 'hidden sm:block' : ''">
      <slot />
    </div>
  </AdminPanel>
</template>
