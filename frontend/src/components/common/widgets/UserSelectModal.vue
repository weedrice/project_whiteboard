<template>
  <BaseModal
    :isOpen="isOpen"
    size="2xl"
    :title="modalTitle"
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
          <Search class="h-4 w-4 nv-text-subtle" />
        </template>
      </BaseInput>

      <div class="rounded-xl border nv-border overflow-hidden">
        <div class="flex items-center justify-between border-b nv-border nv-surface-muted px-3 py-2">
          <p class="text-xs font-medium nv-text-muted">
            {{ isMultiMode ? $t('user.selectModal.multiMode') : $t('user.selectModal.singleMode') }}
          </p>
          <p class="text-xs nv-text-subtle">
            {{ selectedUsers.length > 0 ? $t('user.selectModal.selectedCount', { count: selectedUsers.length }) : $t('user.selectModal.selectedEmpty') }}
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
          interactive-rows
          :row-action-label="getUserRowActionLabel"
          @row-click="toggleSelection"
        >
          <template #loading>
            <BaseSpinner />
          </template>

          <template #cell-selection="{ item }">
            <span
              class="inline-flex h-4 w-4 items-center justify-center rounded border text-[10px]"
              :class="isSelected(item.userId)
                ? 'user-select-check-selected'
                : 'nv-border-strong text-transparent'"
            >
              <Check class="h-3 w-3" />
            </span>
          </template>

          <template #cell-displayName="{ item }">
            <span class="inline-flex items-center gap-2">
              <span>{{ item.displayName }}</span>
              <span
                v-if="item.currentManager"
                class="rounded nv-status-info px-1.5 py-0.5 text-xs font-medium"
              >
                {{ $t('user.selectModal.currentManager') }}
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
import BaseTable from '@/components/common/ui/BaseTable.vue'
import {
  useUserSelectSource,
  type SelectableUser,
  type SelectionMode,
  type UserSelectSource
} from '@/composables/useUserSelectSource'
import { useDebounce } from '@/composables/useDebounce'

const props = withDefaults(defineProps<{
  isOpen: boolean
  title?: string
  selectionMode?: SelectionMode
  source?: UserSelectSource
  boardUrl?: string
  initialSelectedIds?: number[]
  excludeUserIds?: number[]
}>(), {
  selectionMode: 'single',
  source: 'admin',
  boardUrl: '',
  initialSelectedIds: () => [],
  excludeUserIds: () => []
})
const { t } = useI18n()
const modalTitle = computed(() => props.title ?? t('user.selectModal.title'))

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', users: SelectableUser[]): void
}>()

const searchQuery = ref('')
const debouncedSearchQuery = useDebounce(searchQuery, 300)
const selectedMap = ref<Record<number, SelectableUser>>({})
const hasUserSelectionChanged = ref(false)
const hasAppliedInitialSelection = ref(false)

const {
  filteredUsers,
  isLoading,
  isMultiMode,
  userColumns,
} = useUserSelectSource({
  isOpen: computed(() => props.isOpen),
  selectionMode: computed(() => props.selectionMode),
  source: computed(() => props.source),
  boardUrl: computed(() => props.boardUrl),
  excludeUserIds: computed(() => props.excludeUserIds),
  searchQuery: debouncedSearchQuery,
})

const selectedUsers = computed<SelectableUser[]>(() => Object.values(selectedMap.value))

function isSelected(userId: number) {
  return !!selectedMap.value[userId]
}

function getUserRowClass(user: SelectableUser) {
  return isSelected(user.userId)
    ? 'cursor-pointer !bg-[var(--nv-selection)]'
    : 'cursor-pointer'
}

function getUserRowActionLabel(user: SelectableUser) {
  return t('user.selectModal.selectUser', { name: user.displayName })
}

function toggleSelection(user: SelectableUser) {
  hasUserSelectionChanged.value = true

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

function applyInitialSelection(users: SelectableUser[]) {
  if (
    hasUserSelectionChanged.value
    || hasAppliedInitialSelection.value
    || !users.length
    || !props.initialSelectedIds.length
  ) {
    return
  }

  const initialSet = new Set(props.initialSelectedIds)
  const matched = users.filter((user) => initialSet.has(user.userId))

  if (!matched.length) return

  hasAppliedInitialSelection.value = true

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
}

watch([
  () => props.isOpen,
  () => props.selectionMode,
  () => props.initialSelectedIds,
  () => props.source,
  () => props.boardUrl,
  () => props.excludeUserIds,
], ([isOpen]) => {
  if (!isOpen) return

  searchQuery.value = ''
  selectedMap.value = {}
  hasUserSelectionChanged.value = false
  hasAppliedInitialSelection.value = false
  applyInitialSelection(filteredUsers.value)
})

watch(filteredUsers, (users) => {
  applyInitialSelection(users)
}, { immediate: true })
</script>

<style scoped>
.user-select-check-selected {
  background: var(--nv-accent);
  border-color: var(--nv-accent);
  color: #fff;
}
</style>
