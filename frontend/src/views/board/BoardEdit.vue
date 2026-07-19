<script setup lang="ts">
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import BoardManagerGovernancePanel from '@/components/board/BoardManagerGovernancePanel.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
import { useBoardEditPage } from '@/features/board/edit/useBoardEditPage'

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

    <div v-else-if="canManageBoard" class="nv-surface nv-elevated-surface shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <PageHeader
        :title="$t('board.form.editTitle')"
        :description="$t('board.form.editDesc')"
        size="compact"
        class="border-b nv-border px-4 py-5 sm:px-6"
      >
        <template #actions>
          <BaseButton type="button" @click="goBack" variant="secondary">
            {{ $t('common.back') }}
          </BaseButton>
        </template>
      </PageHeader>

      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- Board Form -->
        <BoardForm :initialData="form" :isEdit="true" :isSubmitting="isSubmitting" :error="error"
          :submit-action="handleUpdate" @submit="handleUpdate"
          @cancel="goBack" />

        <hr class="nv-border" />

        <div class="rounded-lg border nv-border p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-sm font-semibold nv-title">{{ $t('common.admin') }}</h2>
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
          <CategoryManager :boardUrl="boardUrl" heading-tag="h2" />
        </div>

        <hr class="nv-border" />

        <BoardManagerGovernancePanel :board-url="boardUrl" :enabled="canManageBoard" />

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
