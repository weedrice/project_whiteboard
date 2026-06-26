<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = withDefaults(defineProps<{
  searchLabel?: string
  resetLabel?: string
  searchClass?: string
  resetClass?: string
  gapClass?: string
}>(), {
  searchClass: 'btn-search',
  resetClass: 'btn-reset',
  gapClass: 'gap-2',
})
const { t } = useI18n()
const effectiveSearchLabel = computed(() => props.searchLabel ?? t('admin.common.search'))
const effectiveResetLabel = computed(() => props.resetLabel ?? t('admin.common.reset'))

const emit = defineEmits<{
  search: []
  reset: []
}>()
</script>

<template>
  <div :class="['filter-item filter-item--actions flex items-center', gapClass]">
    <BaseButton type="button" variant="primary" size="sm" :class="searchClass" @click="emit('search')">
      <Search class="mr-1 h-4 w-4" aria-hidden="true" />
      {{ effectiveSearchLabel }}
    </BaseButton>
    <BaseButton type="button" variant="secondary" size="sm" :class="resetClass" @click="emit('reset')">
      {{ effectiveResetLabel }}
    </BaseButton>
  </div>
</template>
