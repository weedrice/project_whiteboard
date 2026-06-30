<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminModalContentState from '@/components/admin/AdminModalContentState.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'

const props = withDefaults(defineProps<{
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
  mobileFitContent?: boolean
  closeAriaLabel?: string
  closeButtonClass?: string
  footerAlign?: 'start' | 'center' | 'end' | 'between'
  bodyClass?: string
  contentClass?: string
  paddingClass?: string
}>(), {
  empty: false,
  loading: false,
  error: undefined,
  emptyText: '',
  loadingText: undefined,
  errorText: undefined,
  size: 'md',
  mobileFull: false,
  mobileFitContent: false,
  closeAriaLabel: '',
  closeButtonClass: '',
  footerAlign: 'end',
  bodyClass: '',
  contentClass: 'space-y-6',
  paddingClass: 'py-10',
})

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()

const resolvedLoadingText = computed(() => props.loadingText ?? t('common.loading'))
const resolvedErrorText = computed(() => props.errorText ?? t('common.messages.loadFailed'))
</script>

<template>
  <BaseModal
    :is-open="isOpen"
    :title="title"
    :size="size"
    :mobile-full="mobileFull"
    :mobile-fit-content="mobileFitContent"
    :close-aria-label="closeAriaLabel"
    :close-button-class="closeButtonClass"
    :footer-align="footerAlign"
    :body-class="bodyClass"
    @close="emit('close')"
  >
    <AdminModalContentState
      :loading="loading"
      :error="error"
      :empty="empty"
      :loading-text="resolvedLoadingText"
      :error-text="resolvedErrorText"
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
