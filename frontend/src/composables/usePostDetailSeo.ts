import { computed, watch, type ComputedRef, type Ref } from 'vue'
import { useHead } from '@unhead/vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { Post } from '@/types'
import type { PostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { stripHtmlToText, truncateWithEllipsis } from '@/utils/textExcerpt'

interface UsePostDetailSeoOptions {
  route: RouteLocationNormalizedLoaded
  post: Ref<Post | undefined>
  postView: ComputedRef<PostDetailViewModel | null>
  t: (key: string) => string
}

export function usePostDetailSeo({
  route,
  post,
  postView,
  t,
}: UsePostDetailSeoOptions) {
  const postPageTitle = computed(() => {
    const postTitle = postView.value?.title.trim()
    const boardName = postView.value?.boardName.trim()

    if (postTitle && boardName) {
      return `${postTitle} - ${boardName}`
    }
    return postTitle || 'Post'
  })

  const postDescription = computed(() => {
    if (!post.value?.contents) return 'Post content'
    const text = stripHtmlToText(post.value.contents)
    return truncateWithEllipsis(text, 160, { ellipsisAtLength: true })
  })

  const canonicalUrl = computed(() => {
    if (typeof window === 'undefined') return ''
    const normalizedPath = route.path.endsWith('/') ? route.path : `${route.path}/`
    return `${window.location.origin}${normalizedPath}`
  })

  const articleStructuredData = computed(() => {
    const viewModel = postView.value
    if (!viewModel || !canonicalUrl.value) return ''

    return JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: viewModel.title,
      datePublished: viewModel.createdAt,
      dateModified: post.value?.modifiedAt || viewModel.createdAt,
      author: {
        '@type': 'Person',
        name: viewModel.authorDisplayName
      },
      mainEntityOfPage: canonicalUrl.value,
      url: canonicalUrl.value
    })
  })

  useHead({
    titleTemplate: '%s',
    title: postPageTitle,
    link: [
      { rel: 'canonical', href: canonicalUrl }
    ],
    meta: [
      { name: 'description', content: postDescription },
      { property: 'og:title', content: computed(() => `${postView.value?.title || 'Post'} - ${t('common.appName')}`) },
      { property: 'og:description', content: postDescription },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonicalUrl }
    ],
    script: [
      {
        type: 'application/ld+json',
        textContent: articleStructuredData
      }
    ]
  })

  watch([() => route.name, postPageTitle], ([routeName, title]) => {
    if (routeName !== 'post-detail' || typeof document === 'undefined') {
      return
    }
    document.title = title
  }, { immediate: true })

  return {
    postPageTitle,
    postDescription,
    canonicalUrl,
    articleStructuredData,
  }
}
