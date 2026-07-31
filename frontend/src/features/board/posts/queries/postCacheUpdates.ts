import type { QueryClient } from '@tanstack/vue-query'
import type { Post } from '@/types'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { tagQueryKeys } from '@/composables/tagQueryKeys'
import { searchQueryKeys } from '@/features/search/queries/searchQueryKeys'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

type PostId = string | number

interface InfiniteQueryData {
  pages: Array<{ content: Partial<Post>[]; [key: string]: unknown }>
  pageParams: unknown[]
}

interface PageQueryData {
  content: Partial<Post>[]
  [key: string]: unknown
}

export function updatePostInAllCaches(
  queryClient: QueryClient,
  sessionGeneration: number,
  postId: PostId,
  updater: (post: Partial<Post>) => Partial<Post>
) {
  queryClient.setQueriesData<Post>({
    queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.detailPrefix(postId)),
  }, (old) => {
    if (!old) return old
    return updater(old) as Post
  })

  queryClient.setQueriesData<InfiniteQueryData>(
    { queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.lists) },
    (old) => {
      if (!old?.pages) return old
      return {
        ...old,
        pages: old.pages.map((page) => ({
          ...page,
          content: Array.isArray(page.content)
            ? page.content.map((post) => (
              String(post.postId) === String(postId) ? updater(post) : post
            ))
            : page.content,
        })),
      }
    }
  )

  queryClient.getQueriesData({
    queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.boardPostsRoot),
  }).forEach(([key]) => {
    queryClient.setQueryData(key, (old: InfiniteQueryData | PageQueryData | undefined) => {
      if (!old) return old

      const infiniteData = old as InfiniteQueryData
      if (Array.isArray(infiniteData.pages)) {
        return {
          ...old,
          pages: infiniteData.pages.map((page) => ({
            ...page,
            content: Array.isArray(page.content)
              ? page.content.map((post) => (
                String(post.postId) === String(postId) ? updater(post) : post
              ))
              : page.content,
          })),
        }
      }

      if ('content' in old && Array.isArray(old.content)) {
        return {
          ...old,
          content: old.content.map((post) => (
            String(post.postId) === String(postId) ? updater(post) : post
          )),
        }
      }

      return old
    })
  })
}

export function savePostCacheSnapshots(
  queryClient: QueryClient,
  sessionGeneration: number,
  postId: PostId,
) {
  return {
    postDetailQueries: queryClient.getQueriesData({
      queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.detailPrefix(postId)),
    }),
    postsQueries: queryClient.getQueriesData({
      queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.lists),
    }),
    boardQueries: queryClient.getQueriesData({
      queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.boardPostsRoot),
    }),
  }
}

export function restorePostCacheSnapshots(
  queryClient: QueryClient,
  snapshots: ReturnType<typeof savePostCacheSnapshots>
) {
  snapshots.postDetailQueries.forEach(([key, data]) => {
    queryClient.setQueryData(key, data)
  })
  snapshots.postsQueries.forEach(([key, data]) => {
    queryClient.setQueryData(key, data)
  })
  snapshots.boardQueries.forEach(([key, data]) => {
    queryClient.setQueryData(key, data)
  })
}

export function invalidatePostCaches(
  queryClient: QueryClient,
  sessionGeneration: number,
  postId: PostId,
) {
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.detailPrefix(postId)) })
  invalidatePostCollectionCaches(queryClient, sessionGeneration)
}

export function invalidatePostCollectionCaches(queryClient: QueryClient, sessionGeneration: number) {
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.lists) })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, tagQueryKeys.all) })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, homeQueryKeys.landingRoot) })
  queryClient.invalidateQueries({
    queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.boardPostsRoot),
  })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, boardQueryKeys.detailRoot) })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, boardQueryKeys.all) })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, userQueryKeys.myPostsRoot) })
  queryClient.invalidateQueries({ queryKey: sessionQueryKey(sessionGeneration, searchQueryKeys.all) })
  queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey
      return Array.isArray(key)
        && key[0] === 'session'
        && key[1] === sessionGeneration
        && key[2] === 'board'
        && key[3] === 'posts'
    },
  })
  queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey
      if (!Array.isArray(key) || key[0] !== 'session' || key[1] !== sessionGeneration || key[2] !== 'user') {
        return false
      }
      const publicUserId = key[3]
      return (typeof publicUserId === 'string' || typeof publicUserId === 'number')
        && publicUserId !== 'me'
        && Number.isFinite(Number(publicUserId))
    },
  })
}
