<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useUser } from '@/composables/useUser'
import { useApiQuery } from '@/composables/useApiQuery'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { useConfirm } from '@/composables/useConfirm'
import { userApi } from '@/api/user'
import { userQueryKeys } from '@/composables/userQueryKeys'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { Bookmark, Check, Pencil, X } from 'lucide-vue-next'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@/types'

const { t } = useI18n()
const { confirm } = useConfirm()
const queryClient = useQueryClient()
const { useMyScraps } = useUser()
const selectedFolderId = ref<number | null>(null)
const searchInput = ref('')
const appliedSearch = ref('')
const newFolderName = ref('')
const creatingFolder = ref(false)
const deletingFolderId = ref<number | null>(null)
const editingFolderId = ref<number | null>(null)
const editingFolderName = ref('')
const updatingFolderId = ref<number | null>(null)
const folderActionError = ref('')

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
const {
  data: folderData,
  isError: isFolderError,
  isFetching: isFolderFetching,
  refetch: refetchFolders,
} = useApiQuery({
  queryKey: userQueryKeys.scrapFolders,
  request: ({ signal }) => userApi.getScrapFolders({ signal }),
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

function startEditFolder(folderId: number, name: string) {
  editingFolderId.value = folderId
  editingFolderName.value = name
}

function cancelEditFolder() {
  editingFolderId.value = null
  editingFolderName.value = ''
}

async function updateFolder(folderId: number) {
  const name = editingFolderName.value.trim()
  if (!name || updatingFolderId.value !== null) return
  updatingFolderId.value = folderId
  try {
    await userApi.updateScrapFolder(folderId, { name })
    cancelEditFolder()
    await refetchFolders()
  } finally {
    updatingFolderId.value = null
  }
}

async function getFolderScrapCount(folderId: number) {
  const { data } = await userApi.getMyScraps({ folderId, page: 0, size: 1 })
  return data.data.totalElements
}

async function deleteFolder(folderId: number) {
  if (deletingFolderId.value !== null) return
  const folder = folders.value.find((item) => item.folderId === folderId)
  if (!folder) return

  deletingFolderId.value = folderId
  folderActionError.value = ''
  try {
    const scrapCount = await getFolderScrapCount(folderId)
    const confirmed = await confirm(
      t('user.scrapList.deleteConfirmMessage', { name: folder.name, count: scrapCount }),
      t('user.scrapList.deleteConfirmTitle'),
      t('common.delete'),
      t('common.cancel')
    )
    if (!confirmed) return

    await userApi.deleteScrapFolder(folderId)
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
    await refetchFolders()
    await queryClient.invalidateQueries({ queryKey: ['user', 'scraps'] })
  } catch {
    folderActionError.value = t('user.scrapList.folderActionFailed')
  } finally {
    deletingFolderId.value = null
  }
}
</script>

<template>
  <PaginatedListCard
    title-tag="h1"
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
        <ErrorState
          v-if="isFolderError"
          title-tag="h2"
          :message="$t('common.messages.loadFailed')"
          :show-icon="false"
          auto-focus
          show-retry
          @retry="refetchFolders()"
        />
        <div v-else class="flex flex-wrap gap-2" :aria-busy="isFolderFetching">
          <button
            type="button"
            class="min-h-11 rounded-full border px-3 py-1.5 text-sm sm:min-h-0"
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
            <template v-if="editingFolderId === folder.folderId">
              <BaseInput
                :id="`scrap-folder-edit-${folder.folderId}`"
                v-model="editingFolderName"
                :label="$t('user.scrapList.renameFolder')"
                inputClass="h-8 w-36 rounded-full text-sm"
                hideLabel
                :disabled="updatingFolderId === folder.folderId"
                @keydown.enter.prevent="updateFolder(folder.folderId)"
                @keydown.esc.prevent="cancelEditFolder"
              />
              <button
                type="button"
                class="inline-flex h-11 w-11 items-center justify-center rounded-full nv-text-subtle nv-hover-accent sm:h-8 sm:w-8"
                :aria-label="$t('user.scrapList.saveFolder', { name: folder.name })"
                :disabled="updatingFolderId === folder.folderId || !editingFolderName.trim()"
                @click="updateFolder(folder.folderId)"
              >
                <Check class="h-4 w-4" />
              </button>
              <button
                type="button"
                class="inline-flex h-11 w-11 items-center justify-center rounded-full nv-text-subtle nv-hover-surface sm:h-8 sm:w-8"
                :aria-label="$t('user.scrapList.cancelEditFolder')"
                :disabled="updatingFolderId === folder.folderId"
                @click="cancelEditFolder"
              >
                <X class="h-4 w-4" />
              </button>
            </template>
            <template v-else>
              <button
                type="button"
                class="min-h-11 px-3 py-1.5 text-sm nv-text sm:min-h-0"
                @click="selectedFolderId = folder.folderId"
              >
                {{ folder.name }}
              </button>
              <button
                type="button"
                class="inline-flex h-11 w-11 items-center justify-center rounded-full nv-text-subtle nv-hover-accent sm:h-8 sm:w-8"
                :aria-label="$t('user.scrapList.editFolder', { name: folder.name })"
                @click="startEditFolder(folder.folderId, folder.name)"
              >
                <Pencil class="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                class="inline-flex h-11 w-11 items-center justify-center rounded-full nv-text-subtle nv-hover-danger sm:h-8 sm:w-8"
                :aria-label="$t('user.scrapList.deleteFolder', { name: folder.name })"
                :disabled="deletingFolderId === folder.folderId"
                @click="deleteFolder(folder.folderId)"
              >
                <X class="h-3.5 w-3.5" />
              </button>
            </template>
          </span>
        </div>
        <p v-if="folderActionError" class="nv-form-error text-sm" role="alert" aria-live="assertive">
          {{ folderActionError }}
        </p>
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
