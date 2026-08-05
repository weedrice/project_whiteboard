<script setup lang="ts">
import { computed } from 'vue'
import { isAxiosError } from 'axios'
import { useQueryClient } from '@tanstack/vue-query'
import { CalendarClock, ExternalLink, FileEdit, Pencil, Trash2, XCircle } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { useConfirm } from '@/composables/useConfirm'
import { useUser, userQueryKeys } from '@/features/user/useUser'
import { usePost } from '@/features/board/posts/queries/usePost'
import { markDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'
import { API_ERROR_CODES } from '@/api/errorCodes'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { sessionQueryKey } from '@/queryAuthScope'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { formatDateTimeOrDash, withServerOffset } from '@/utils/date'
import { encodePathSegment } from '@/utils/urlPath'
import type { DraftPostPageResponse, DraftPostSummary } from '@/types'
import type { ScheduledPost } from '@/api/post'

const { t } = useI18n()
const queryClient = useQueryClient()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useMyDrafts, useMyScheduledPosts } = useUser()

function isDraftDeleteOutdatedError(error: unknown) {
  if (!isAxiosError(error) || error.response?.status !== 409) return false
  const data = error.response.data as { code?: string, error?: { code?: string } } | undefined
  return data?.code === API_ERROR_CODES.DRAFT_OUTDATED
    || data?.error?.code === API_ERROR_CODES.DRAFT_OUTDATED
}
const { useDeleteDraft, useCancelScheduledPost } = usePost()

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
  data: draftPage,
} = usePaginatedListState<DraftPostSummary, ReturnType<typeof useMyDrafts>>(
  useMyDrafts,
  { initialSize: 15, t },
)

const draftRetentionDays = computed(() => (
  (draftPage.value as DraftPostPageResponse | undefined)?.retentionDays
))
const maxDraftsPerUser = computed(() => (
  (draftPage.value as DraftPostPageResponse | undefined)?.maxDraftsPerUser
))

const {
  page: scheduledPage,
  size: scheduledSize,
  handlePageChange: handleScheduledPageChange,
  handleSizeChange: handleScheduledSizeChange,
  items: scheduledPosts,
  totalPages: scheduledTotalPages,
  isLoading: scheduledLoading,
  errorMessage: scheduledErrorMessage,
  refetch: refetchScheduledPosts,
} = usePaginatedListState<ScheduledPost>(useMyScheduledPosts, { initialSize: 10, t })

const { mutateAsync: deleteDraft, isPending: isDeletingDraft } = useDeleteDraft()
const { mutateAsync: cancelScheduledPost, isPending: isCancelingScheduledPost } = useCancelScheduledPost()

const listTitle = computed(() => t('user.tabs.drafts'))
const scheduledListTitle = computed(() => t('user.draftList.scheduledTitle'))

function getDraftTitle(draft: DraftPostSummary) {
  return draft.title?.trim() || t('user.draftList.untitled')
}

function getDraftTimestamp(draft: DraftPostSummary) {
  return draft.updatedAt ?? draft.modifiedAt
}

function getDraftExpiresAt(draft: DraftPostSummary) {
  const timestamp = getDraftTimestamp(draft)
  if (!timestamp || draftRetentionDays.value == null) return null
  const modifiedAt = new Date(withServerOffset(timestamp)).getTime()
  if (!Number.isFinite(modifiedAt)) return null
  return new Date(modifiedAt + draftRetentionDays.value * 24 * 60 * 60 * 1000).toISOString()
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
  const intent = captureAuthSessionIntent(authStore)
  const draftId = draft.draftId
  const userId = authStore.user?.userId
  if (!(await confirm(t('user.draftList.deleteConfirm')))) return
  if (!isAuthSessionIntentCurrent(authStore, intent)) return

  try {
    await deleteDraft({ draftId, version: draft.version })
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    if (userId != null) {
      if (!markDraftDeletedLocally(userId, draftId)) {
        void reportDraftOperationalEvent('tombstone_write_failed')
      }
    }
    toastStore.addToast(t('user.draftList.deleted'), 'success')
    queryClient.invalidateQueries({ queryKey: sessionQueryKey(intent.sessionGeneration, userQueryKeys.draftsRoot) })
    refetch()
  } catch (error: unknown) {
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    if (isDraftDeleteOutdatedError(error)) {
      toastStore.addToast(t('user.draftList.deleteOutdated'), 'warning')
      queryClient.invalidateQueries({ queryKey: sessionQueryKey(intent.sessionGeneration, userQueryKeys.draftsRoot) })
      refetch()
      return
    }
    toastStore.addToast(t('user.draftList.deleteFailed'), 'error')
  }
}

function getScheduledTitle(post: ScheduledPost) {
  return post.title?.trim() || t('user.draftList.untitled')
}

function getScheduledPostRoute(post: ScheduledPost) {
  if (!post.publishedPostId) return null
  return `/board/${encodePathSegment(post.boardUrl)}/post/${encodePathSegment(post.publishedPostId)}`
}

function getScheduledEditRoute(post: ScheduledPost) {
  return `/scheduled-posts/${encodePathSegment(post.scheduledPostId)}/edit`
}

function getScheduledStatus(post: ScheduledPost) {
  const variants: Record<ScheduledPost['status'], 'info' | 'warning' | 'success' | 'secondary' | 'danger'> = {
    SCHEDULED: 'info',
    PUBLISHING: 'warning',
    PUBLISHED: 'success',
    CANCELED: 'secondary',
    FAILED: 'danger',
  }
  return {
    label: t(`user.draftList.scheduledStatus.${post.status}`),
    variant: variants[post.status],
  }
}

async function handleCancelScheduledPost(post: ScheduledPost) {
  const intent = captureAuthSessionIntent(authStore)
  const scheduledPostId = post.scheduledPostId
  if (!(await confirm(t('user.draftList.cancelScheduledConfirm')))) return
  if (!isAuthSessionIntentCurrent(authStore, intent)) return

  try {
    await cancelScheduledPost(scheduledPostId)
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('user.draftList.cancelScheduledSuccess'), 'success')
    queryClient.invalidateQueries({
      queryKey: sessionQueryKey(intent.sessionGeneration, userQueryKeys.scheduledPostsRoot),
    })
    refetchScheduledPosts()
  } catch {
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('user.draftList.cancelScheduledFailed'), 'error')
  }
}

function getScheduledFailureMessage(failureReason: string | null | undefined) {
  if (!failureReason) return null
  if (failureReason === 'PUBLISH_BUSINESS_REJECTED') {
    return t('user.draftList.scheduledFailure.businessRejected')
  }
  if (failureReason === 'PUBLISH_INTERNAL_ERROR') {
    return t('user.draftList.scheduledFailure.internalError')
  }
  return t('user.draftList.scheduledFailure.unknown')
}
</script>

<template>
  <div class="space-y-6">
    <p
      v-if="draftRetentionDays != null && maxDraftsPerUser != null"
      class="rounded-lg border border-[var(--nv-line)] bg-[var(--nv-surface-2)] px-4 py-3 text-sm nv-text-subtle"
    >
      {{ $t('user.draftList.retentionNotice', {
        days: draftRetentionDays,
        max: maxDraftsPerUser,
      }) }}
    </p>
    <PaginatedListCard
      title-tag="h1"
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
    <ul class="divide-y divide-[var(--nv-line)]">
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
            <span v-if="getDraftExpiresAt(draft)">
              {{ $t('user.draftList.expiresAt', { date: formatDateTimeOrDash(getDraftExpiresAt(draft)) }) }}
            </span>
            <span v-if="draft.originalPostId != null">{{ $t('user.draftList.editDraft') }}</span>
          </div>
        </div>

        <div class="flex flex-shrink-0 items-center gap-2">
          <RouterLink
            :to="getDraftRoute(draft)"
            class="inline-flex min-h-11 items-center justify-center rounded-md border border-[var(--nv-line)] px-3 text-xs font-medium nv-text hover:bg-[var(--nv-surface-2)]"
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

    <PaginatedListCard
      title-tag="h2"
      :title="scheduledListTitle"
      :icon="CalendarClock"
      :items-count="scheduledPosts.length"
      :loading="scheduledLoading"
      :error="scheduledErrorMessage || null"
      :empty-title="$t('user.draftList.scheduledEmpty')"
      :page="scheduledPage"
      :size="scheduledSize"
      :total-pages="scheduledTotalPages"
      max-width-class="max-w-7xl"
      loading-preset="compact-status-list"
      @retry="refetchScheduledPosts"
      @page-change="handleScheduledPageChange"
      @size-change="handleScheduledSizeChange"
    >
      <ul class="divide-y divide-[var(--nv-line)]">
        <li
          v-for="post in scheduledPosts"
          :key="post.scheduledPostId"
          class="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6"
        >
          <div class="min-w-0">
            <component
              :is="getScheduledPostRoute(post) ? RouterLink : 'span'"
              :to="getScheduledPostRoute(post) || undefined"
              class="block truncate text-sm font-semibold nv-title"
              :class="getScheduledPostRoute(post) ? 'hover:text-[var(--nv-accent)]' : ''"
            >
              {{ getScheduledTitle(post) }}
            </component>
            <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs nv-text-subtle">
              <span>{{ post.boardName || post.boardUrl }}</span>
              <span>{{ formatDateTimeOrDash(post.scheduledAt) }}</span>
              <BaseBadge :variant="getScheduledStatus(post).variant" size="sm">
                {{ getScheduledStatus(post).label }}
              </BaseBadge>
              <span v-if="getScheduledFailureMessage(post.failureReason)" class="text-[var(--nv-danger-text)]">
                {{ getScheduledFailureMessage(post.failureReason) }}
              </span>
            </div>
          </div>

          <div class="flex flex-shrink-0 items-center gap-2">
            <RouterLink
              v-if="post.status === 'SCHEDULED' || post.status === 'FAILED'"
              :to="getScheduledEditRoute(post)"
              class="inline-flex min-h-11 items-center justify-center rounded-md border border-[var(--nv-line)] px-3 text-xs font-medium nv-text hover:bg-[var(--nv-surface-2)]"
            >
              <Pencil class="mr-1.5 h-3.5 w-3.5" />
              {{ $t('user.draftList.editScheduled') }}
            </RouterLink>
            <RouterLink
              v-if="getScheduledPostRoute(post)"
              :to="getScheduledPostRoute(post)!"
              class="inline-flex min-h-11 items-center justify-center rounded-md border border-[var(--nv-line)] px-3 text-xs font-medium nv-text hover:bg-[var(--nv-surface-2)]"
            >
              <ExternalLink class="mr-1.5 h-3.5 w-3.5" />
              {{ $t('user.draftList.openPublished') }}
            </RouterLink>
            <BaseButton
              v-if="post.status === 'SCHEDULED' || post.status === 'FAILED'"
              type="button"
              variant="danger"
              size="sm"
              :disabled="isCancelingScheduledPost"
              @click="handleCancelScheduledPost(post)"
            >
              <XCircle class="mr-1.5 h-3.5 w-3.5" />
              {{ $t('common.cancel') }}
            </BaseButton>
          </div>
        </li>
      </ul>
    </PaginatedListCard>
  </div>
</template>
