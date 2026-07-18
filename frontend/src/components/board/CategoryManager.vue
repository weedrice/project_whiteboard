<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { Trash2, Edit2, Check, X, Plus, GripVertical, ChevronUp, ChevronDown } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useBoardCategoriesManager } from '@/features/board/categories/useBoardCategoriesManager'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  boardUrl: string
  headingTag?: 'h2' | 'h3' | 'h4'
}>(), {
  headingTag: 'h3',
})

const roles = computed(() => [
  { value: 'USER', label: t('admin.users.role.USER') },
  { value: 'BOARD_ADMIN', label: t('admin.users.role.BOARD_ADMIN') },
  { value: 'SUPER_ADMIN', label: t('admin.users.role.SUPER_ADMIN') }
])

const {
  categories,
  isLoading,
  newCategoryName,
  newCategoryRole,
  editingId,
  editingName,
  editingRole,
  error,
  isReordering,
  isMutating,
  defaultCategory,
  draggableCategories,
  fetchCategories,
  resetState,
  handleAdd,
  handleDelete,
  startEdit,
  cancelEdit,
  saveEdit,
  onDragStart,
  onDrop,
  moveCategory,
} = useBoardCategoriesManager(toRef(props, 'boardUrl'))

const reorderAnnouncement = ref('')

const handleKeyboardMove = async (index: number, offset: -1 | 1, name: string) => {
  if (await moveCategory(index, offset)) {
    reorderAnnouncement.value = t('common.moved', { name, position: index + offset + 1 })
  }
}

watch(() => props.boardUrl, () => {
  resetState()
  void fetchCategories()
}, { immediate: true })
</script>

<template>
  <div class="space-y-4" :aria-busy="isLoading || isReordering || isMutating">
    <component :is="headingTag" class="text-lg font-medium leading-6 nv-title">{{ $t('common.category') }}</component>
    <p class="sr-only" role="status" aria-live="polite">{{ reorderAnnouncement }}</p>

    <!-- Add Category -->
    <form @submit.prevent="handleAdd" class="flex gap-2">
      <BaseInput v-model="newCategoryName" :label="$t('board.category.placeholder.new')" :placeholder="$t('board.category.placeholder.new')" hideLabel
        class="flex-1" />
      <BaseSelect v-model="newCategoryRole" :label="$t('common.role')" class="w-32" hideLabel
        >
        <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
      </BaseSelect>
      <BaseButton
        type="submit"
        variant="primary"
        class="px-3"
        :aria-label="$t('board.category.add')"
        :disabled="isReordering || isMutating"
      >
        <Plus class="h-4 w-4" aria-hidden="true" />
      </BaseButton>
    </form>

    <!-- Category List -->
    <div v-if="isLoading" class="text-center py-4">
      <BaseSpinner size="md" />
    </div>

    <ErrorState
      v-else-if="error"
      :message="error"
      :show-retry="true"
      title-tag="h4"
      @retry="fetchCategories"
    />

    <div
      class="border nv-border rounded-md divide-y divide-[var(--nv-line)] nv-surface"
      v-else>
      <!-- Default Category (Static) -->
      <div v-if="defaultCategory" class="px-4 py-3 flex items-center justify-between nv-surface-muted">
        <div class="flex items-center nv-text-subtle cursor-not-allowed p-1 mr-3">
          <GripVertical class="h-4 w-4" aria-hidden="true" />
        </div>

        <div v-if="editingId === defaultCategory.categoryId" class="flex-1 flex items-center gap-2">
          <span class="text-sm nv-title font-medium">{{ defaultCategory.name }} {{
            $t('board.category.default') }}</span>
          <div class="ml-auto flex items-center gap-2">
            <BaseSelect v-model="editingRole" :label="$t('common.role')" class="w-32" hideLabel
              >
              <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
            </BaseSelect>
            <BaseButton @click="saveEdit(defaultCategory)" variant="ghost" size="sm"
              :aria-label="$t('board.category.save')"
              :disabled="isReordering || isMutating"
              class="p-1 text-[var(--nv-success-text)]">
              <Check class="h-4 w-4" aria-hidden="true" />
            </BaseButton>
            <BaseButton @click="cancelEdit" variant="ghost" size="sm"
              :aria-label="$t('board.category.cancel')"
              class="p-1 nv-text-subtle">
              <X class="h-4 w-4" aria-hidden="true" />
            </BaseButton>
          </div>
        </div>

        <div v-else class="flex-1 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-sm nv-title font-medium">{{ defaultCategory.name }} {{
              $t('board.category.default') }}</span>
            <span
              class="text-xs nv-text-subtle nv-surface-muted px-2 py-0.5 rounded-full">{{
                  defaultCategory.minWriteRole || 'USER' }}</span>
          </div>
          <BaseButton @click="startEdit(defaultCategory)" variant="ghost" size="sm"
            :aria-label="$t('board.category.edit')"
            :disabled="isReordering || isMutating"
            class="p-1 nv-accent-text">
            <Edit2 class="h-4 w-4" aria-hidden="true" />
          </BaseButton>
        </div>
      </div>

      <!-- Draggable List -->
      <transition-group name="list" tag="ul" class="divide-y divide-[var(--nv-line)]">
        <li v-for="(category, index) in draggableCategories" :key="category.categoryId"
          class="px-4 py-3 flex items-center justify-between group nv-surface" @dragover.prevent
          @dragenter.prevent @drop="onDrop(index)">
          <div class="flex items-center">
            <div :draggable="!isReordering && !isMutating" @dragstart="onDragStart($event, index)"
              class="mr-3 nv-text-subtle p-1 rounded"
              :class="isReordering || isMutating
                ? 'cursor-not-allowed opacity-50'
                : 'cursor-move nv-hover-surface'">
              <GripVertical class="h-4 w-4" aria-hidden="true" />
            </div>
          </div>

          <div v-if="editingId === category.categoryId" class="flex-1 flex items-center gap-2">
            <BaseInput v-model="editingName" :label="$t('board.category.placeholder.new')" hideLabel
              class="w-full" />
            <div class="ml-auto flex items-center gap-2">
              <BaseSelect v-model="editingRole" :label="$t('common.role')" class="w-32" hideLabel
                >
                <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
              </BaseSelect>
              <BaseButton @click="saveEdit(category)" variant="ghost" size="sm"
                :aria-label="$t('board.category.save')"
                :disabled="isReordering || isMutating"
                class="p-1 text-[var(--nv-success-text)]">
                <Check class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="cancelEdit" variant="ghost" size="sm"
                :aria-label="$t('board.category.cancel')"
                class="p-1 nv-text-subtle">
                <X class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
            </div>
          </div>
          <div v-else class="flex-1 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm nv-text">{{ category.name }}</span>
              <span
                class="text-xs nv-text-subtle nv-surface-muted px-2 py-0.5 rounded-full">{{
                  category.minWriteRole }}</span>
            </div>
            <div class="flex items-center gap-2">
              <BaseButton @click="handleKeyboardMove(index, -1, category.name)" variant="ghost" size="sm"
                :aria-label="$t('common.moveUp', { name: category.name })"
                :disabled="isReordering || isMutating || index === 0"
                class="p-1 nv-text-subtle">
                <ChevronUp class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="handleKeyboardMove(index, 1, category.name)" variant="ghost" size="sm"
                :aria-label="$t('common.moveDown', { name: category.name })"
                :disabled="isReordering || isMutating || index === draggableCategories.length - 1"
                class="p-1 nv-text-subtle">
                <ChevronDown class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="startEdit(category)" variant="ghost" size="sm"
                :aria-label="$t('board.category.edit')"
                :disabled="isReordering || isMutating"
                class="p-1 nv-accent-text">
                <Edit2 class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="handleDelete(category.categoryId)" variant="ghost" size="sm"
                :aria-label="$t('board.category.delete')"
                :disabled="isReordering || isMutating"
                class="p-1 nv-danger-text">
                <Trash2 class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
            </div>
          </div>
        </li>
      </transition-group>
    </div>
    <div v-if="!isLoading && !error && categories.length === 0" class="text-sm nv-text-subtle text-center">
      {{ $t('board.category.empty') }}
    </div>
  </div>
</template>

<style scoped>
.list-move,
.list-enter-active,
.list-leave-active {
  transition: all 0.5s ease;
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

.list-leave-active {
  position: absolute;
}
</style>
