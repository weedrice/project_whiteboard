import type { QueryClient } from '@tanstack/vue-query'
import type { Post } from '@/types'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { postQueryKeys } from '@/composables/postQueryKeys'

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
  postId: PostId,
  updater: (post: Partial<Post>) => Partial<Post>
) {
  queryClient.setQueriesData<Post>({ queryKey: postQueryKeys.detailPrefix(postId) }, (old) => {
    if (!old) return old
    return updater(old) as Post
  })

  queryClient.setQueriesData<InfiniteQueryData>(
    { queryKey: postQueryKeys.lists },
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

  queryClient.getQueriesData({ queryKey: postQueryKeys.boardPostsRoot }).forEach(([key]) => {
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

export function savePostCacheSnapshots(queryClient: QueryClient, postId: PostId) {
  return {
    postDetailQueries: queryClient.getQueriesData({ queryKey: postQueryKeys.detailPrefix(postId) }),
    postsQueries: queryClient.getQueriesData({ queryKey: postQueryKeys.lists }),
    boardQueries: queryClient.getQueriesData({ queryKey: postQueryKeys.boardPostsRoot }),
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

export function invalidatePostCaches(queryClient: QueryClient, postId: PostId) {
  queryClient.invalidateQueries({ queryKey: postQueryKeys.detailPrefix(postId) })
  queryClient.invalidateQueries({ queryKey: postQueryKeys.lists })
  queryClient.invalidateQueries({ queryKey: homeQueryKeys.landingRoot })
  queryClient.invalidateQueries({ queryKey: postQueryKeys.boardPostsRoot })
  queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey
      return Array.isArray(key) && key[0] === 'board' && key[1] === 'posts'
    },
  })
}
