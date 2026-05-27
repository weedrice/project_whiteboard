<template>
  <BaseModal
    :isOpen="isOpen"
    size="2xl"
    :title="title"
    @close="emit('close')"
  >
    <div class="p-4 space-y-4">
      <BaseInput
        id="user-select-search"
        v-model="searchQuery"
        name="userSelectSearch"
        autocomplete="off"
        :label="$t('admin.users.searchPlaceholder')"
        :placeholder="$t('admin.users.searchPlaceholder')"
        hideLabel
      >
        <template #prefix>
          <Search class="h-4 w-4 text-gray-400" />
        </template>
      </BaseInput>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="flex items-center justify-between border-b border-gray-200 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800">
          <p class="text-xs font-medium text-gray-600 dark:text-gray-300">
            {{ isMultiMode ? '멀티 선택 모드' : '단일 선택 모드' }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ selectedUsers.length > 0 ? `선택 ${selectedUsers.length}명` : '선택된 사용자 없음' }}
          </p>
        </div>

        <BaseTable
          :columns="userColumns"
          :items="filteredUsers"
          :loading="isLoading"
          :empty-text="$t('common.noData')"
          density="compact"
          :shadow="false"
          max-height-class="max-h-[420px]"
          row-key="userId"
          :row-class="getUserRowClass"
          @row-click="toggleSelection"
        >
          <template #loading>
            <BaseSpinner />
          </template>

          <template #cell-selection="{ item }">
            <span
              class="inline-flex h-4 w-4 items-center justify-center rounded border text-[10px]"
              :class="isSelected(item.userId)
                ? 'border-indigo-500 bg-indigo-500 text-white'
                : 'border-gray-300 text-transparent dark:border-gray-600'"
            >
              <Check class="h-3 w-3" />
            </span>
          </template>

          <template #cell-displayName="{ item }">
            <span class="inline-flex items-center gap-2">
              <span>{{ item.displayName }}</span>
              <span
                v-if="item.currentManager"
                class="rounded bg-indigo-50 px-1.5 py-0.5 text-[11px] font-medium text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-200"
              >
                current
              </span>
            </span>
          </template>
        </BaseTable>
      </div>

      <div class="flex items-center justify-end gap-2">
          <BaseButton variant="secondary" @click="emit('close')">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton :disabled="selectedUsers.length === 0" @click="confirmSelection">{{ $t('common.save') }}</BaseButton>
      </div>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Check } from 'lucide-vue-next'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
import { useAdmin } from '@/composables/useAdmin'
import { useBoard } from '@/composables/useBoard'

type SelectionMode = 'single' | 'multiple'
type UserSelectSource = 'admin' | 'board-manager-candidates'

interface SelectableUser {
  userId: number
  loginId: string
  displayName: string
  profileImageUrl?: string | null
  email?: string
  currentManager?: boolean
}

const props = withDefaults(defineProps<{
  isOpen: boolean
  title?: string
  selectionMode?: SelectionMode
  source?: UserSelectSource
  boardUrl?: string
  initialSelectedIds?: number[]
  excludeUserIds?: number[]
}>(), {
  title: '사용자 선택',
  selectionMode: 'single',
  source: 'admin',
  boardUrl: '',
  initialSelectedIds: () => [],
  excludeUserIds: () => []
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', users: SelectableUser[]): void
}>()

const { t } = useI18n()
const { useUsers } = useAdmin()
const { useBoardManagerCandidates } = useBoard()

const searchQuery = ref('')
const selectedMap = ref<Record<number, SelectableUser>>({})

const params = computed(() => ({
  page: 0,
  size: 10,
  q: searchQuery.value
}))
const boardUrlRef = computed(() => props.boardUrl)
const isBoardCandidateSource = computed(() => props.source === 'board-manager-candidates')
const isUsersQueryEnabled = computed(() => props.isOpen && props.source === 'admin')
const isBoardCandidatesQueryEnabled = computed(() => props.isOpen && isBoardCandidateSource.value && !!props.boardUrl)

const { data: adminUsersData, isLoading: isAdminUsersLoading } = useUsers(params, isUsersQueryEnabled)
const { data: boardCandidatesData, isLoading: isBoardCandidatesLoading } = useBoardManagerCandidates(
  boardUrlRef,
  params,
  isBoardCandidatesQueryEnabled
)
const isMultiMode = computed(() => props.selectionMode === 'multiple')
const showEmailColumn = computed(() => !isBoardCandidateSource.value)
const usersData = computed(() => (isBoardCandidateSource.value ? boardCandidatesData.value : adminUsersData.value))
const isLoading = computed(() => (isBoardCandidateSource.value ? isBoardCandidatesLoading.value : isAdminUsersLoading.value))
const userColumns = computed<TableColumn[]>(() => {
  const columns: TableColumn[] = []

  if (isMultiMode.value) {
    columns.push({ key: 'selection', label: '', width: '2.5rem', align: 'center' })
  }

  columns.push({ key: 'loginId', label: t('common.loginId') })
  columns.push({ key: 'displayName', label: t('common.displayName') })

  if (showEmailColumn.value) {
    columns.push({ key: 'email', label: t('common.email') })
  }

  return columns
})

const filteredUsers = computed<SelectableUser[]>(() => {
  const list = usersData.value?.content || []
  if (!props.excludeUserIds.length) return list
  const excluded = new Set(props.excludeUserIds)
  return list.filter((user) => !excluded.has(user.userId))
})

const selectedUsers = computed<SelectableUser[]>(() => Object.values(selectedMap.value))

function isSelected(userId: number) {
  return !!selectedMap.value[userId]
}

function getUserRowClass(user: SelectableUser) {
  return isSelected(user.userId)
    ? 'cursor-pointer !bg-indigo-50 dark:!bg-indigo-900/20'
    : 'cursor-pointer'
}

function toggleSelection(user: SelectableUser) {
  if (props.selectionMode === 'single') {
    selectedMap.value = { [user.userId]: user }
    return
  }

  if (selectedMap.value[user.userId]) {
    const next = { ...selectedMap.value }
    delete next[user.userId]
    selectedMap.value = next
    return
  }

  selectedMap.value = { ...selectedMap.value, [user.userId]: user }
}

function confirmSelection() {
  emit('confirm', selectedUsers.value)
}

watch([() => props.isOpen, () => props.selectionMode, () => props.initialSelectedIds], ([isOpen]) => {
  if (!isOpen) return

  searchQuery.value = ''
  selectedMap.value = {}
})

watch(filteredUsers, (users) => {
  if (!users.length || !props.initialSelectedIds.length) return

  const initialSet = new Set(props.initialSelectedIds)
  const matched = users.filter((user) => initialSet.has(user.userId))

  if (!matched.length) return

  if (props.selectionMode === 'single') {
    const first = matched[0]
    selectedMap.value = { [first.userId]: first }
    return
  }

  const next: Record<number, SelectableUser> = {}
  matched.forEach((user) => {
    next[user.userId] = user
  })
  selectedMap.value = next
}, { immediate: true })
</script>
