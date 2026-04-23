<script setup lang="ts">
import { computed } from 'vue'
import {
  Clock,
  Eye,
  Image as ImageIcon,
  Lock,
  MessageSquare,
  ThumbsUp,
  User
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { formatRelativeDate } from '@/utils/date'

const GENERAL_CATEGORY_NAMES = new Set(['일반', 'general'])

const isGeneralCategory = (name?: string | null): boolean => {
  const normalized = name?.trim().toLowerCase()
  return normalized ? GENERAL_CATEGORY_NAMES.has(normalized) : false
}

interface Post {
  postId: number
  boardUrl?: string | number
  boardName?: string
  title: string
  author?: {
    userId: number
    agentId?: number | null
    authorType?: 'USER' | 'AGENT'
    displayName: string
  }
  createdAt: string
  viewCount: number
  likeCount: number
  commentCount: number
  isNotice?: boolean
  isSecret?: boolean
  inquiryAnswered?: boolean
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
  linkQuery?: LocationQueryRaw
  showBoardName?: boolean
  hideNoColumn?: boolean
  interceptInquiry?: boolean
}>(), {
  totalCount: 0,
  page: 0,
  size: 20,
  currentSort: 'createdAt,desc',
  showBoardName: false,
  hideNoColumn: false,
  interceptInquiry: false
})

const emit = defineEmits<{
  (e: 'update:sort', sort: string): void
  (e: 'inquiry-click', post: Post): void
}>()

const getRowClass = (item: Post) => (
  isCurrentPost(item) ? 'post-list-row post-list-row-current' : 'post-list-row'
)

const { t } = useI18n()

function isCurrentPost(item: Post): boolean {
  return String(item.postId) === String(props.currentPostId ?? '')
}

function getResolvedBoardUrl(item: Post): string {
  const raw = String(props.boardUrl || item.boardUrl || '').trim().toLowerCase()
  return raw.replace(/^\/+|\/+$/g, '')
}

function hasValidBoardTarget(item: Post): boolean {
  return getResolvedBoardUrl(item).length > 0
}

function getPostLink(item: Post): RouteLocationRaw {
  return {
    path: `/board/${getResolvedBoardUrl(item)}/post/${item.postId}`,
    query: props.linkQuery
  }
}

function isInquiryPost(item: Post): boolean {
  return getResolvedBoardUrl(item) === 'inquiry'
}

function shouldInterceptInquiry(item: Post): boolean {
  return props.interceptInquiry && isInquiryPost(item)
}

function hasInquiryStatus(item: Post): boolean {
  return isInquiryPost(item) && typeof item.inquiryAnswered === 'boolean'
}

function getInquiryStatusLabel(item: Post): string {
  return item.inquiryAnswered ? '답변완료' : '답변대기'
}

function getInteractiveTag(item: Post): 'button' | 'router-link' | 'div' {
  if (shouldInterceptInquiry(item)) return 'button'
  if (hasValidBoardTarget(item)) return 'router-link'
  return 'div'
}

function isAgentAuthor(item: Post): boolean {
  return item.author?.authorType === 'AGENT'
}

function handleSort(field: string) {
  const [currentField, currentDirection] = props.currentSort.split(',')
  let nextDirection = 'desc'

  if (field === currentField) {
    nextDirection = currentDirection === 'desc' ? 'asc' : 'desc'
  }

  emit('update:sort', `${field},${nextDirection}`)
}

function onPostClick(event: Event, item: Post) {
  if (shouldInterceptInquiry(item)) {
    event.preventDefault()
    emit('inquiry-click', item)
  }
}

function onTitleClick(event: Event, item: Post) {
  if (shouldInterceptInquiry(item)) {
    event.preventDefault()
    emit('inquiry-click', item)
  }
}

function onBoardClick(event: Event, item: Post) {
  if (shouldInterceptInquiry(item)) {
    event.preventDefault()
    emit('inquiry-click', item)
  }
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
      width: '14%',
      align: 'left' as const
    })
  }

  cols.push({
    key: 'title',
    label: t('common.title'),
    width: props.showBoardName ? '32%' : '46%',
    align: 'left' as const
  })

  cols.push({
    key: 'author',
    label: t('common.author'),
    width: '15%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'likeCount',
    label: t('common.likes'),
    width: '9%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'viewCount',
    label: t('common.views'),
    width: '9%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'createdAt',
    label: t('common.date'),
    width: '11%',
    align: 'center' as const,
    sortable: true
  })

  return cols
})
</script>

<template>
  <div class="card border-0 bg-transparent shadow-none">
    <div class="sm:hidden divide-y divide-[var(--nv-line)]">
      <template v-if="posts.length === 0">
        <div class="px-4 py-10 text-center text-xs text-[var(--nv-muted)]">
          {{ $t('board.list.noPosts') }}
        </div>
      </template>

      <component
        v-for="item in posts"
        :is="getInteractiveTag(item)"
        :key="item.postId"
        :to="getInteractiveTag(item) === 'router-link' ? getPostLink(item) : undefined"
        :type="getInteractiveTag(item) === 'button' ? 'button' : undefined"
        :aria-disabled="getInteractiveTag(item) === 'div' ? 'true' : undefined"
        :class="[
          'nv-post-card block w-full px-4 py-4 text-left transition-colors',
          item.isNotice ? 'nv-post-card-notice' : '',
          isCurrentPost(item) ? 'nv-post-card-current' : '',
          getInteractiveTag(item) === 'div' ? 'cursor-not-allowed opacity-70' : ''
        ]"
        @click="onPostClick($event, item)"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <span
                v-if="item.category && !isGeneralCategory(item.category.name)"
                class="badge badge-gray !mr-0 text-[10px]"
              >
                {{ item.category.name }}
              </span>
              <span v-if="item.isNotice" class="badge badge-red !mr-0 text-[10px]">
                {{ $t('common.notice') }}
              </span>
              <span
                v-if="hasInquiryStatus(item)"
                class="rounded-full px-2 py-0.5 text-[10px] font-semibold"
                :class="item.inquiryAnswered ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'"
              >
                {{ getInquiryStatusLabel(item) }}
              </span>
            </div>

            <div class="mt-2 flex items-start gap-1.5 text-sm font-medium text-[var(--nv-ink)]">
              <span
                v-if="item.isSecret"
                class="mt-0.5 inline-flex flex-shrink-0 text-amber-500"
                :title="$t('board.writePost.secret')"
              >
                <Lock class="h-4 w-4" />
              </span>
              <span v-if="item.hasImage" class="mt-0.5 inline-flex flex-shrink-0 text-[var(--nv-muted)]">
                <ImageIcon class="h-4 w-4" />
              </span>
              <span class="min-w-0 flex-1 break-words">
                {{ item.title }}
                <span v-if="item.commentCount > 0" class="ml-1 text-indigo-600 dark:text-indigo-400">
                  [{{ item.commentCount }}]
                </span>
              </span>
            </div>
          </div>

          <span class="mt-0.5 flex-shrink-0 text-[11px] text-[var(--nv-muted)]">
            {{ formatRelativeDate(item.createdAt) }}
          </span>
        </div>

        <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-[11px] text-[var(--nv-ink-soft)]">
          <span class="inline-flex items-center gap-1">
            <User class="h-3.5 w-3.5" />
            <span>{{ item.author?.displayName || '-' }}</span>
            <span
              v-if="isAgentAuthor(item)"
              class="rounded-full bg-sky-100 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
            >
              AGENT
            </span>
          </span>
          <span class="inline-flex items-center gap-1">
            <ThumbsUp class="h-3.5 w-3.5" />
            {{ item.likeCount }}
          </span>
          <span class="inline-flex items-center gap-1">
            <Eye class="h-3.5 w-3.5" />
            {{ item.viewCount }}
          </span>
        </div>
      </component>
    </div>

    <div class="hidden sm:block table-container">
      <BaseTable
        :columns="columns"
        :items="posts"
        :emptyText="$t('board.list.noPosts')"
        :rowClass="getRowClass"
        @sort="handleSort"
      >
        <template #cell-postId="{ item }">
          <span v-if="item.isNotice" class="font-semibold text-red-600 dark:text-red-400">
            {{ $t('common.notice') }}
          </span>
          <span v-else>{{ item.rowNum }}</span>
        </template>

        <template #cell-boardName="{ item }">
          <button
            v-if="shouldInterceptInquiry(item)"
            type="button"
            class="truncate text-left text-[var(--nv-ink-soft)] hover:text-[var(--nv-accent)]"
            @click="onBoardClick($event, item)"
          >
            {{ item.boardName || '-' }}
          </button>
          <router-link
            v-else-if="hasValidBoardTarget(item)"
            :to="`/board/${getResolvedBoardUrl(item)}`"
            class="truncate text-[var(--nv-ink-soft)] hover:text-[var(--nv-accent)]"
            @click="onBoardClick($event, item)"
          >
            {{ item.boardName || '-' }}
          </router-link>
          <span v-else class="truncate text-[var(--nv-muted)]">{{ item.boardName || '-' }}</span>
        </template>

        <template #cell-title="{ item }">
          <div class="flex min-w-0 items-center gap-1.5 overflow-hidden">
            <button
              v-if="shouldInterceptInquiry(item)"
              type="button"
              class="flex min-w-0 flex-1 items-center gap-1.5 overflow-hidden text-left hover:text-[var(--nv-accent)]"
              @click="onTitleClick($event, item)"
            >
              <span
                v-if="item.category && !isGeneralCategory(item.category.name)"
                class="badge badge-gray !mr-0 flex-shrink-0 text-[10px]"
              >
                {{ item.category.name }}
              </span>
              <span v-if="item.isNotice" class="badge badge-red !mr-0 flex-shrink-0 text-[10px]">
                {{ $t('common.notice') }}
              </span>
              <span
                v-if="item.isSecret"
                class="inline-flex flex-shrink-0 text-amber-500"
                :title="$t('board.writePost.secret')"
              >
                <Lock class="h-4 w-4" />
              </span>
              <span v-if="item.hasImage" class="inline-flex flex-shrink-0 text-[var(--nv-muted)]">
                <ImageIcon class="h-4 w-4" />
              </span>
              <span
                v-if="hasInquiryStatus(item)"
                class="rounded-full px-2 py-0.5 text-[10px] font-semibold flex-shrink-0"
                :class="item.inquiryAnswered ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'"
              >
                {{ getInquiryStatusLabel(item) }}
              </span>
              <span class="truncate">{{ item.title }}</span>
              <span v-if="item.commentCount > 0" class="flex-shrink-0 text-indigo-600 dark:text-indigo-400">
                [{{ item.commentCount }}]
              </span>
            </button>

            <router-link
              v-else-if="hasValidBoardTarget(item)"
              :to="getPostLink(item)"
              class="flex min-w-0 flex-1 items-center gap-1.5 overflow-hidden hover:text-[var(--nv-accent)]"
              @click="onTitleClick($event, item)"
            >
              <span
                v-if="item.category && !isGeneralCategory(item.category.name)"
                class="badge badge-gray !mr-0 flex-shrink-0 text-[10px]"
              >
                {{ item.category.name }}
              </span>
              <span v-if="item.isNotice" class="badge badge-red !mr-0 flex-shrink-0 text-[10px]">
                {{ $t('common.notice') }}
              </span>
              <span
                v-if="item.isSecret"
                class="inline-flex flex-shrink-0 text-amber-500"
                :title="$t('board.writePost.secret')"
              >
                <Lock class="h-4 w-4" />
              </span>
              <span v-if="item.hasImage" class="inline-flex flex-shrink-0 text-[var(--nv-muted)]">
                <ImageIcon class="h-4 w-4" />
              </span>
              <span
                v-if="hasInquiryStatus(item)"
                class="rounded-full px-2 py-0.5 text-[10px] font-semibold flex-shrink-0"
                :class="item.inquiryAnswered ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'"
              >
                {{ getInquiryStatusLabel(item) }}
              </span>
              <span class="truncate">{{ item.title }}</span>
              <span v-if="item.commentCount > 0" class="flex-shrink-0 text-indigo-600 dark:text-indigo-400">
                [{{ item.commentCount }}]
              </span>
            </router-link>

            <span v-else class="truncate text-[var(--nv-muted)]" :title="$t('board.invalidUrl')">
              {{ item.title }}
            </span>
          </div>
        </template>

        <template #cell-author="{ item }">
          <span class="inline-flex min-w-0 max-w-full items-center gap-1 overflow-hidden">
            <UserMenu
              v-if="item.author"
              :user-id="item.author.userId"
              :display-name="item.author.displayName"
            />
            <span v-else>-</span>
            <span
              v-if="isAgentAuthor(item)"
              class="rounded-full bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
            >
              AGENT
            </span>
          </span>
        </template>

        <template #cell-likeCount="{ item }">
          <span class="inline-flex items-center justify-center gap-1">
            <ThumbsUp class="h-3.5 w-3.5 text-[var(--nv-muted)]" />
            {{ item.likeCount }}
          </span>
        </template>

        <template #cell-viewCount="{ item }">
          <span class="inline-flex items-center justify-center gap-1">
            <Eye class="h-3.5 w-3.5 text-[var(--nv-muted)]" />
            {{ item.viewCount }}
          </span>
        </template>

        <template #cell-createdAt="{ item }">
          <span class="inline-flex items-center justify-center gap-1">
            <Clock class="h-3.5 w-3.5 text-[var(--nv-muted)]" />
            {{ formatRelativeDate(item.createdAt) }}
          </span>
        </template>
      </BaseTable>
    </div>
  </div>
</template>

<style scoped>
:deep(.post-list-row td) {
  transition: background-color 0.15s ease, padding-left 0.15s ease, border-color 0.15s ease;
}

:deep(.post-list-row td:first-child) {
  border-left: 3px solid transparent;
  padding-left: calc(1.5rem - 3px);
}

:deep(.post-list-row:hover td:first-child) {
  border-left-color: var(--nv-accent);
  padding-left: 1.5rem;
}

:deep(.post-list-row-current td:first-child) {
  border-left-color: var(--nv-accent);
  padding-left: 1.5rem;
}

:deep(.post-list-row:hover td) {
  background: color-mix(in srgb, var(--nv-surface-2) 40%, transparent);
}
</style>

<style scoped>
.nv-post-card {
  background: transparent;
}

.nv-post-card:hover {
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
}

.nv-post-card-notice {
  background: color-mix(in srgb, var(--nv-surface-2) 86%, transparent);
}

.nv-post-card-current {
  background: color-mix(in srgb, var(--nv-accent-bg) 86%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--nv-accent) 24%, transparent);
}

:deep(.table-container > div) {
  background: transparent;
  border: 0;
  box-shadow: none;
  border-radius: 0;
}

:deep(thead) {
  background: color-mix(in srgb, var(--nv-surface-2) 65%, transparent);
}

:deep(th) {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
}

:deep(tbody) {
  background: transparent;
}

:deep(tr:hover td) {
  background: color-mix(in srgb, var(--nv-surface-2) 70%, transparent);
}

:deep(.post-list-row-current td) {
  background: color-mix(in srgb, var(--nv-accent-bg) 82%, transparent);
}

:deep(.post-list-row-current td:first-child) {
  border-left: 4px solid var(--nv-accent);
}
</style>
