<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/composables/useBoard'
import SubscribedBoardList from '@/components/board/SubscribedBoardList.vue'
import BoardGrid from '@/components/board/BoardGrid.vue'
import BoardListSkeleton from '@/components/common/ui/BoardListSkeleton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useI18n } from 'vue-i18n'

import { useHead } from '@unhead/vue'

const authStore = useAuthStore()
const { useBoards } = useBoard()
const { data: boards, isLoading, error, refetch } = useBoards()
const { t } = useI18n()
const boardSearch = ref('')
const boardSort = ref('default')

useHead({
  title: computed(() => t('board.list.title')),
  meta: [
    { name: 'description', content: computed(() => t('board.seo.allBoardsDescription')) },
    { property: 'og:title', content: computed(() => `${t('board.list.title')} - ${t('common.appName')}`) },
    { property: 'og:description', content: computed(() => t('board.seo.allBoardsDescription')) }
  ]
})

const subscribedBoards = computed(() => {
  return boards.value?.filter(board => board.isSubscribed) || []
})

const allBoards = computed(() => {
  return boards.value || []
})

const sortOptions = computed(() => [
  { value: 'default', label: t('board.list.sortDefault') },
  { value: 'name', label: t('board.list.sortName') },
  { value: 'popular', label: t('board.list.sortPopular') },
  { value: 'posts', label: t('board.list.sortPosts') },
])

const filteredBoards = computed(() => {
  const normalizedKeyword = boardSearch.value.trim().toLocaleLowerCase()

  return [...allBoards.value]
    .filter((board) => {
      const matchesKeyword = !normalizedKeyword || [
        board.boardName,
        board.description,
        board.adminDisplayName,
      ].some((value) => value?.toLocaleLowerCase().includes(normalizedKeyword))

      return matchesKeyword
    })
    .sort((a, b) => {
      switch (boardSort.value) {
        case 'name':
          return a.boardName.localeCompare(b.boardName, 'ko')
        case 'popular':
          return b.subscriberCount - a.subscriberCount
        case 'posts':
          return b.postCount - a.postCount
        default:
          return a.sortOrder - b.sortOrder
      }
    })
})
</script>

<template>
  <div class="mx-auto max-w-7xl">
    <header class="mb-8">
      <h1 class="text-2xl font-bold nv-title">
        {{ $t('board.list.title') }}
      </h1>
    </header>

    <BoardListSkeleton v-if="isLoading" :count="6" :show-subscribed="authStore.isAuthenticated" />

    <ErrorState
      v-else-if="error"
      title-tag="h2"
      :title="$t('common.messages.defaultTitle')"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetch()"
    />

    <div v-else>
      <!-- Subscribed Boards -->
      <SubscribedBoardList v-if="authStore.isAuthenticated && subscribedBoards.length > 0" :boards="subscribedBoards" />

      <!-- All Boards -->
      <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 class="text-xl font-bold nv-title"
            v-if="authStore.isAuthenticated && subscribedBoards.length > 0">{{ $t('board.list.title') }}</h2>
          <p class="mt-1 text-sm nv-text-subtle">
            {{ $t('board.list.resultCount', { count: filteredBoards.length }) }}
          </p>
        </div>
        <div class="grid gap-3 sm:grid-cols-[minmax(9rem,14rem)_minmax(8rem,10rem)] sm:items-end">
          <BaseInput
            v-model="boardSearch"
            :label="$t('board.list.searchLabel')"
            :placeholder="$t('board.list.searchPlaceholder')"
            input-class="min-h-[44px]"
          />
          <BaseSelect
            v-model="boardSort"
            :label="$t('board.list.sortLabel')"
            :options="sortOptions"
          />
        </div>
      </div>
      <BoardGrid :boards="filteredBoards" />
    </div>
  </div>
</template>
