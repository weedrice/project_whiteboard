<script setup lang="ts">
import { computed, onMounted, toRef } from 'vue'
import { Trash2, Edit2, Check, X, Plus, GripVertical } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { useBoardCategoriesManager } from '@/composables/useBoardCategoriesManager'

const { t } = useI18n()

const props = defineProps<{
  boardUrl: string
}>()

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
  isReordering,
  defaultCategory,
  draggableCategories,
  fetchCategories,
  handleAdd,
  handleDelete,
  startEdit,
  cancelEdit,
  saveEdit,
  onDragStart,
  onDrop
} = useBoardCategoriesManager(toRef(props, 'boardUrl'))

onMounted(fetchCategories)
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('common.category') }}</h3>

    <!-- Add Category -->
    <form @submit.prevent="handleAdd" class="flex gap-2">
      <BaseInput v-model="newCategoryName" :label="$t('board.category.placeholder.new')" :placeholder="$t('board.category.placeholder.new')" hideLabel
        inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600 dark:placeholder-gray-400" class="flex-1" />
      <BaseSelect v-model="newCategoryRole" :label="$t('common.role')" class="w-32" hideLabel
        inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
        <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
      </BaseSelect>
      <BaseButton
        type="submit"
        variant="primary"
        class="px-3"
        :aria-label="$t('board.category.add')"
        :disabled="isReordering"
      >
        <Plus class="h-4 w-4" aria-hidden="true" />
      </BaseButton>
    </form>

    <!-- Category List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div
      class="border border-gray-200 dark:border-gray-700 rounded-md divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800"
      v-else>
      <!-- Default Category (Static) -->
      <div v-if="defaultCategory" class="px-4 py-3 flex items-center justify-between bg-gray-50 dark:bg-gray-900/50">
        <div class="flex items-center text-gray-400 dark:text-gray-500 cursor-not-allowed p-1 mr-3">
          <GripVertical class="h-4 w-4" aria-hidden="true" />
        </div>

        <div v-if="editingId === defaultCategory.categoryId" class="flex-1 flex items-center gap-2">
          <span class="text-sm text-gray-900 dark:text-gray-200 font-medium">{{ defaultCategory.name }} {{
            $t('board.category.default') }}</span>
          <div class="ml-auto flex items-center gap-2">
            <BaseSelect v-model="editingRole" :label="$t('common.role')" class="w-32" hideLabel
              inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
              <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
            </BaseSelect>
            <BaseButton @click="saveEdit(defaultCategory)" variant="ghost" size="sm"
              :aria-label="$t('board.category.save')"
              :disabled="isReordering"
              class="p-1 text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300">
              <Check class="h-4 w-4" aria-hidden="true" />
            </BaseButton>
            <BaseButton @click="cancelEdit" variant="ghost" size="sm"
              :aria-label="$t('board.category.cancel')"
              class="p-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300">
              <X class="h-4 w-4" aria-hidden="true" />
            </BaseButton>
          </div>
        </div>

        <div v-else class="flex-1 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-900 dark:text-gray-200 font-medium">{{ defaultCategory.name }} {{
              $t('board.category.default') }}</span>
            <span
              class="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">{{
                  defaultCategory.minWriteRole || 'USER' }}</span>
          </div>
          <BaseButton @click="startEdit(defaultCategory)" variant="ghost" size="sm"
            :aria-label="$t('board.category.edit')"
            :disabled="isReordering"
            class="p-1 text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300">
            <Edit2 class="h-4 w-4" aria-hidden="true" />
          </BaseButton>
        </div>
      </div>

      <!-- Draggable List -->
      <transition-group name="list" tag="ul" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="(category, index) in draggableCategories" :key="category.categoryId"
          class="px-4 py-3 flex items-center justify-between group bg-white dark:bg-gray-800" @dragover.prevent
          @dragenter.prevent @drop="onDrop(index)">
          <div class="flex items-center">
            <div :draggable="!isReordering" @dragstart="onDragStart($event, index)"
              class="mr-3 text-gray-400 dark:text-gray-500 p-1 rounded"
              :class="isReordering
                ? 'cursor-not-allowed opacity-50'
                : 'cursor-move hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'">
              <GripVertical class="h-4 w-4" aria-hidden="true" />
            </div>
          </div>

          <div v-if="editingId === category.categoryId" class="flex-1 flex items-center gap-2">
            <BaseInput v-model="editingName" :label="$t('board.category.placeholder.new')" hideLabel
              inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600" class="w-full" />
            <div class="ml-auto flex items-center gap-2">
              <BaseSelect v-model="editingRole" :label="$t('common.role')" class="w-32" hideLabel
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
                <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
              </BaseSelect>
              <BaseButton @click="saveEdit(category)" variant="ghost" size="sm"
                :aria-label="$t('board.category.save')"
                :disabled="isReordering"
                class="p-1 text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300">
                <Check class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="cancelEdit" variant="ghost" size="sm"
                :aria-label="$t('board.category.cancel')"
                class="p-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300">
                <X class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
            </div>
          </div>
          <div v-else class="flex-1 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-900 dark:text-gray-200">{{ category.name }}</span>
              <span
                class="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">{{
                  category.minWriteRole }}</span>
            </div>
            <div class="flex items-center gap-2">
              <BaseButton @click="startEdit(category)" variant="ghost" size="sm"
                :aria-label="$t('board.category.edit')"
                :disabled="isReordering"
                class="p-1 text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300">
                <Edit2 class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
              <BaseButton @click="handleDelete(category.categoryId)" variant="ghost" size="sm"
                :aria-label="$t('board.category.delete')"
                :disabled="isReordering"
                class="p-1 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300">
                <Trash2 class="h-4 w-4" aria-hidden="true" />
              </BaseButton>
            </div>
          </div>
        </li>
      </transition-group>
    </div>
    <div v-if="!isLoading && categories.length === 0" class="text-sm text-gray-500 dark:text-gray-400 text-center">
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
