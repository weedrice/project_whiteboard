<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ThumbsUp } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { RouteLocationRaw } from 'vue-router'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
import PostListTitleContent from '@/components/board/PostListTitleContent.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import type { PostSummary } from '@/types'
import { formatRelativeDate } from '@/utils/date'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
import {
  getPostListBoardNameLabel,
  getPostListCountLabel,
  getPostListPreviewImageUrl,
  getPostListRowNumberLabel,
} from '@/components/board/postListModel'

type TitleTag = 'button' | 'router-link' | 'span'

const props = defineProps<{
  posts: PostSummary[]
  loading: boolean
  columns: TableColumn[]
  activeSortKey: string | null
  activeSortDirection: 'asc' | 'desc' | null
  showNoticeBadge: boolean
  showCommentCount: boolean
  showPreviewIndicator: boolean
  showSecretIndicator: boolean
  density: 'default' | 'compact'
  maxAuthorNameLength: number
  getRowClass: (item: PostSummary) => string
  shouldInterceptInquiry: (item: PostSummary) => boolean
  hasBoardRouteTarget: (item: PostSummary) => boolean
  getBoardLinkTarget: (item: PostSummary) => RouteLocationRaw
  getTitleTag: (item: PostSummary) => TitleTag
  getTitleProps: (item: PostSummary) => Record<string, unknown>
  shouldShowInquiryStatus: (item: PostSummary) => boolean
  hasInteractiveAuthor: (item: PostSummary) => boolean
  getAuthorName: (item: PostSummary) => string
  getVisibleAuthorName: (item: PostSummary) => string
  isAgentAuthor: (item: PostSummary) => boolean
  onNavigationClick: (event: Event, item: PostSummary) => void
  caption?: string
}>()

const emit = defineEmits<{
  (e: 'sort', sort: string): void
}>()

const { t } = useI18n()

const PREVIEW_WIDTH = 192
const PREVIEW_HEIGHT = 128
const PREVIEW_GAP = 8
const VIEWPORT_MARGIN = 8

const hoverPreview = ref<{
  url: string
  left: number
  top: number
} | null>(null)
let previewController: AbortController | null = null
let previewGeneration = 0
const previewObjectUrls = new Map<string, string>()

function showImagePreview(event: Event, item: PostSummary) {
  const url = getPostListPreviewImageUrl(item)
  const target = event.currentTarget
  hideImagePreview()
  if (!url || !(target instanceof HTMLElement) || typeof window === 'undefined') {
    return
  }

  const rect = target.getBoundingClientRect()
  const viewportWidth = document.documentElement.clientWidth || window.innerWidth
  const viewportHeight = document.documentElement.clientHeight || window.innerHeight
  const maxLeft = Math.max(VIEWPORT_MARGIN, viewportWidth - PREVIEW_WIDTH - VIEWPORT_MARGIN)
  const left = Math.min(Math.max(rect.left + 24, VIEWPORT_MARGIN), maxLeft)
  const belowTop = rect.bottom + PREVIEW_GAP
  const top = belowTop + PREVIEW_HEIGHT <= viewportHeight - VIEWPORT_MARGIN
    ? belowTop
    : Math.max(VIEWPORT_MARGIN, rect.top - PREVIEW_HEIGHT - PREVIEW_GAP)

  const requestPath = resolveAuthenticatedFileRequestPath(url)
  if (!requestPath) {
    hoverPreview.value = { url, left, top }
    return
  }

  const cachedObjectUrl = previewObjectUrls.get(requestPath)
  if (cachedObjectUrl) {
    hoverPreview.value = { url: cachedObjectUrl, left, top }
    return
  }

  const generation = previewGeneration
  const controller = new AbortController()
  previewController = controller
  void loadAuthenticatedPreview(requestPath, left, top, controller, generation)
}

function hideImagePreview() {
  previewGeneration += 1
  previewController?.abort()
  previewController = null
  hoverPreview.value = null
}

function releaseImagePreviewResources() {
  hideImagePreview()
  previewObjectUrls.forEach((objectUrl) => URL.revokeObjectURL(objectUrl))
  previewObjectUrls.clear()
}

async function loadAuthenticatedPreview(
  requestPath: string,
  left: number,
  top: number,
  controller: AbortController,
  generation: number,
) {
  try {
    const { default: api } = await import('@/api')
    const response = await api.get<Blob>(requestPath, {
      responseType: 'blob',
      signal: controller.signal,
      skipGlobalErrorHandler: true,
    })
    if (controller.signal.aborted || generation !== previewGeneration) return

    const objectUrl = URL.createObjectURL(response.data)
    previewObjectUrls.set(requestPath, objectUrl)
    hoverPreview.value = { url: objectUrl, left, top }
  } catch {
    if (!controller.signal.aborted && generation === previewGeneration) hoverPreview.value = null
  } finally {
    if (previewController === controller) previewController = null
  }
}

onMounted(() => {
  window.addEventListener('resize', hideImagePreview)
  window.addEventListener('scroll', hideImagePreview, true)
})

watch(() => props.posts, releaseImagePreviewResources)

const stopSessionBoundary = subscribeAuthSessionBoundary(releaseImagePreviewResources)

onBeforeUnmount(() => {
  stopSessionBoundary()
  window.removeEventListener('resize', hideImagePreview)
  window.removeEventListener('scroll', hideImagePreview, true)
  releaseImagePreviewResources()
})
</script>

<template>
  <div class="hidden sm:block table-container">
    <BaseTable
      :columns="columns"
      :items="posts"
      :loading="loading"
      :caption="caption"
      :emptyText="t('board.list.noPosts')"
      :density="density"
      :current-sort-key="activeSortKey"
      :current-sort-direction="activeSortDirection"
      :rowClass="getRowClass"
      @sort="emit('sort', $event)"
    >
      <template #cell-postId="{ item }">
        <span v-if="showNoticeBadge && item.isNotice" class="nv-post-table-emphasis">
          {{ getPostListRowNumberLabel(item, t('common.notice'), showNoticeBadge) }}
        </span>
        <span v-else>{{ getPostListRowNumberLabel(item, t('common.notice'), showNoticeBadge) }}</span>
      </template>

      <template #cell-boardName="{ item }">
        <button
          v-if="shouldInterceptInquiry(item)"
          type="button"
          class="nv-post-board-link"
          @click="onNavigationClick($event, item)"
        >
          {{ getPostListBoardNameLabel(item) }}
        </button>
        <router-link
          v-else-if="hasBoardRouteTarget(item)"
          :to="getBoardLinkTarget(item)"
          class="nv-post-board-link"
          @click="onNavigationClick($event, item)"
        >
          {{ getPostListBoardNameLabel(item) }}
        </router-link>
        <span v-else class="truncate text-[var(--nv-muted)]">{{ getPostListBoardNameLabel(item) }}</span>
      </template>

      <template #cell-title="{ item }">
        <div
          class="nv-post-title-cell flex min-w-0 items-center gap-1.5"
          @mouseenter="showImagePreview($event, item)"
          @mouseleave="hideImagePreview"
          @focusin="showImagePreview($event, item)"
          @focusout="hideImagePreview"
        >
          <component
            :is="getTitleTag(item)"
            v-bind="getTitleProps(item)"
            class="nv-post-title-link"
            :class="{ 'text-[var(--nv-muted)]': getTitleTag(item) === 'span' }"
            @click="onNavigationClick($event, item)"
          >
            <PostListTitleContent
              :post="item"
              :show-inquiry-status="shouldShowInquiryStatus(item)"
              :show-notice-badge="showNoticeBadge"
              :show-comment-count="showCommentCount"
              :show-preview-indicator="showPreviewIndicator"
              :show-secret-indicator="showSecretIndicator"
              truncateTitle
            />
          </component>
        </div>
      </template>

      <template #cell-author="{ item }">
        <span class="nv-post-author-cell">
          <UserMenu
            v-if="hasInteractiveAuthor(item)"
            class="nv-post-author-menu"
            :user-id="item.author.userId"
            :display-name="item.author.displayName"
            :max-label-length="maxAuthorNameLength"
            size="inherit"
          />
          <span
            v-else
            class="nv-post-author-fallback"
            :title="getAuthorName(item)"
          >
            {{ getVisibleAuthorName(item) }}
          </span>
          <span
            v-if="isAgentAuthor(item)"
            class="nv-post-badge nv-post-badge-agent"
          >
            AGENT
          </span>
        </span>
      </template>

      <template #cell-likeCount="{ item }">
        <span class="nv-post-count-cell justify-center">
          <ThumbsUp class="h-3.5 w-3.5 flex-shrink-0 text-[var(--nv-muted)]" />
          <span>{{ getPostListCountLabel(item.likeCount) }}</span>
        </span>
      </template>

      <template #cell-viewCount="{ item }">
        <span class="nv-post-count-cell justify-end">
          <span>{{ getPostListCountLabel(item.viewCount) }}</span>
        </span>
      </template>

      <template #cell-createdAt="{ item }">{{ formatRelativeDate(item.createdAt) }}</template>
    </BaseTable>

    <Teleport to="body">
      <div
        v-if="hoverPreview"
        class="nv-post-hover-preview"
        :style="{
          left: `${hoverPreview.left}px`,
          top: `${hoverPreview.top}px`,
          width: `${PREVIEW_WIDTH}px`,
          height: `${PREVIEW_HEIGHT}px`,
        }"
        aria-hidden="true"
      >
        <img
          :src="hoverPreview.url"
          alt=""
          decoding="async"
          @error="hideImagePreview"
        />
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.nv-post-badge {
  align-items: center;
  border-radius: 9999px;
  display: inline-flex;
  font-size: 0.62rem;
  font-weight: 700;
  justify-content: center;
  letter-spacing: 0.02em;
  min-height: 1.35rem;
  padding: 0.15rem 0.55rem;
}

.nv-post-badge-agent {
  background: var(--nv-info-bg);
  border: 1px solid var(--nv-info-border);
  color: var(--nv-info-text);
}

.nv-post-table-emphasis {
  color: var(--nv-danger-text);
  font-weight: 700;
}

.nv-post-count-cell {
  align-items: center;
  display: inline-flex;
  gap: 0.25rem;
  line-height: 1;
  min-height: 1.25rem;
  width: 100%;
}

.nv-post-board-link,
.nv-post-title-link {
  color: inherit;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  transition: color 0.15s ease;
}

.nv-post-title-cell {
  overflow: hidden;
}

.nv-post-hover-preview {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 0.75rem;
  box-shadow: 0 12px 32px color-mix(in srgb, var(--nv-ink) 22%, transparent);
  overflow: hidden;
  padding: 0.25rem;
  pointer-events: none;
  position: fixed;
  z-index: 1000;
}

.nv-post-hover-preview img {
  border-radius: 0.5rem;
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.nv-post-board-link:hover,
.nv-post-title-link:hover {
  color: var(--nv-accent);
}

.nv-post-title-link:visited {
  color: color-mix(in srgb, var(--nv-ink-soft) 72%, var(--nv-muted));
}

.nv-post-title-link {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  gap: 0.375rem;
}

.nv-post-author-cell {
  align-items: center;
  display: inline-flex;
  gap: 0.35rem;
  justify-content: flex-start;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.nv-post-author-menu {
  display: inline-flex;
  max-width: 16ch;
  min-width: 0;
}

.nv-post-author-fallback {
  display: inline-block;
  max-width: 16ch;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.table-container > div) {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

:deep(thead) {
  background: color-mix(in srgb, var(--nv-surface-2) 65%, transparent);
}

:deep(th) {
  color: var(--nv-muted);
  font-family: var(--font-mono);
  font-size: var(--nv-type-kicker);
  font-weight: 700;
  letter-spacing: 0.14em;
}

:deep(.post-list-row td) {
  border-bottom: 1px solid var(--nv-line-soft);
  transition: background-color 0.15s ease, padding-left 0.15s ease, border-color 0.15s ease;
}

:deep(.post-list-row:last-child td) {
  border-bottom: 0;
}

:deep(.post-list-row td:first-child) {
  border-left: 3px solid transparent;
  padding-left: calc(1.5rem - 3px);
}

:deep(.post-list-row:hover td:first-child),
:deep(.post-list-row-current td:first-child) {
  border-left-color: var(--nv-accent);
  padding-left: 1.5rem;
}

:deep(.post-list-row:hover td) {
  background: color-mix(in srgb, var(--nv-surface-2) 70%, transparent);
}

:deep(.post-list-row-current td) {
  background: color-mix(in srgb, var(--nv-accent-bg) 82%, transparent);
}

:deep(.post-list-row-current td:first-child) {
  border-left-width: 4px;
}
</style>
