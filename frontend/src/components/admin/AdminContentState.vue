<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  empty?: boolean
  loadingText?: string
  emptyText?: string
  paddingClass?: string
}>(), {
  loading: false,
  empty: false,
  loadingText: undefined,
  emptyText: undefined,
  paddingClass: 'py-8',
})

const { t } = useI18n()

const resolvedLoadingText = computed(() => props.loadingText ?? t('common.loading'))
const resolvedEmptyText = computed(() => props.emptyText ?? t('common.noData'))
</script>

<template>
  <div v-if="loading" :class="paddingClass" class="flex justify-center">
    <slot name="loading">
      <BaseSpinner />
      <span class="sr-only">{{ resolvedLoadingText }}</span>
    </slot>
  </div>

  <div v-else-if="empty" :class="paddingClass" class="text-sm nv-text-subtle text-center">
    <slot name="empty">
      {{ resolvedEmptyText }}
    </slot>
  </div>

  <slot v-else />
</template>
