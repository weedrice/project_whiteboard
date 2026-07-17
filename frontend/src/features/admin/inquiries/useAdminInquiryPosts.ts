import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { adminInquiryQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
  callAdminApiWithOptionalConfig,
  useAdminNullableDataQuery,
  useAdminPageQuery,
} from '@/features/admin/queries/adminApiQuery'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { formatDateTimeOrDash } from '@/utils/date'
import { stripHtmlToText, truncateWithEllipsis } from '@/utils/textExcerpt'
import type { AdminInquirySummary, PageResponse, Post } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey } from '@/queryAuthScope'

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
  contents: string
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
    contents: post.contents ?? '',
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
  const authStore = useAuthStore()
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
  } = useAdminPageQuery<AdminInquirySummary, PageResponse<AdminInquiryListItem>>(
    computed(() => adminInquiryQueryKeys.listPage(page.value, size.value, sort.value)),
    (config) => {
      const params = {
        page: page.value,
        size: size.value,
        sort: sort.value
      }
      return callAdminApiWithOptionalConfig(
        config,
        (requestConfig) => adminApi.getInquiryPosts(params, requestConfig),
        () => adminApi.getInquiryPosts(params),
      )
    },
    { selectData: toAdminInquiryPage },
  )

  const {
    data: selectedInquiry,
    isLoading: isDetailLoading,
    isFetching: isDetailFetching,
    error: detailError,
  } = useAdminNullableDataQuery<Post, AdminInquiryDetail>(
    computed(() => adminInquiryQueryKeys.detail(selectedPostId.value)),
    (config) => {
      const postId = selectedPostId.value
      if (!postId) return null
      return callAdminApiWithOptionalConfig(
        config,
        (requestConfig) => adminApi.getInquiryPost(postId, requestConfig),
        () => adminApi.getInquiryPost(postId),
      )
    },
    computed(() => selectedPostId.value !== null),
    { selectData: toAdminInquiryDetail },
  )

  const {
    items: posts,
    totalPages,
    totalElements,
  } = usePageResponseState(data, page)

  function openDetail(postId: number) {
    selectedPostId.value = postId
  }

  function closeDetail() {
    queryClient.invalidateQueries({
      queryKey: currentSessionQueryKey(authStore, adminInquiryQueryKeys.list),
    })
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
