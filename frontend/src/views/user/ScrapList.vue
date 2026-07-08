<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useUser } from '@/composables/useUser'
import { useApiQuery } from '@/composables/useApiQuery'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { userApi } from '@/api/user'
import { userQueryKeys } from '@/composables/userQueryKeys'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { Bookmark } from 'lucide-vue-next'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@/types'

const { t } = useI18n()
const queryClient = useQueryClient()
const { useMyScraps } = useUser()
const selectedFolderId = ref<number | null>(null)
const searchInput = ref('')
const appliedSearch = ref('')
const newFolderName = ref('')
const creatingFolder = ref(false)
const deletingFolderId = ref<number | null>(null)

const {
  page,
  size,
  params: paginationParams,
  handlePageChange,
  handleSizeChange,
  resetPage,
} = usePaginatedQueryState({ initialSize: 15 })

const scrapParams = computed(() => ({
  ...paginationParams.value,
  ...(selectedFolderId.value ? { folderId: selectedFolderId.value } : {}),
  ...(appliedSearch.value ? { q: appliedSearch.value } : {}),
}))

const scrapQuery = useMyScraps(scrapParams)
const scrapPage = computed(() => scrapQuery.data.value ?? null)
const pageState = usePageResponseState<PostSummary>(scrapPage, page)
const { data: folderData, refetch: refetchFolders } = useApiQuery({
  queryKey: userQueryKeys.scrapFolders,
  request: () => userApi.getScrapFolders(),
  staleTime: 60_000,
})

const scraps = computed(() => pageState.items.value)
const totalPages = computed(() => pageState.totalPages.value)
const folders = computed(() => folderData.value || [])
const loading = computed(() => scrapQuery.isLoading.value)
const errorMessage = computed(() => scrapQuery.isError.value ? t('common.messages.loadFailed') : '')

watch(selectedFolderId, () => {
  resetPage()
})

function applySearch() {
  appliedSearch.value = searchInput.value.trim()
  resetPage()
}

function clearSearch() {
  searchInput.value = ''
  appliedSearch.value = ''
  resetPage()
}

async function createFolder() {
  const name = newFolderName.value.trim()
  if (!name || creatingFolder.value) return
  creatingFolder.value = true
  try {
    await userApi.createScrapFolder({ name })
    newFolderName.value = ''
    await refetchFolders()
  } finally {
    creatingFolder.value = false
  }
}

async function deleteFolder(folderId: number) {
  if (deletingFolderId.value !== null) return
  deletingFolderId.value = folderId
  try {
    await userApi.deleteScrapFolder(folderId)
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
    await refetchFolders()
    await queryClient.invalidateQueries({ queryKey: ['user', 'scraps'] })
  } finally {
    deletingFolderId.value = null
  }
}
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.scraps')"
    :icon="Bookmark"
    :items-count="scraps.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.scrapList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    loading-preset="post-list"
    @retry="scrapQuery.refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-actions>
      <form class="flex w-full flex-col gap-2 sm:w-auto sm:flex-row" @submit.prevent="createFolder">
        <BaseInput
          id="scrap-folder-name"
          v-model="newFolderName"
          :label="$t('user.scrapList.newFolder')"
          :placeholder="$t('user.scrapList.newFolder')"
          inputClass="h-9"
          hideLabel
        />
        <BaseButton type="submit" size="sm" :disabled="!newFolderName.trim() || creatingFolder">
          {{ $t('common.add') }}
        </BaseButton>
      </form>
    </template>

    <template #subheader>
      <div class="space-y-3">
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="rounded-full border px-3 py-1.5 text-sm"
            :class="selectedFolderId === null ? 'border-[var(--nv-accent)] nv-accent-bg nv-accent-text' : 'nv-border nv-hover-surface'"
            @click="selectedFolderId = null"
          >
            {{ $t('user.scrapList.allFolder') }}
          </button>
          <span
            v-for="folder in folders"
            :key="folder.folderId"
            class="inline-flex items-center gap-1 rounded-full border nv-border"
            :class="selectedFolderId === folder.folderId ? 'border-[var(--nv-accent)] nv-accent-bg' : 'nv-surface'"
          >
            <button
              type="button"
              class="px-3 py-1.5 text-sm nv-text"
              @click="selectedFolderId = folder.folderId"
            >
              {{ folder.name }}
            </button>
            <button
              type="button"
              class="pr-2 text-xs nv-text-subtle nv-hover-danger"
              :aria-label="$t('user.scrapList.deleteFolder', { name: folder.name })"
              :disabled="deletingFolderId === folder.folderId"
              @click="deleteFolder(folder.folderId)"
            >
              x
            </button>
          </span>
        </div>
        <form class="flex flex-col gap-2 sm:flex-row" @submit.prevent="applySearch">
          <BaseInput
            id="scrap-search"
            v-model="searchInput"
            :label="$t('user.scrapList.search')"
            :placeholder="$t('user.scrapList.search')"
            inputClass="h-10"
            hideLabel
          />
          <BaseButton type="submit" variant="secondary" class="h-10">
            {{ $t('user.scrapList.searchButton') }}
          </BaseButton>
          <BaseButton v-if="appliedSearch" type="button" variant="secondary" class="h-10" @click="clearSearch">
            {{ $t('common.reset') }}
          </BaseButton>
        </form>
      </div>
    </template>

    <PostList
      :posts="scraps"
      :show-board-name="true"
      :hide-no-column="true"
      :show-notice-badge="false"
      :show-comment-count="false"
      :show-preview-indicator="false"
      :show-secret-indicator="false"
      :resolve-post-route="resolvePostDetailRoute"
      :resolve-board-route="resolveBoardRoute"
      :show-inquiry-status="isInquiryPostItem"
    />
  </PaginatedListCard>
</template>
