<script setup lang="ts">
import { computed } from 'vue'
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
import ReportModal from '@/components/report/ReportModal.vue'
import { usePost } from '@/composables/usePost'
import { usePostDetailPermissions } from '@/composables/usePostDetailPermissions'
import { usePostDetailActions } from '@/composables/usePostDetailActions'
import { usePostDetailShare } from '@/composables/usePostDetailShare'
import { usePostDetailNavigation } from '@/composables/usePostDetailNavigation'
import { usePostDetailScrollEffects } from '@/composables/usePostDetailScrollEffects'
import { usePostDetailSeo } from '@/composables/usePostDetailSeo'
import { usePostDetailUiEffects } from '@/composables/usePostDetailUiEffects'
import { usePostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { usePostContentViewRef } from '@/composables/usePostContentViewRef'
import { useAuthStore } from '@/stores/auth'
import { isRestrictedResourceError } from '@/utils/errorHandler'

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

const postContents = computed(() => post.value?.contents ?? '')

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
          @back-to-list="router.push(buildBoardListRoute(postView.boardUrl))"
          @delete="handleDelete"
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
