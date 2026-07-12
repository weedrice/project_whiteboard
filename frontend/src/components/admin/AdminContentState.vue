<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  empty?: boolean
  loadingText?: string
  emptyText?: string
  paddingClass?: string
  actionLabel?: string
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

defineEmits<{ action: [] }>()
</script>

<template>
  <div v-if="loading" :class="paddingClass" class="flex justify-center">
    <slot name="loading">
      <BaseSpinner />
      <span class="sr-only">{{ resolvedLoadingText }}</span>
    </slot>
  </div>

  <div v-else-if="empty" :class="paddingClass">
    <slot name="empty">
      <EmptyState :title="resolvedEmptyText" :action-label="actionLabel" container-class="py-4" @action="$emit('action')">
        <template v-if="$slots['empty-action']" #action>
          <slot name="empty-action" />
        </template>
      </EmptyState>
    </slot>
  </div>

  <slot v-else />
</template>
