<script setup lang="ts">
import { ref } from 'vue'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import { SlidersHorizontal, ChevronDown } from 'lucide-vue-next'

withDefaults(defineProps<{
  className?: string
  collapsible?: boolean
  title?: string
  chips?: Array<{ key: string; label: string }>
}>(), {
  className: 'mt-6',
  collapsible: false,
  title: '',
  chips: () => [],
})
defineEmits<{ removeChip: [key: string] }>()

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
    <div v-if="chips.length" class="mb-3 flex flex-wrap gap-2" aria-live="polite">
      <button v-for="chip in chips" :key="chip.key" type="button" class="nv-chip max-w-full" @click="$emit('removeChip', chip.key)">
        <span class="truncate">{{ chip.label }}</span><span aria-hidden="true">×</span>
      </button>
    </div>
    <div :class="collapsible && !open ? 'hidden sm:block' : ''">
      <slot />
    </div>
  </AdminPanel>
</template>
