<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PostDetailArticleContent from '@/components/board/PostDetailArticleContent.vue'
import PostDetailComposerCta from '@/components/board/PostDetailComposerCta.vue'
import PostDetailErrorState from '@/components/board/PostDetailErrorState.vue'
import PostDetailHeader from '@/components/board/PostDetailHeader.vue'
import PostDetailQuickActions from '@/components/board/PostDetailQuickActions.vue'
import PostDetailSkeleton from '@/components/board/PostDetailSkeleton.vue'
import PostRelatedSection from '@/components/board/PostRelatedSection.vue'
import ReportModal from '@/components/report/ReportModal.vue'
import { usePost } from '@/features/board/posts/queries/usePost'
import { usePostDetailPermissions } from '@/features/board/posts/detail/usePostDetailPermissions'
import { usePostDetailActions } from '@/features/board/posts/detail/usePostDetailActions'
import { usePostDetailShare } from '@/features/board/posts/detail/usePostDetailShare'
import { usePostDetailNavigation } from '@/features/board/posts/detail/usePostDetailNavigation'
import { usePostDetailScrollEffects } from '@/features/board/posts/detail/usePostDetailScrollEffects'
import { usePostDetailSeo } from '@/features/board/posts/detail/usePostDetailSeo'
import { usePostDetailUiEffects } from '@/features/board/posts/detail/usePostDetailUiEffects'
import { usePostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'
import { usePostContentViewRef } from '@/features/board/posts/detail/usePostContentViewRef'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { notificationApi } from '@/api/notification'
import { onCommentStreamEvent } from '@/composables/commentStreamEvents'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { isRestrictedResourceError } from '@/utils/errorHandler'

const route = useRoute()
const router = useRouter()
function resolveQueryClient(): QueryClient | null {
  try {
    return useQueryClient()
  } catch {
    return null
  }
}

const queryClient = resolveQueryClient()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { t } = useI18n()

const {
  usePostDetail,
  usePinPostByManager,
  useUnpinPostByManager,
  useBlindPostByManager,
  useUnblindPostByManager,
} = usePost()

const postId = computed(() => route.params.postId as string)
const commentSubscriberId = `post-detail-${Date.now()}-${Math.random().toString(36).slice(2)}`
let activeCommentTopicPostId: string | null = null
let commentRefreshTimer: ReturnType<typeof setTimeout> | null = null
const pendingCommentCount = ref(0)
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
  canManage,
  canReport
} = usePostDetailPermissions(post, {
  currentUserId,
  isAuthenticated,
  isAdmin: authIsAdmin
})

const postContents = computed(() => post.value?.contents ?? '')
const commentQueryPostId = computed(() => post.value?.postId ?? postId.value)
const pinPostMutation = usePinPostByManager()
const unpinPostMutation = useUnpinPostByManager()
const blindPostMutation = useBlindPostByManager()
const unblindPostMutation = useUnblindPostByManager()

async function runManagerAction(action: () => Promise<unknown>) {
  await action()
  toastStore.addToast(t('board.postDetail.managerActionSuccess'), 'success')
}

function handleManagerPin() {
  void runManagerAction(() => pinPostMutation.mutateAsync(postId.value))
}

function handleManagerUnpin() {
  void runManagerAction(() => unpinPostMutation.mutateAsync(postId.value))
}

function handleManagerBlind() {
  void runManagerAction(() => blindPostMutation.mutateAsync({ postId: postId.value }))
}

function handleManagerUnblind() {
  void runManagerAction(() => unblindPostMutation.mutateAsync(postId.value))
}

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

const { assignContentRef } = usePostContentViewRef(contentRef)

async function subscribeCommentTopic(nextPostId: string) {
  if (!authStore.isAuthenticated || activeCommentTopicPostId === nextPostId) return

  if (activeCommentTopicPostId) {
    await unsubscribeCommentTopic(activeCommentTopicPostId)
  }

  try {
    await notificationApi.subscribeCommentTopic(nextPostId, commentSubscriberId)
    activeCommentTopicPostId = nextPostId
  } catch {
    activeCommentTopicPostId = null
  }
}

async function unsubscribeCommentTopic(targetPostId: string) {
  try {
    await notificationApi.unsubscribeCommentTopic(targetPostId, commentSubscriberId)
  } catch {
    // SSE comments are an enhancement; the regular comment queries keep working.
  } finally {
    if (activeCommentTopicPostId === targetPostId) {
      activeCommentTopicPostId = null
    }
  }
}

function clearCommentRefreshTimer() {
  if (!commentRefreshTimer) return
  clearTimeout(commentRefreshTimer)
  commentRefreshTimer = null
}

function refreshComments() {
  clearCommentRefreshTimer()
  pendingCommentCount.value = 0
  void queryClient?.invalidateQueries({ queryKey: commentQueryKeys.postRoot(commentQueryPostId.value) })
}

function scheduleCommentRefresh() {
  clearCommentRefreshTimer()
  commentRefreshTimer = setTimeout(() => {
    refreshComments()
  }, 2000)
}

const stopCommentStreamListener = onCommentStreamEvent((event) => {
  if (String(event.postId) !== String(postId.value)) return

  if (event.action === 'DELETED') {
    scheduleCommentRefresh()
    return
  }

  if (event.action !== 'CREATED' || event.actorUserId === currentUserId.value) return
  pendingCommentCount.value += 1
})

watch([postId, isAuthenticated], ([nextPostId, authenticated]) => {
  pendingCommentCount.value = 0
  clearCommentRefreshTimer()
  if (!authenticated) {
    if (activeCommentTopicPostId) {
      void unsubscribeCommentTopic(activeCommentTopicPostId)
    }
    return
  }

  void subscribeCommentTopic(nextPostId)
}, { immediate: true })

onBeforeUnmount(() => {
  clearCommentRefreshTimer()
  stopCommentStreamListener()
  if (activeCommentTopicPostId) {
    void unsubscribeCommentTopic(activeCommentTopicPostId)
  }
})
</script>

<template>
  <div class="nv-post-shell">
    <BaseCard noPadding>
      <PostDetailSkeleton v-if="isLoading" />

      <PostDetailErrorState v-else-if="error" :message="error" @back="router.back()" />

      <template v-else-if="post && postView">
        <PostDetailHeader
          :post-view="postView"
          :edit-route="buildEditRoute()"
          :is-agent-author="isAgentAuthor"
          :can-edit="canEdit"
          :can-delete="canDelete"
          :can-manage="canManage"
          @back-to-list="router.push(buildBoardListRoute(postView.boardUrl))"
          @delete="handleDelete"
          @pin="handleManagerPin"
          @unpin="handleManagerUnpin"
          @blind="handleManagerBlind"
          @unblind="handleManagerUnblind"
        />

        <div class="px-4 pt-5 lg:px-6">
          <article class="nv-post-page-stack min-w-0">
            <PostDetailArticleContent
              :post-view="postView"
              :post-contents="postContents"
              :assign-content-ref="assignContentRef"
              :current-url="currentUrl"
              :compact-url="compactUrl"
              :show-copy-hint="showCopyHint"
              :is-blurred="isBlurred"
              :time-left="timeLeft"
              :is-authenticated="authStore.isAuthenticated"
              :can-report="canReport"
              :is-like-animating="isLikeAnimating"
              :is-bookmark-animating="isBookmarkAnimating"
              @copy-url="onUrlBarClick"
              @reveal-spoiler="revealSpoiler"
              @tag-click="handleTagClick"
              @like="handleLike"
              @bookmark="handleBookmark"
              @share="handleShare"
              @report="openReportModal"
            />
            <PostRelatedSection :post-id="postView.postId" />
            <section id="comments" ref="commentsRef" class="nv-post-comments">
              <CommentList
                :postId="postView.postId"
                :boardUrl="postView.boardUrl"
                :last-viewed-at="postView.lastViewedAt"
                :pending-comment-count="pendingCommentCount"
                @refresh-comments="refreshComments"
              />
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

    <PostDetailComposerCta :visible="showComposerCta" @focus="scrollToCommentComposer" />

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

.nv-post-page-stack {
  display: grid;
  gap: 1.25rem;
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

.nv-post-comments {
  padding: 1.15rem 1rem;
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
