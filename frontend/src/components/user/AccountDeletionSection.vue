<template>
  <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
    <button
      type="button"
      class="text-left text-xs text-gray-500 underline underline-offset-2 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400"
      @click="$emit('update:showDeleteModal', true)"
    >
      {{ $t('user.settings.deleteAccount') }}
    </button>
    <BaseButton type="button" :loading="loading" class="w-full sm:w-auto h-11 min-h-[44px]" @click="$emit('save')">
      {{ loading ? $t('common.saving') : $t('common.save') }}
    </BaseButton>
  </div>

  <BaseModal
    :isOpen="showDeleteModal"
    :title="$t('user.settings.deleteAccount')"
    @close="$emit('update:showDeleteModal', false)"
  >
    <div class="space-y-4">
      <p class="text-sm text-gray-500">
        {{ $t('user.settings.deleteAccountConfirmation') }}
      </p>

      <div class="bg-red-50 p-4 rounded-md">
        <div class="flex items-center">
          <div class="shrink-0 flex items-center justify-center">
            <svg class="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
              <path
                fill-rule="evenodd"
                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
          <div class="ml-3 min-w-0 flex-1">
            <p class="text-[13px] text-red-700 text-left">{{ $t('user.settings.deleteAccountWarning') }}</p>
          </div>
        </div>
      </div>

      <BaseInput
        :model-value="deletePassword"
        type="password"
        :label="$t('common.password')"
        :placeholder="$t('auth.placeholders.password')"
        :error="deleteError"
        @update:model-value="$emit('update:deletePassword', String($event))"
      />
    </div>
    <template #footer>
      <div class="flex justify-end space-x-3">
        <BaseButton variant="secondary" @click="$emit('update:showDeleteModal', false)">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton variant="danger" :loading="isDeleting" @click="$emit('delete')">
          {{ $t('user.settings.deleteAccount') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'

defineProps<{
  showDeleteModal: boolean
  deletePassword: string
  deleteError: string
  isDeleting: boolean
  loading: boolean
}>()

defineEmits<{
  (e: 'update:showDeleteModal', value: boolean): void
  (e: 'update:deletePassword', value: string): void
  (e: 'save'): void
  (e: 'delete'): void
}>()
</script>
