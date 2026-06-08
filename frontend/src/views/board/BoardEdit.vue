<script setup lang="ts">
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
import { useBoardEditPage } from '@/composables/useBoardEditPage'

const {
  boardUrl,
  canManageBoard,
  closeManagerModal,
  confirmManagerSelection,
  currentManagerLabel,
  error,
  form,
  goBack,
  handleDelete,
  handleUpdate,
  isLoading,
  isManagerModalOpen,
  isSubmitting,
  isTransferringManager,
  openManagerModal
} = useBoardEditPage()
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <BaseSpinner size="lg" />
    </div>

    <div v-else-if="canManageBoard" class="nv-surface shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b nv-border flex justify-between items-center">
        <div>
          <h3 class="text-lg leading-6 font-medium nv-title">{{ $t('board.form.editTitle') }}</h3>
          <p class="mt-1 max-w-2xl text-sm nv-text-subtle">{{ $t('board.form.editDesc') }}</p>
        </div>
        <BaseButton type="button" @click="goBack" variant="secondary">
          {{ $t('common.back') }}
        </BaseButton>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- Board Form -->
        <BoardForm :initialData="form" :isEdit="true" :isSubmitting="isSubmitting" :error="error" @submit="handleUpdate"
          @cancel="goBack" />

        <hr class="nv-border" />

        <div class="rounded-lg border nv-border p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h4 class="text-sm font-semibold nv-title">{{ $t('common.admin') }}</h4>
              <p class="mt-1 text-sm nv-text-muted">{{ currentManagerLabel }}</p>
            </div>
            <BaseButton type="button" @click="openManagerModal" :disabled="isTransferringManager">
              {{ isTransferringManager ? $t('common.messages.saving') : $t('common.manage') }}
            </BaseButton>
          </div>
        </div>

        <hr class="nv-border" />

        <!-- Category Manager -->
        <div class="py-6">
          <CategoryManager :boardUrl="boardUrl" />
        </div>

        <hr class="nv-border" />

        <!-- Delete Board (Moved to bottom right) -->
        <div class="flex justify-end">
          <BaseButton type="button" @click="handleDelete" variant="danger">
            {{ $t('board.form.delete') }}
          </BaseButton>
        </div>
      </div>
    </div>

    <UserSelectModal
      :isOpen="isManagerModalOpen"
      selectionMode="single"
      source="board-manager-candidates"
      :boardUrl="boardUrl"
      @close="closeManagerModal"
      @confirm="confirmManagerSelection"
    />
  </div>
</template>
