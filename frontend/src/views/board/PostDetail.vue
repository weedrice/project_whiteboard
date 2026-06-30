<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Copy,
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostDetailHeader from '@/components/board/PostDetailHeader.vue'
import PostDetailQuickActions from '@/components/board/PostDetailQuickActions.vue'
import PostDetailReactionBar from '@/components/board/PostDetailReactionBar.vue'
import PostDetailSkeleton from '@/components/board/PostDetailSkeleton.vue'
import ReportModal from '@/components/report/ReportModal.vue'
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
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { applyImageFallback } from '@/utils/imageFallback'
import { renderPostContentHtml } from '@/utils/postContentHtml'
import type { SanitizedHtml } from '@/utils/sanitize'

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
    return t('board.postDetail.restricted')
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

const processedContents = computed<SanitizedHtml>(() => {
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
        <PostDetailHeader
          :post-view="postView"
          :edit-route="buildEditRoute()"
          :is-agent-author="isAgentAuthor"
          :can-edit="canEdit"
          :can-delete="canDelete"
          @back-to-list="router.push(buildBoardListRoute(postView.boardUrl))"
          @delete="handleDelete"
        />

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
              <PostDetailReactionBar
                :liked="postView.liked"
                :scrapped="postView.scrapped"
                :like-count="postView.likeCount"
                :is-authenticated="authStore.isAuthenticated"
                :can-report="canReport"
                :is-like-animating="isLikeAnimating"
                :is-bookmark-animating="isBookmarkAnimating"
                @like="handleLike"
                @bookmark="handleBookmark"
                @share="handleShare"
                @report="openReportModal"
              />
            </div>

            <section id="comments" ref="commentsRef" class="nv-post-comments">
              <CommentList :postId="postView.postId" :boardUrl="postView.boardUrl" />
            </section>
          </article>
        </div>
      </template>
    </BaseCard>

    <PostDetailQuickActions
      :visible="!!postView"
      @comments="scrollToComments"
      @list="goToList"
      @top="scrollToTop"
    />

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

    <ReportModal
      :isOpen="showReportModal"
      :title="$t('common.report')"
      :targetText="`${$t('common.post')} | ${postView?.title ?? ''}`"
      :submit="submitReport"
      :submitLabel="$t('common.submit')"
      submitVariant="danger"
      @close="showReportModal = false"
    />
  </div>
</template>

<style scoped>
.nv-post-shell {
  color: var(--nv-ink);
  position: relative;
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
