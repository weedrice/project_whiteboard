import type { ComputedRef, Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, RouteLocationRaw, Router } from 'vue-router'
import type { Post } from '@/types'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'
import { encodePathSegment } from '@/utils/urlPath'

interface UsePostDetailNavigationOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  post: Ref<Post | undefined>
  postView: ComputedRef<PostDetailViewModel | null>
}

export function usePostDetailNavigation({
  route,
  router,
  post,
  postView,
}: UsePostDetailNavigationOptions) {
  function buildBoardListRoute(boardUrl: string): RouteLocationRaw {
    const { fromCreate: _fromCreate, ...query } = route.query
    return {
      path: `/board/${encodePathSegment(boardUrl)}`,
      query
    }
  }

  function handleTagClick(tag: string) {
    router.push({
      path: `/tag/${encodePathSegment(tag)}`,
    })
  }

  function syncBoardListPageForDirectEntry() {
    const listPage = post.value?.boardListPage
    if (
      route.name !== 'post-detail'
      || typeof listPage !== 'number'
      || listPage <= 0
      || route.query.page
      || route.query.q
      || route.query.type
      || route.query.categoryId
      || route.query.concept
    ) {
      return
    }

    router.replace({
      path: route.path,
      hash: route.hash,
      query: {
        ...route.query,
        page: String(listPage + 1)
      }
    })
  }

  function buildEditRoute() {
    const viewModel = postView.value
    if (!viewModel) return '/'
    return `/board/${encodePathSegment(viewModel.boardUrl)}/post/${encodePathSegment(viewModel.postId)}/edit`
  }

  function goToList() {
    if (postView.value?.boardUrl) {
      router.push(buildBoardListRoute(postView.value.boardUrl))
      return
    }

    router.back()
  }

  return {
    buildBoardListRoute,
    handleTagClick,
    syncBoardListPageForDirectEntry,
    buildEditRoute,
    goToList,
  }
}
