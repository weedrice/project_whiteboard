<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminInlinePager from '@/components/admin/AdminInlinePager.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import type { AdminUserCommentViewItem } from '@/features/admin/users/useAdminUserDetailTabs'
import type { SanitizedHtml } from '@/utils/sanitize'

interface PageNavigationState {
  number: number
  totalPages: number
}

defineProps<{
  items: AdminUserCommentViewItem[]
  loading: boolean
  pageData?: PageNavigationState | null
  renderCommentContent: (content: string | null | undefined) => SanitizedHtml
  isCommentEmoticonOnly: (content: string | null | undefined) => boolean
}>()

defineEmits<{
  previous: []
  next: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div v-if="loading" class="py-6 text-center text-sm nv-text-subtle">{{ t('common.loading') }}</div>
    <div v-else-if="!items.length" class="py-6 text-center text-sm nv-text-subtle">
      {{ t('admin.users.detail.commentsEmpty') }}
    </div>
    <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
      <div v-for="comment in items" :key="comment.commentId" class="rounded-lg border nv-border p-3">
        <div class="mb-2 flex flex-wrap gap-1">
          <AdminStatusBadge v-for="badge in comment.badges" :key="badge.label" :label="badge.label" :variant="badge.variant" />
        </div>
        <div class="comment-content-list">
          <SanitizedHtmlView
            v-if="isCommentEmoticonOnly(comment.content)"
            tag="p"
            :html="renderCommentContent(comment.content)"
            class="text-sm"
          />
          <SanitizedHtmlView
            v-else
            tag="p"
            :html="renderCommentContent(comment.content)"
            class="line-clamp-2 break-words text-sm nv-text"
          />
        </div>
        <div class="mt-1 text-xs nv-text-subtle">
          {{ comment.metaText }}
        </div>
        <div class="mt-1 text-xs nv-text-subtle">
          {{ comment.statsText }}
        </div>
      </div>
    </div>
    <AdminInlinePager
      v-if="pageData"
      :page="pageData.number"
      :total-pages="pageData.totalPages"
      @previous="$emit('previous')"
      @next="$emit('next')"
    />
  </div>
</template>
