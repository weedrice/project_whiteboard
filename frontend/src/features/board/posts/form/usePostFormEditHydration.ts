import { ref, watchEffect, type Ref } from 'vue'
import type { Post } from '@/types'
import type { PostComposerMode, PostComposerSnapshot } from '@/features/board/posts/form/usePostComposerState'

interface UsePostFormEditHydrationOptions {
  mode: () => PostComposerMode
  post: Ref<Post | null | undefined>
  postId: Ref<string | number>
  applyDraftSnapshot: (snapshot: PostComposerSnapshot) => void
  markCurrentSnapshotSaved: () => void
}

export function usePostFormEditHydration({
  mode,
  post,
  postId,
  applyDraftSnapshot,
  markCurrentSnapshotSaved,
}: UsePostFormEditHydrationOptions) {
  const hasHydratedEditPost = ref(false)

  function resetEditHydrationState() {
    hasHydratedEditPost.value = false
  }

  watchEffect(() => {
    if (mode() !== 'edit' || !post.value || hasHydratedEditPost.value) return
    if (String(post.value.postId) !== String(postId.value)) return
    hasHydratedEditPost.value = true
    applyDraftSnapshot({
      title: post.value.title,
      contents: post.value.contents,
      categoryId: post.value.category?.categoryId,
      tags: post.value.tags?.map((tag: { name?: string } | string) => typeof tag === 'string' ? tag : (tag.name ?? '')) ?? [],
      isNsfw: post.value.isNsfw,
      isSpoiler: post.value.isSpoiler,
      isSecret: post.value.isSecret ?? false,
      seriesNavigation: post.value.seriesNavigation,
      poll: post.value.poll ?? null,
      isNotice: false,
      fileIds: [],
    })
    markCurrentSnapshotSaved()
  })

  return {
    resetEditHydrationState,
  }
}
