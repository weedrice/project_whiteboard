<script setup lang="ts">
import AdminModalContentState from '@/components/admin/AdminModalContentState.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'

withDefaults(defineProps<{
  isOpen: boolean
  title: string
  empty?: boolean
  loading?: boolean
  error?: unknown
  emptyText?: string
  loadingText?: string
  errorText?: string
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
  bodyClass?: string
  contentClass?: string
  paddingClass?: string
}>(), {
  empty: false,
  loading: false,
  error: undefined,
  emptyText: '',
  loadingText: 'Loading...',
  errorText: 'Failed to load data.',
  size: 'md',
  mobileFull: false,
  bodyClass: '',
  contentClass: 'space-y-6',
  paddingClass: 'py-10',
})

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <BaseModal
    :is-open="isOpen"
    :title="title"
    :size="size"
    :mobile-full="mobileFull"
    :body-class="bodyClass"
    @close="emit('close')"
  >
    <AdminModalContentState
      :loading="loading"
      :error="error"
      :empty="empty"
      :loading-text="loadingText"
      :error-text="errorText"
      :empty-text="emptyText"
      :padding-class="paddingClass"
    >
      <template v-if="$slots.loading" #loading>
        <slot name="loading" />
      </template>
      <template v-if="$slots.error" #error>
        <slot name="error" />
      </template>
      <template v-if="$slots.empty" #empty>
        <slot name="empty" />
      </template>

      <div :class="contentClass">
        <slot />
      </div>
    </AdminModalContentState>

    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </BaseModal>
</template>
