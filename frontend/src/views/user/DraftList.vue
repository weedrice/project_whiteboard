<script setup lang="ts">
import { computed } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { FileEdit, Pencil, Trash2 } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { useUser, userQueryKeys } from '@/composables/useUser'
import { usePost } from '@/features/board/posts/queries/usePost'
import { useToastStore } from '@/stores/toast'
import { formatDateTimeOrDash } from '@/utils/date'
import { encodePathSegment } from '@/utils/urlPath'
import type { DraftPostSummary } from '@/types'

const { t } = useI18n()
const queryClient = useQueryClient()
const toastStore = useToastStore()
const { useMyDrafts } = useUser()
const { useDeleteDraft } = usePost()

const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: drafts,
  totalPages,
  isLoading: loading,
  errorMessage,
  refetch,
} = usePaginatedListState<DraftPostSummary>(useMyDrafts, { initialSize: 15, t })

const { mutateAsync: deleteDraft, isPending: isDeletingDraft } = useDeleteDraft()

const listTitle = computed(() => t('user.tabs.drafts'))

function getDraftTitle(draft: DraftPostSummary) {
  return draft.title?.trim() || t('user.draftList.untitled')
}

function getDraftTimestamp(draft: DraftPostSummary) {
  return draft.updatedAt ?? draft.modifiedAt
}

function getDraftRoute(draft: DraftPostSummary) {
  const boardUrl = encodePathSegment(draft.boardUrl)
  const query = { draftId: String(draft.draftId) }
  if (draft.originalPostId != null) {
    return {
      path: `/board/${boardUrl}/post/${encodePathSegment(draft.originalPostId)}/edit`,
      query,
    }
  }
  return {
    path: `/board/${boardUrl}/write`,
    query,
  }
}

async function handleDeleteDraft(draft: DraftPostSummary) {
  if (!window.confirm(t('user.draftList.deleteConfirm'))) return

  try {
    await deleteDraft(draft.draftId)
    toastStore.addToast(t('user.draftList.deleted'), 'success')
    queryClient.invalidateQueries({ queryKey: userQueryKeys.draftsRoot })
    refetch()
  } catch {
    toastStore.addToast(t('user.draftList.deleteFailed'), 'error')
  }
}
</script>

<template>
  <PaginatedListCard
    :title="listTitle"
    :icon="FileEdit"
    :items-count="drafts.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.draftList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    loading-preset="compact-status-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <ul class="divide-y divide-[var(--nv-border)]">
      <li
        v-for="draft in drafts"
        :key="draft.draftId"
        class="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6"
      >
        <div class="min-w-0">
          <RouterLink
            :to="getDraftRoute(draft)"
            class="block truncate text-sm font-semibold nv-title hover:text-[var(--nv-accent)]"
          >
            {{ getDraftTitle(draft) }}
          </RouterLink>
          <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs nv-text-subtle">
            <span>{{ draft.boardName || draft.boardUrl }}</span>
            <span>{{ formatDateTimeOrDash(getDraftTimestamp(draft)) }}</span>
            <span v-if="draft.originalPostId != null">{{ $t('user.draftList.editDraft') }}</span>
          </div>
        </div>

        <div class="flex flex-shrink-0 items-center gap-2">
          <RouterLink
            :to="getDraftRoute(draft)"
            class="inline-flex min-h-[36px] items-center justify-center rounded-md border border-[var(--nv-border)] px-3 text-xs font-medium nv-text hover:bg-[var(--nv-surface-2)]"
          >
            <Pencil class="mr-1.5 h-3.5 w-3.5" />
            {{ $t('user.draftList.continue') }}
          </RouterLink>
          <BaseButton
            type="button"
            variant="danger"
            size="sm"
            :disabled="isDeletingDraft"
            @click="handleDeleteDraft(draft)"
          >
            <Trash2 class="mr-1.5 h-3.5 w-3.5" />
            {{ $t('common.delete') }}
          </BaseButton>
        </div>
      </li>
    </ul>
  </PaginatedListCard>
</template>
