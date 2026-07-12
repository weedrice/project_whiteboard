<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminDetailModalShell from '@/components/admin/AdminDetailModalShell.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import PostContentView from '@/components/board/PostContentView.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import CommentList from '@/components/comment/CommentList.vue'
import type { AdminInquiryDetail } from '@/features/admin/inquiries/useAdminInquiryPosts'

defineProps<{
  isOpen: boolean
  inquiry: AdminInquiryDetail | null | undefined
  loading: boolean
  fetching: boolean
  error: unknown
}>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()
</script>

<template>
  <AdminDetailModalShell
    :is-open="isOpen"
    :title="t('admin.inquiries.detail.title')"
    size="2xl"
    mobile-full
    :loading="loading || fetching"
    :error="error"
    :empty="!inquiry"
    empty-text=""
    :error-text="t('common.messages.loadFailed')"
    content-class="space-y-4 p-2 sm:p-4"
    @close="emit('close')"
  >
    <template #loading>
      <div class="flex items-center justify-center py-10">
        <BaseSpinner size="lg" aria-hidden="true" />
      </div>
    </template>

    <template #error>
      <div
        class="rounded border border-[var(--nv-danger-border)] bg-[var(--nv-danger-bg)] px-4 py-3 text-sm text-[var(--nv-danger-text)]"
      >
        {{ t('common.messages.loadFailed') }}
      </div>
    </template>

    <template v-if="inquiry">
      <div class="space-y-2 border-b nv-border pb-3">
        <h2 class="text-lg font-semibold nv-title">{{ inquiry.title }}</h2>
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs nv-text-subtle">
          <span>{{ t('common.author') }} {{ inquiry.authorName }}</span>
          <span>{{ t('common.createdAt') }} {{ inquiry.createdAtText }}</span>
        </div>
      </div>

      <div class="max-h-[60vh] overflow-y-auto rounded-md nv-surface-muted p-4 text-sm nv-text-muted">
        <PostContentView
          v-if="inquiry.contents"
          class="break-words leading-6"
          :content="inquiry.contents"
          :sandbox-title="inquiry.title"
        />
        <div v-else>-</div>
      </div>

      <div class="border-t nv-border pt-4">
        <CommentList :postId="inquiry.id" boardUrl="inquiry" />
      </div>
    </template>

    <template #footer>
      <AdminModalActions>
        <BaseButton type="button" variant="secondary" @click="emit('close')">{{ t('common.close') }}</BaseButton>
      </AdminModalActions>
    </template>
  </AdminDetailModalShell>
</template>
