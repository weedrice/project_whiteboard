<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  error?: unknown
  empty?: boolean
  loadingText?: string
  errorText?: string
  emptyText?: string
  paddingClass?: string
}>(), {
  loading: false,
  error: undefined,
  empty: false,
  loadingText: undefined,
  errorText: undefined,
  emptyText: undefined,
  paddingClass: 'py-10',
})

const { t } = useI18n()

const resolvedLoadingText = computed(() => props.loadingText ?? t('common.loading'))
const resolvedErrorText = computed(() => props.errorText ?? t('common.messages.loadFailed'))
const resolvedEmptyText = computed(() => props.emptyText ?? t('common.noData'))
</script>

<template>
  <div v-if="loading" :class="paddingClass" role="status" aria-live="polite" aria-busy="true">
    <slot name="loading">
      <div class="flex flex-col items-center justify-center gap-2 text-sm nv-text-subtle">
        <BaseSpinner size="lg" aria-hidden="true" />
        <span v-if="resolvedLoadingText">{{ resolvedLoadingText }}</span>
      </div>
    </slot>
  </div>

  <div v-else-if="error" :class="paddingClass" role="alert" aria-live="assertive">
    <slot name="error">
      <div class="rounded nv-status-danger px-4 py-3 text-sm">
        {{ resolvedErrorText }}
      </div>
    </slot>
  </div>

  <div v-else-if="empty" :class="paddingClass" role="status" aria-live="polite">
    <slot name="empty">
      <p v-if="resolvedEmptyText" class="text-center text-sm nv-text-subtle">{{ resolvedEmptyText }}</p>
    </slot>
  </div>

  <slot v-else />
</template>
