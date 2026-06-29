import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { adminInquiryQueryKeys } from '@/composables/adminQueryKeys'
import { useApiPageQuery, useNullableApiQuery } from '@/composables/useApiQuery'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { formatDateTimeOrDash } from '@/utils/date'
import { renderPostContentHtml } from '@/utils/postContentHtml'
import { stripHtmlToText, truncateWithEllipsis } from '@/utils/textExcerpt'
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
  const plainSummary = stripHtmlToText(post.summary, {
    tagReplacement: ' ',
    collapseWhitespace: true,
  })

  return {
    id: post.postId,
    title: post.title,
    summaryText: plainSummary ? truncateWithEllipsis(plainSummary, 50) : '-',
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
  const {
    page,
    size,
    handlePageChange,
    resetPage,
  } = usePaginatedQueryState({
    initialSize: 20,
  })
  const sort = ref('createdAt,desc')
  const selectedPostId = ref<number | null>(null)

  watch(sort, () => {
    resetPage()
  })

  const {
    data,
    isLoading,
    isFetching,
    error,
  } = useApiPageQuery<AdminInquirySummary, PageResponse<AdminInquiryListItem>>({
    queryKey: adminInquiryQueryKeys.listPage(page, size, sort),
    request: () => adminApi.getInquiryPosts({
        page: page.value,
        size: size.value,
        sort: sort.value
    }),
    selectData: toAdminInquiryPage,
  })

  const {
    data: selectedInquiry,
    isLoading: isDetailLoading,
    isFetching: isDetailFetching,
    error: detailError,
  } = useNullableApiQuery<Post, AdminInquiryDetail>({
    queryKey: adminInquiryQueryKeys.detail(selectedPostId),
    request: () => {
      const postId = selectedPostId.value
      return postId ? adminApi.getInquiryPost(postId) : null
    },
    selectData: toAdminInquiryDetail,
    enabled: computed(() => selectedPostId.value !== null)
  })

  const {
    items: posts,
    totalPages,
    totalElements,
  } = usePageResponseState(data, page)

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
