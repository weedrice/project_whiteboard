<script setup lang="ts">
// defineProps and defineEmits are compiler macros and don't need to be imported
import { MessageSquare, ThumbsUp, User, Clock, Image as ImageIcon, ArrowUp, ArrowDown, Eye } from 'lucide-vue-next'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// Define interfaces for props
interface Post {
  postId: number
  boardUrl?: string | number
  boardName?: string
  title: string
  author?: {
    userId: number
    displayName: string
  }
  createdAt: string
  viewCount: number
  likeCount: number
  commentCount: number
  isNotice?: boolean
  hasImage?: boolean
  category?: {
    name: string
  }
  rowNum?: number
}

const props = withDefaults(defineProps<{
  posts: Post[]
  boardUrl?: string
  totalCount?: number
  page?: number
  size?: number
  currentSort?: string
  currentPostId?: string
  showBoardName?: boolean
  hideNoColumn?: boolean
}>(), {
  totalCount: 0,
  page: 0,
  size: 20,
  currentSort: 'createdAt,desc',
  showBoardName: false,
  hideNoColumn: false
})

function isCurrentPost(item: Post): boolean {
  return String(item.postId) === String(props.currentPostId ?? '')
}

function onPostClick(event: Event, item: Post) {
  if (isCurrentPost(item)) {
    event.preventDefault()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function onTitleClick(event: Event, item: Post) {
  if (isCurrentPost(item)) {
    event.preventDefault()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

import { formatRelativeDate } from '@/utils/date'

const emit = defineEmits<{
  (e: 'update:sort', sort: string): void
}>()

function handleSort(field: string) {
  const [currentField, currentDirection] = props.currentSort.split(',')
  let newDirection = 'desc'

  if (field === currentField) {
    newDirection = currentDirection === 'desc' ? 'asc' : 'desc'
  }

  emit('update:sort', `${field},${newDirection}`)
}

const columns = computed(() => {
  const cols = []

  if (!props.hideNoColumn) {
    cols.push({
      key: 'postId',
      label: t('common.no'),
      width: '10%',
      align: 'center' as const,
      sortable: true
    })
  }

  if (props.showBoardName) {
    cols.push({
      key: 'boardName',
      label: t('common.board'),
      width: '15%',
      align: 'center' as const
    })
  }

  cols.push({
    key: 'title',
    label: t('common.title'),
    width: props.showBoardName ? '28%' : '38%',
    align: 'left' as const
  })

  cols.push({
    key: 'author',
    label: t('common.author'),
    width: '15%',
    align: 'center' as const
  })

  cols.push({
    key: 'createdAt',
    label: t('common.date'),
    width: '12%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'viewCount',
    label: t('common.views'),
    width: '10%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'likeCount',
    label: t('common.likes'),
    width: '10%',
    align: 'center' as const,
    sortable: true
  })

  return cols
})
</script>

<template>
  <div class="card">
    <!-- Mobile: 제목·날짜 상단, 글쓴이·조회·추천 하단 (번호 없음, 공지 배경 구분) -->
    <div class="sm:hidden divide-y divide-gray-200 dark:divide-gray-700">
      <template v-if="posts.length === 0">
        <div class="px-4 py-8 text-center text-xs text-gray-500 dark:text-gray-400">
          {{ $t('board.list.noPosts') }}
        </div>
      </template>
      <router-link
        v-for="(item, index) in posts"
        :key="item.postId"
        :to="boardUrl || item.boardUrl ? `/board/${boardUrl || item.boardUrl}/post/${item.postId}` : {}"
        :class="[
          'block px-3 py-3 transition-colors',
          item.isNotice ? 'bg-gray-100/80 dark:bg-gray-700/40 hover:bg-gray-200/80 dark:hover:bg-gray-700/60' : 'hover:bg-gray-50 dark:hover:bg-gray-700/50',
          isCurrentPost(item) ? 'bg-indigo-50 dark:bg-indigo-900/20 ring-1 ring-inset ring-indigo-200 dark:ring-indigo-700' : ''
        ]"
        active-class=""
        @click="(e) => onPostClick(e, item)"
      >
        <div class="flex items-center gap-2 min-w-0">
          <span class="flex-1 min-w-0 truncate text-xs text-gray-900 dark:text-white">
            <span v-if="item.category && item.category.name !== '일반'" class="badge badge-gray mr-1 text-[10px]">{{ item.category.name }}</span>
            <span v-if="item.isNotice" class="badge badge-red mr-1 text-[10px]">{{ $t('common.notice') }}</span>
            <span v-if="item.hasImage" class="mr-0.5 inline-flex text-gray-400"><ImageIcon class="h-3.5 w-3.5" /></span>
            {{ item.title }}
            <span v-if="item.commentCount > 0" class="text-indigo-600 dark:text-indigo-400">[{{ item.commentCount }}]</span>
          </span>
          <span class="flex-shrink-0 text-[10px] text-gray-500 dark:text-gray-400">{{ formatRelativeDate(item.createdAt) }}</span>
        </div>
        <div class="flex items-center gap-3 mt-1.5 text-[10px] text-gray-500 dark:text-gray-400">
          <span class="flex items-center gap-0.5">
            <User class="h-3 w-3" />
            {{ item.author?.displayName || '-' }}
          </span>
          <span class="flex items-center gap-0.5">
            <Eye class="h-3 w-3" />
            {{ item.viewCount }}
          </span>
          <span class="flex items-center gap-0.5">
            <ThumbsUp class="h-3 w-3" />
            {{ item.likeCount }}
          </span>
        </div>
      </router-link>
    </div>

    <!-- Desktop: 테이블 -->
    <div class="hidden sm:block table-container">
      <BaseTable
        :columns="columns"
        :items="posts"
        :emptyText="$t('board.list.noPosts')"
        :rowClass="(item) => isCurrentPost(item) ? 'post-list-row-current' : ''"
        @sort="handleSort"
      >
        <template #cell-postId="{ item }">
          <span v-if="item.isNotice" class="font-bold text-red-600 dark:text-red-400">{{ $t('common.notice') }}</span>
          <span v-else>{{ item.rowNum }}</span>
        </template>

        <template #cell-boardName="{ item }">
          <router-link v-if="item.boardUrl" :to="`/board/${item.boardUrl}`"
            class="hover:text-indigo-600 dark:hover:text-indigo-400 text-gray-700 dark:text-gray-300">
            {{ item.boardName || '-' }}
          </router-link>
          <span v-else>{{ item.boardName || '-' }}</span>
        </template>

        <template #cell-title="{ item }">
          <div class="flex items-center h-full min-w-0 overflow-hidden">
            <router-link :to="`/board/${boardUrl || item.boardUrl}/post/${item.postId}`"
              class="hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center h-full min-w-0 flex-1 overflow-hidden"
              v-if="boardUrl || item.boardUrl"
              @click="(e) => onTitleClick(e, item)">
              <span v-if="item.category && item.category.name !== '일반'" class="badge badge-gray mr-1.5 sm:mr-2 text-[10px] sm:text-xs flex-shrink-0">
                {{ item.category.name }}
              </span>
              <span v-if="item.isNotice" class="badge badge-red mr-1.5 sm:mr-2 text-[10px] sm:text-xs flex-shrink-0">
                {{ $t('common.notice') }}
              </span>
              <span v-if="item.hasImage" class="mr-1 text-gray-400 flex items-center flex-shrink-0">
                <ImageIcon class="h-4 w-4" />
              </span>
              <span class="truncate min-w-0">{{ item.title }}</span>
              <span v-if="item.commentCount > 0"
                class="ml-1 text-indigo-600 dark:text-indigo-400 text-[10px] sm:text-xs flex-shrink-0">
                [{{ item.commentCount }}]
              </span>
            </router-link>
            <span v-else class="text-gray-400 flex items-center h-full cursor-not-allowed min-w-0 overflow-hidden"
              :title="$t('board.invalidUrl')">
              <span class="truncate min-w-0">{{ item.title }}</span>
            </span>
          </div>
        </template>

        <template #cell-author="{ item }">
          <span class="inline-block min-w-0 max-w-full overflow-hidden">
            <UserMenu v-if="item.author" :user-id="item.author.userId" :display-name="item.author.displayName" />
          </span>
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatRelativeDate(item.createdAt) }}
        </template>

        <template #cell-viewCount="{ item }">
          {{ item.viewCount }}
        </template>

        <template #cell-likeCount="{ item }">
          {{ item.likeCount }}
        </template>
      </BaseTable>
    </div>
  </div>
</template>

<style scoped>
/* 현재 읽는 글 하이라이트: td에만 배경 적용, 셀별 테두리 없음(한 줄로 보이게) */
:deep(.post-list-row-current td) {
  background-color: rgb(238 242 255);
}
:deep(.post-list-row-current td:first-child) {
  border-left: 4px solid rgb(99 102 241);
}
</style>
<style>
/* 다크모드: 별도 비-scoped 블록으로 적용 (scoped + :global 조합 시 미적용 이슈 방지) */
html.dark .post-list-row-current td {
  background-color: rgb(49 46 129 / 0.35);
}
html.dark .post-list-row-current td:first-child {
  border-left-color: rgb(129 140 248);
}
</style>
