<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import CommentList from '@/components/comment/CommentList.vue'
import { useI18n } from 'vue-i18n'
import type { Post, PostSummary } from '@/types'

const { t } = useI18n()
const queryClient = useQueryClient()

const page = ref(0)
const size = ref(20)
const sort = ref('createdAt,desc')
const selectedPostId = ref<number | null>(null)

watch(sort, () => {
  page.value = 0
})

const { data, isLoading, isFetching, error } = useQuery({
  queryKey: ['admin', 'inquiry-posts', page, size, sort],
  queryFn: async () => {
    const { data } = await adminApi.getInquiryPosts({
      page: page.value,
      size: size.value,
      sort: sort.value
    })
    return data.data
  },
  placeholderData: (previousData) => previousData
})

const {
  data: selectedPost,
  isLoading: isDetailLoading,
  isFetching: isDetailFetching,
  error: detailError
} = useQuery({
  queryKey: ['admin', 'inquiry-post-detail', selectedPostId],
  queryFn: async () => {
    const postId = selectedPostId.value
    if (!postId) {
      throw new Error('Invalid post id')
    }
    const { data } = await adminApi.getInquiryPost(postId)
    return data.data as Post
  },
  enabled: computed(() => selectedPostId.value !== null)
})

const posts = computed(() => (data.value?.content || []) as PostSummary[])
const totalPages = computed(() => data.value?.totalPages || 0)
const totalElements = computed(() => data.value?.totalElements || 0)

function handlePageChange(nextPage: number) {
  page.value = nextPage
}

function formatDate(dateString?: string) {
  if (!dateString) return '-'
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString()
}

function stripHtml(value?: string) {
  if (!value) return ''
  return value.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

function truncateText(value: string, maxLength = 50) {
  if (value.length <= maxLength) return value
  return `${value.slice(0, maxLength)}...`
}

function getSummary(post: PostSummary) {
  const plain = stripHtml(post.summary || '')
  if (!plain) return '-'
  return truncateText(plain, 50)
}

function getInquiryStatusLabel(post: PostSummary) {
  return post.inquiryAnswered ? '답변 완료' : '답변 대기'
}

function openDetail(postId: number) {
  selectedPostId.value = postId
}

function closeDetail() {
  queryClient.invalidateQueries({ queryKey: ['admin', 'inquiry-posts'] })
  selectedPostId.value = null
}
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center sm:justify-between">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">문의 게시글 조회</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">문의 게시판에 등록된 글을 확인하고 댓글로 답변할 수 있습니다.</p>
      </div>
      <div class="mt-3 flex items-center gap-2 sm:mt-0">
        <label for="inquiry-sort" class="text-sm text-gray-600 dark:text-gray-300">정렬</label>
        <select
          id="inquiry-sort"
          v-model="sort"
          class="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
        >
          <option value="createdAt,desc">최신순</option>
          <option value="createdAt,asc">오래된순</option>
        </select>
      </div>
    </div>

    <div class="mt-4 rounded-lg border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <div v-if="isLoading" class="flex items-center justify-center py-10">
        <BaseSpinner size="lg" />
      </div>

      <div v-else-if="error" class="px-4 py-6 text-sm text-red-600 dark:text-red-400">
        {{ t('common.messages.loadFailed') }}
      </div>

      <div v-else class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead class="bg-gray-50 dark:bg-gray-900/30">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">제목</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">내용</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">작성자</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">작성일</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">상태</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <tr v-if="posts.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
                등록된 문의가 없습니다.
              </td>
            </tr>
            <tr
              v-for="post in posts"
              :key="post.postId"
              class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/40"
              @click="openDetail(post.postId)"
            >
              <td class="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">
                <button type="button" class="max-w-[280px] truncate text-left hover:underline" @click.stop="openDetail(post.postId)">
                  {{ post.title }}
                </button>
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ getSummary(post) }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ post.author?.displayName || post.authorName || '-' }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ formatDate(post.createdAt) }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                <span
                  class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
                  :class="post.inquiryAnswered ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'"
                >
                  {{ getInquiryStatusLabel(post) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mt-3 flex items-center justify-between">
      <p class="text-sm text-gray-600 dark:text-gray-300">총 {{ totalElements }}건</p>
      <p v-if="isFetching" class="text-xs text-gray-500 dark:text-gray-400">갱신 중...</p>
    </div>

    <div class="mt-4">
      <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
    </div>

    <BaseModal :isOpen="selectedPostId !== null" title="문의 상세" size="2xl" mobile-full @close="closeDetail">
      <div class="space-y-4 p-2 sm:p-4">
        <div v-if="isDetailLoading || isDetailFetching" class="flex items-center justify-center py-10">
          <BaseSpinner size="lg" />
        </div>

        <div v-else-if="detailError" class="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300">
          {{ t('common.messages.loadFailed') }}
        </div>

        <template v-else-if="selectedPost">
          <div class="space-y-2 border-b border-gray-200 pb-3 dark:border-gray-700">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ selectedPost.title }}</h2>
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
              <span>작성자 {{ selectedPost.author?.displayName || '-' }}</span>
              <span>작성일 {{ formatDate(selectedPost.createdAt) }}</span>
            </div>
          </div>

          <div class="max-h-[60vh] overflow-y-auto rounded-md bg-gray-50 p-4 text-sm text-gray-800 dark:bg-gray-900/30 dark:text-gray-200">
            <div v-if="selectedPost.contents" class="break-words leading-6" v-html="selectedPost.contents" />
            <div v-else>-</div>
          </div>

          <div class="border-t border-gray-200 pt-4 dark:border-gray-700">
            <CommentList :postId="selectedPost.postId" boardUrl="inquiry" />
          </div>
        </template>
      </div>

      <template #footer>
        <div class="flex justify-end">
          <BaseButton type="button" variant="secondary" @click="closeDetail">{{ t('common.close') }}</BaseButton>
        </div>
      </template>
    </BaseModal>
  </div>
</template>
