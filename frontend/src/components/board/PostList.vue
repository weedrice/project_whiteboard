<script setup lang="ts">
// defineProps and defineEmits are compiler macros and don't need to be imported
import { MessageSquare, ThumbsUp, User, Clock, Image as ImageIcon, ArrowUp, ArrowDown } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'
import BaseTable from '@/components/common/BaseTable.vue'
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

const emit = defineEmits<{
  (e: 'update:sort', sort: string): void
}>()

function formatDate(dateString: string) {
  const date = new Date(dateString)
  const today = new Date()

  const isToday = date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()

  if (isToday) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString()
}

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
    <div class="table-container">
      <BaseTable :columns="columns" :items="posts" :emptyText="$t('board.list.noPosts')" @sort="handleSort">
        <template #cell-postId="{ item }">
          <span v-if="item.isNotice" class="font-bold text-red-600 dark:text-red-400">{{ $t('common.notice') }}</span>
          <span v-else>{{ item.rowNum }}</span>
        </template>

        <template #cell-boardName="{ item }">
          {{ item.boardName || '-' }}
        </template>

        <template #cell-title="{ item }">
          <div class="flex items-center h-full">
            <router-link :to="`/board/${boardUrl || item.boardUrl}/post/${item.postId}`"
              class="hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center h-full w-full"
              v-if="boardUrl || item.boardUrl">
              <span v-if="item.category && item.category.name !== '일반'" class="badge badge-gray mr-2">
                {{ item.category.name }}
              </span>
              <span v-if="item.isNotice" class="badge badge-red mr-2">
                {{ $t('common.notice') }}
              </span>
              <span v-if="item.hasImage" class="mr-1 text-gray-400 flex items-center">
                <ImageIcon class="h-4 w-4" />
              </span>
              <span class="truncate">{{ item.title }}</span>
              <span v-if="item.commentCount > 0"
                class="ml-1 text-indigo-600 dark:text-indigo-400 text-xs flex items-center">
                [{{ item.commentCount }}]
              </span>
            </router-link>
            <span v-else class="text-gray-400 flex items-center h-full cursor-not-allowed w-full"
              :title="$t('board.invalidUrl')">
              <span class="truncate">{{ item.title }}</span>
            </span>
          </div>
        </template>

        <template #cell-author="{ item }">
          <UserMenu v-if="item.author" :user-id="item.author.userId" :display-name="item.author.displayName" />
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
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