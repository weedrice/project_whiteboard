<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  empty?: boolean
  error?: boolean
  loadingText?: string
  emptyText?: string
  paddingClass?: string
  actionLabel?: string
  errorText?: string
  retryLabel?: string
}>(), {
  loading: false,
  empty: false,
  error: false,
  loadingText: undefined,
  emptyText: undefined,
  paddingClass: 'py-8',
  errorText: undefined,
  retryLabel: undefined,
})

const { t } = useI18n()

const resolvedLoadingText = computed(() => props.loadingText ?? t('common.loading'))
const resolvedEmptyText = computed(() => props.emptyText ?? t('common.noData'))
const resolvedErrorText = computed(() => props.errorText ?? t('common.messages.loadFailed'))
const resolvedRetryLabel = computed(() => props.retryLabel ?? t('common.error.retry'))

defineEmits<{ action: []; retry: [] }>()
</script>

<template>
  <div v-if="loading" :class="paddingClass" class="flex justify-center">
    <slot name="loading">
      <BaseSpinner />
      <span class="sr-only">{{ resolvedLoadingText }}</span>
    </slot>
  </div>

  <div v-else-if="error" :class="paddingClass" role="alert">
    <div class="rounded-lg border border-[var(--nv-danger-border)] bg-[var(--nv-danger-bg)] px-4 py-3 text-sm text-[var(--nv-danger-text)]">
      <p>{{ resolvedErrorText }}</p>
      <BaseButton type="button" variant="secondary" size="sm" class="mt-3" @click="$emit('retry')">
        {{ resolvedRetryLabel }}
      </BaseButton>
    </div>
  </div>

  <div v-else-if="empty" :class="paddingClass">
    <slot name="empty">
      <EmptyState title-tag="h2" :title="resolvedEmptyText" :action-label="actionLabel" container-class="py-4" @action="$emit('action')">
        <template v-if="$slots['empty-action']" #action>
          <slot name="empty-action" />
        </template>
      </EmptyState>
    </slot>
  </div>

  <slot v-else />
</template>
