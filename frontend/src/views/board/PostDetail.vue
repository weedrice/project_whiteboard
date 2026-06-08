<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  Bookmark,
  Clock,
  Copy,
  Eye,
  List,
  MessageSquare,
  Pencil,
  Share2,
  ThumbsUp,
  Trash2,
  User
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostDetailSkeleton from '@/components/board/PostDetailSkeleton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { usePost } from '@/composables/usePost'
import { usePostDetailPermissions } from '@/composables/usePostDetailPermissions'
import { usePostDetailActions } from '@/composables/usePostDetailActions'
import { usePostDetailShare } from '@/composables/usePostDetailShare'
import { usePostDetailNavigation } from '@/composables/usePostDetailNavigation'
import { usePostDetailScrollEffects } from '@/composables/usePostDetailScrollEffects'
import { usePostDetailSeo } from '@/composables/usePostDetailSeo'
import { usePostDetailUiEffects } from '@/composables/usePostDetailUiEffects'
import { usePostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { useAuthStore } from '@/stores/auth'
import { formatDate } from '@/utils/date'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { applyImageFallback } from '@/utils/imageFallback'
import { renderPostContentHtml } from '@/utils/postContentHtml'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const {
  usePostDetail,
} = usePost()

const postId = computed(() => route.params.postId as string)
const {
  data: post,
  isLoading,
  error: postError
} = usePostDetail(postId, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})
const postView = usePostDetailViewModel(post)

usePostDetailSeo({
  route,
  post,
  postView,
  t,
})

const error = computed(() => {
  if (!postError.value) return ''
  if (isRestrictedResourceError(postError.value)) {
    return '접근 권한이 없는 게시글입니다.'
  }
  return t('board.postDetail.loadFailed')
})

const currentUserId = computed(() => authStore.user?.userId)
const isAuthenticated = computed(() => authStore.isAuthenticated)
const authIsAdmin = computed(() => authStore.isAdmin)
const {
  isAgentAuthor,
  canEdit,
  canDelete,
  canReport
} = usePostDetailPermissions(post, {
  currentUserId,
  isAuthenticated,
  isAdmin: authIsAdmin
})

const processedContents = computed(() => {
  return renderPostContentHtml(post.value?.contents)
})

const {
  isBlurred,
  timeLeft,
  showComposerCta,
  markPostDetailUiMounted,
  isPostDetailUiDisposed,
  startBlurTimer,
  clearBlurTimer,
  revealSpoiler,
  scheduleComposerFocus,
  trackImageLoadTimeout,
  setupComposerObserver,
  disposePostDetailUiEffects
} = usePostDetailUiEffects()

const {
  currentUrl,
  compactUrl,
  showCopyHint,
  handleCopyUrl,
  onUrlBarClick,
  handleShare
} = usePostDetailShare({
  route,
  post,
  t,
})

const {
  buildBoardListRoute,
  handleTagClick,
  syncBoardListPageForDirectEntry,
  buildEditRoute,
  goToList,
} = usePostDetailNavigation({
  route,
  router,
  post,
  postView,
})

const {
  isLikeAnimating,
  isBookmarkAnimating,
  showReportModal,
  reportReason,
  handleDelete,
  handleLike,
  handleBookmark,
  openReportModal,
  submitReport,
} = usePostDetailActions({
  post,
  canReport,
  authStore,
  route,
  router,
  t,
  buildBoardListRoute,
  closeOverflowMenu: () => {},
})

const {
  contentRef,
  commentsRef,
  scrollToTop,
  scrollToCommentComposer,
  scrollToComments,
} = usePostDetailScrollEffects({
  route,
  router,
  post,
  postView,
  authStore,
  canEdit,
  isReportModalOpen: showReportModal,
  isBlurred,
  timeLeft,
  startBlurTimer,
  clearBlurTimer,
  markPostDetailUiMounted,
  isPostDetailUiDisposed,
  scheduleComposerFocus,
  trackImageLoadTimeout,
  setupComposerObserver,
  disposePostDetailUiEffects,
  syncBoardListPageForDirectEntry,
  buildEditRoute,
  goToList,
  handleBookmark,
  handleShare,
  handleCopyUrl,
  handleLike,
})
</script>

<template>
  <div class="nv-post-shell">
    <BaseCard noPadding>
      <PostDetailSkeleton v-if="isLoading" />

      <div v-else-if="error" class="px-4 py-12 text-center text-sm nv-form-error sm:px-6">
        {{ error }}
        <div class="mt-4">
          <BaseButton @click="router.back()" variant="ghost" size="sm">
            {{ $t('common.back') }}
          </BaseButton>
        </div>
      </div>

      <template v-else-if="post && postView">
        <header class="nv-post-header px-4 py-4 sm:px-6 sm:py-5">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 flex-1 space-y-4">
              <div class="flex items-center justify-between gap-2">
                <BaseButton
                  @click="router.push(buildBoardListRoute(postView.boardUrl))"
                  variant="ghost"
                  size="sm"
                  class="nv-post-back-btn"
                >
                  <ArrowLeft class="h-4 w-4" />
                  <span class="hidden sm:inline">{{ $t('board.postDetail.toList') }}</span>
                </BaseButton>

                <div
                  v-if="canEdit || canDelete"
                  class="flex min-w-0 items-center justify-end gap-2"
                >
                  <router-link
                    v-if="canEdit"
                    :to="buildEditRoute()"
                    class="nv-post-header-action"
                    :aria-label="$t('common.edit')"
                  >
                    <Pencil class="h-4 w-4" />
                    <span>{{ $t('common.edit') }}</span>
                  </router-link>
                  <button
                    v-if="canDelete"
                    type="button"
                    class="nv-post-header-action is-danger"
                    :aria-label="$t('common.delete')"
                    @click="handleDelete"
                  >
                    <Trash2 class="h-4 w-4" />
                    <span>{{ $t('common.delete') }}</span>
                  </button>
                </div>
              </div>

              <div class="space-y-3">
                <div class="nv-post-meta-strip">
                  <span>{{ postView.boardName }}</span>
                  <span>&middot;</span>
                  <span>{{ $t('common.post') }}</span>
                </div>
                <h1 class="text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-4xl">
                  {{ postView.title }}
                </h1>
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                  <span class="inline-flex items-center gap-1.5">
                    <User class="h-4 w-4" />
                    <UserMenu :user-id="postView.authorUserId" :display-name="postView.authorDisplayName" size="inherit" />
                    <span
                      v-if="isAgentAuthor"
                      class="rounded-full nv-status-info px-1.5 py-0.5 text-[10px] font-semibold"
                    >
                      AGENT
                    </span>
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Clock class="h-4 w-4" />
                    {{ formatDate(postView.createdAt) }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <Eye class="h-4 w-4" />
                    {{ postView.viewCount }}
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <MessageSquare class="h-4 w-4" />
                    {{ postView.commentCount }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </header>

        <div class="px-4 pt-5 lg:px-6">
          <article class="nv-post-content-stack min-w-0">
            <div class="nv-post-content-top">
              <Transition name="fade">
                <span
                  v-if="showCopyHint"
                  class="nv-post-copy-hint"
                >
                  {{ $t('common.messages.urlCopied') }}
                </span>
              </Transition>

              <button
                type="button"
                class="nv-post-url-chip"
                :title="currentUrl"
                :aria-label="$t('common.copy')"
                @click="onUrlBarClick"
              >
                <Copy class="h-3.5 w-3.5 flex-shrink-0" />
                <span>{{ compactUrl }}</span>
              </button>
            </div>

            <div class="nv-post-article relative overflow-hidden">
              <div
                ref="contentRef"
                class="ql-editor nv-rich-content prose prose-sm max-w-none sm:prose-base dark:prose-invert"
                :class="{ 'blur-md select-none': isBlurred }"
                v-html="processedContents"
                @error.capture="applyImageFallback"
              ></div>

              <div v-if="isBlurred" class="nv-post-spoiler">
                <div class="nv-post-spoiler-card">
                  <h3 class="text-lg font-semibold text-[var(--nv-ink)]">
                    {{ $t('board.postDetail.spoilerWarning') }}
                  </h3>
                  <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
                    {{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}
                  </p>
                  <div class="nv-post-spoiler-actions">
                    <BaseButton @click="revealSpoiler" size="sm">
                      {{ $t('board.postDetail.revealSpoiler') }}
                    </BaseButton>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-if="postView.tags.length > 0"
              class="nv-post-tags"
            >
              <p class="nv-post-section-label">{{ $t('board.postDetail.tags') }}</p>
              <PostTags
                :modelValue="postView.tags"
                :readOnly="true"
                :boardUrl="postView.boardUrl"
                compact
                @tag-click="handleTagClick"
              />
            </div>

            <div class="hidden sm:block">
              <div class="nv-post-reaction-row">
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :class="{ 'is-active': postView.liked }"
                  :aria-label="$t('common.likes')"
                  :aria-pressed="postView.liked"
                  :title="$t('common.likes')"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleLike"
                >
                  <ThumbsUp class="h-5 w-5" :class="{ 'fill-current bounce-in': isLikeAnimating }" />
                  <span class="nv-post-action-count">{{ postView.likeCount }}</span>
                </button>
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :class="{ 'is-active is-bookmark': postView.scrapped }"
                  :aria-label="$t('board.postDetail.bookmark')"
                  :aria-pressed="postView.scrapped"
                  :title="$t('board.postDetail.bookmark')"
                  :disabled="!authStore.isAuthenticated"
                  @click="handleBookmark"
                >
                  <Bookmark class="h-5 w-5" :class="{ 'fill-current bounce-in': isBookmarkAnimating }" />
                </button>
                <button
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle"
                  :aria-label="$t('common.share')"
                  :title="$t('common.share')"
                  @click="handleShare"
                >
                  <Share2 class="h-5 w-5" />
                </button>
                <button
                  v-if="canReport"
                  type="button"
                  class="nv-post-action-btn nv-post-action-btn-circle is-report"
                  :aria-label="$t('common.report')"
                  :title="$t('common.report')"
                  @click="openReportModal"
                >
                  <AlertTriangle class="h-5 w-5" />
                </button>
              </div>
            </div>

            <section id="comments" ref="commentsRef" class="nv-post-comments">
              <CommentList :postId="postView.postId" :boardUrl="postView.boardUrl" />
            </section>
          </article>
        </div>
      </template>
    </BaseCard>

    <div
      v-if="postView"
      class="nv-post-board-actions hidden xl:flex"
      role="navigation"
      :aria-label="$t('board.postDetail.quickActions')"
    >
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.comments')"
        :title="$t('board.postDetail.comments')"
        @click="scrollToComments"
      >
        <MessageSquare class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.toList')"
        :title="$t('board.postDetail.toList')"
        @click="goToList"
      >
        <List class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="nv-post-board-action"
        :aria-label="$t('board.postDetail.scrollTop')"
        :title="$t('board.postDetail.scrollTop')"
        @click="scrollToTop"
      >
        <ArrowLeft class="h-4 w-4 rotate-90" />
      </button>
    </div>

    <Transition name="slide-up">
      <button
        v-if="showComposerCta"
        type="button"
        class="nv-post-mobile-comment-cta sm:hidden"
        @click="scrollToCommentComposer"
      >
        {{ $t('board.postDetail.focusComposer') }}
      </button>
    </Transition>

    <BaseModal :isOpen="showReportModal" :title="$t('common.report')" @close="showReportModal = false">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium nv-text-muted">
            {{ $t('report.target') }}
          </label>
          <div class="mt-1 text-sm font-medium nv-title">
            {{ $t('common.post') }} | {{ postView?.title }}
          </div>
        </div>
        <div>
          <BaseTextarea
            id="report-reason"
            v-model="reportReason"
            :label="$t('report.reason')"
            rows="4"
            :placeholder="$t('report.inputReason')"
          />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <BaseButton @click="showReportModal = false" variant="secondary" size="sm">
            {{ $t('common.cancel') }}
          </BaseButton>
          <BaseButton @click="submitReport" variant="danger" size="sm">
            {{ $t('common.submit') }}
          </BaseButton>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.nv-post-shell {
  color: var(--nv-ink);
  position: relative;
}

.nv-post-header {
  border-bottom: 1px solid var(--nv-line);
}

.nv-post-meta-strip {
  align-items: center;
  color: var(--nv-muted);
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.nv-post-back-btn {
  gap: 0.375rem;
}

.nv-post-header-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  font-size: 0.85rem;
  font-weight: 600;
  gap: 0.35rem;
  min-height: 2.5rem;
  justify-content: center;
  padding: 0.55rem 0.8rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-header-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-header-action.is-danger {
  color: var(--nv-danger);
}

.nv-post-header-action.is-danger:hover {
  background: color-mix(in srgb, var(--nv-danger) 10%, var(--nv-surface));
  color: var(--nv-danger);
}

.nv-post-copy-hint {
  background: var(--nv-success-bg);
  border: 1px solid var(--nv-success-border);
  border-radius: 9999px;
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-success-text);
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.35rem 0.65rem;
  position: absolute;
  right: 0;
  top: calc(100% + 0.5rem);
  white-space: nowrap;
  z-index: 20;
}

.nv-post-content-top {
  display: flex;
  justify-content: flex-end;
  position: relative;
}

.nv-post-content-stack {
  display: grid;
  gap: 1.25rem;
}

.nv-post-url-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 68%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  cursor: pointer;
  color: var(--nv-muted);
  display: inline-flex;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  gap: 0.35rem;
  letter-spacing: -0.02em;
  max-width: min(16rem, 42vw);
  min-width: 0;
  padding: 0.55rem 0.7rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-url-chip:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink-soft);
}

.nv-post-url-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nv-post-comments {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 0;
  border-inline: 0;
  box-shadow: none;
  margin-inline: -1rem;
  width: calc(100% + 2rem);
}

.nv-post-comments :deep(> div:first-child) {
  margin-top: 0;
}

.nv-post-article {
  padding: 0.25rem 0;
}

.nv-post-comments {
  padding: 1.15rem 1rem;
}

.nv-post-tags {
  display: grid;
  gap: 0.55rem;
}

.nv-post-section-label {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.nv-post-reaction-row {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  margin-top: 1rem;
}

.nv-post-spoiler {
  align-items: flex-start;
  background: color-mix(in srgb, var(--nv-surface) 45%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  overflow-y: auto;
  padding: clamp(0.75rem, 4vh, 1.5rem);
  position: absolute;
}

.nv-post-spoiler-card {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.5rem;
  box-shadow: var(--nv-shadow-card);
  margin-block: auto;
  max-height: 100%;
  max-width: 22rem;
  overflow-y: auto;
  padding: clamp(1rem, 3vw, 1.5rem);
  text-align: center;
  width: 100%;
}

.nv-post-spoiler-actions {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}

.nv-post-action-btn,
.nv-post-board-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.1rem;
  color: var(--nv-ink-soft);
  display: inline-flex;
  gap: 0.55rem;
  justify-content: center;
  min-height: 3rem;
  min-width: 0;
  padding: 0.8rem 1rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-post-action-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nv-post-action-count {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink);
  display: inline-flex;
  font-size: 0.68rem;
  font-weight: 700;
  justify-content: center;
  min-width: 1.45rem;
  padding: 0.12rem 0.38rem;
  position: absolute;
  right: -0.35rem;
  top: -0.2rem;
}

.nv-post-action-btn:hover,
.nv-post-board-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-action-btn.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-post-action-btn.is-bookmark {
  color: var(--nv-warning-text);
}

.nv-post-action-btn.is-report {
  color: var(--nv-danger);
}

.nv-post-action-btn-circle {
  border-radius: 9999px;
  height: 3.1rem;
  justify-content: center;
  padding: 0;
  position: relative;
  width: 3.1rem;
}

.nv-post-board-actions {
  flex-direction: column;
  gap: 0.75rem;
  position: fixed;
  right: clamp(0.5rem, calc((100vw - 1120px) / 2 - 4.5rem), 3.5rem);
  top: 10rem;
  z-index: 25;
}

.nv-post-board-action {
  box-shadow: var(--nv-shadow-card);
  height: 2.9rem;
  justify-content: center;
  padding: 0;
  width: 2.9rem;
}

.nv-post-mobile-comment-cta {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  bottom: calc(1rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-ink);
  left: 50%;
  padding: 0.75rem 1rem;
  position: fixed;
  transform: translateX(-50%);
  z-index: 75;
}

.fade-enter-active,
.fade-leave-active,
.slide-up-enter-active,
.slide-up-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to,
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translate(-50%, 0.4rem);
}

.bounce-in {
  animation: bounce-in 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes bounce-in {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.3);
  }
  100% {
    transform: scale(1);
  }
}

@media (min-width: 640px) {
  .nv-post-comments {
    padding: 1.5rem;
  }
}

@media (min-width: 1024px) {
  .nv-post-comments {
    margin-inline: -1.5rem;
    width: calc(100% + 3rem);
  }
}

@media (max-width: 639px) {
  .nv-post-url-chip {
    max-width: min(11rem, 48vw);
  }
}
</style>

<style>
.ql-editor ul {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.ql-editor ul ul {
  list-style-type: circle;
}

.ql-editor ol {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.ql-editor ol ol {
  list-style-type: lower-alpha;
}

.ql-editor li {
  display: list-item;
  margin: 0.25em 0;
}

.ql-editor .tiptap-video-wrapper {
  position: relative;
  padding-bottom: 56.25%;
  height: 0;
  overflow: hidden;
  max-width: 100%;
  margin: 0.75em 0;
}

.ql-editor .tiptap-video-wrapper iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
</style>
