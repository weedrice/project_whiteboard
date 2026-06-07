import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { adminInquiryQueryKeys } from '@/composables/adminQueryKeys'
import { formatDateTimeOrDash } from '@/utils/date'
import { renderPostContentHtml } from '@/utils/postContentHtml'
import type { AdminInquirySummary, PageResponse, Post } from '@/types'

type AdminInquiryStatusVariant = 'success' | 'warning'

export interface AdminInquiryListItem {
  id: number
  title: string
  summaryText: string
  authorName: string
  createdAtText: string
  statusLabelKey: string
  statusVariant: AdminInquiryStatusVariant
}

export interface AdminInquiryDetail {
  id: number
  title: string
  authorName: string
  createdAtText: string
  contentsHtml: string
}

function stripHtml(value?: string) {
  if (!value) return ''
  return value.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

function truncateText(value: string, maxLength = 50) {
  if (value.length <= maxLength) return value
  return `${value.slice(0, maxLength)}...`
}

function getAuthorName(post: Pick<AdminInquirySummary, 'author'> | Pick<Post, 'author'>) {
  return post.author?.displayName || '-'
}

function getStatusLabelKey(post: AdminInquirySummary) {
  return post.inquiryAnswered ? 'admin.inquiries.status.answered' : 'admin.inquiries.status.pending'
}

function getStatusVariant(post: AdminInquirySummary): AdminInquiryStatusVariant {
  return post.inquiryAnswered ? 'success' : 'warning'
}

export function toAdminInquiryListItem(post: AdminInquirySummary): AdminInquiryListItem {
  const plainSummary = stripHtml(post.summary || '')

  return {
    id: post.postId,
    title: post.title,
    summaryText: plainSummary ? truncateText(plainSummary, 50) : '-',
    authorName: getAuthorName(post),
    createdAtText: formatDateTimeOrDash(post.createdAt),
    statusLabelKey: getStatusLabelKey(post),
    statusVariant: getStatusVariant(post),
  }
}

export function toAdminInquiryDetail(post: Post): AdminInquiryDetail {
  return {
    id: post.postId,
    title: post.title,
    authorName: getAuthorName(post),
    createdAtText: formatDateTimeOrDash(post.createdAt),
    contentsHtml: renderPostContentHtml(post.contents),
  }
}

export function toAdminInquiryPage(page: PageResponse<AdminInquirySummary>): PageResponse<AdminInquiryListItem> {
  return {
    ...page,
    content: page.content.map(toAdminInquiryListItem),
  }
}

export function useAdminInquiryPosts() {
  const queryClient = useQueryClient()
  const page = ref(0)
  const size = ref(20)
  const sort = ref('createdAt,desc')
  const selectedPostId = ref<number | null>(null)

  watch(sort, () => {
    page.value = 0
  })

  const {
    data,
    isLoading,
    isFetching,
    error,
  } = useQuery({
    queryKey: adminInquiryQueryKeys.listPage(page, size, sort),
    queryFn: async () => {
      const { data } = await adminApi.getInquiryPosts({
        page: page.value,
        size: size.value,
        sort: sort.value
      })
      return toAdminInquiryPage(data.data)
    },
    placeholderData: (previousData) => previousData
  })

  const {
    data: selectedInquiry,
    isLoading: isDetailLoading,
    isFetching: isDetailFetching,
    error: detailError,
  } = useQuery({
    queryKey: adminInquiryQueryKeys.detail(selectedPostId),
    queryFn: async () => {
      const postId = selectedPostId.value
      if (!postId) {
        throw new Error('Invalid post id')
      }
      const { data } = await adminApi.getInquiryPost(postId)
      return toAdminInquiryDetail(data.data as Post)
    },
    enabled: computed(() => selectedPostId.value !== null)
  })

  const posts = computed(() => data.value?.content || [])
  const totalPages = computed(() => data.value?.totalPages || 0)
  const totalElements = computed(() => data.value?.totalElements || 0)

  function handlePageChange(nextPage: number) {
    page.value = nextPage
  }

  function openDetail(postId: number) {
    selectedPostId.value = postId
  }

  function closeDetail() {
    queryClient.invalidateQueries({ queryKey: adminInquiryQueryKeys.list })
    selectedPostId.value = null
  }

  return {
    closeDetail,
    detailError,
    error,
    handlePageChange,
    isDetailFetching,
    isDetailLoading,
    isFetching,
    isLoading,
    openDetail,
    page,
    posts,
    selectedInquiry,
    selectedPostId,
    sort,
    totalElements,
    totalPages,
  }
}
