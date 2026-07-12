<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import UserSelectModal from '@/components/common/widgets/UserSelectModal.vue'
import AdminAuditLogTable from '@/components/admin/AdminAuditLogTable.vue'
import { boardApi } from '@/api/board'
import { unwrapAxiosApiData } from '@/api/response'
import { useBoardEditPage } from '@/composables/useBoardEditPage'
import type { ModerationAuditLog } from '@/types'

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

const { t } = useI18n()
const managerAuditParams = computed(() => ({ page: 0, size: 5, sort: 'createdAt,desc' }))
const managerAuditLogs = ref<ModerationAuditLog[]>([])
let managerAuditLoadId = 0

watch([boardUrl, canManageBoard], async ([nextBoardUrl, manageable]) => {
  const loadId = ++managerAuditLoadId
  managerAuditLogs.value = []
  if (!manageable || !nextBoardUrl || typeof boardApi.getManagerAudits !== 'function') return

  try {
    const page = unwrapAxiosApiData(await boardApi.getManagerAudits(nextBoardUrl, managerAuditParams.value))
    if (loadId === managerAuditLoadId) {
      managerAuditLogs.value = page.content
    }
  } catch {
    if (loadId === managerAuditLoadId) {
      managerAuditLogs.value = []
    }
  }
}, { immediate: true })
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
          <h1 class="text-lg leading-6 font-medium nv-title">{{ $t('board.form.editTitle') }}</h1>
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

        <div class="rounded-lg border nv-border">
          <div class="border-b nv-border px-4 py-3">
            <h4 class="text-sm font-semibold nv-title">{{ t('admin.dashboard.auditLogs') }}</h4>
          </div>
          <AdminAuditLogTable
            :audits="managerAuditLogs"
            :caption="t('admin.dashboard.auditLogs')"
            :empty-text="t('admin.dashboard.auditEmpty')"
          />
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
