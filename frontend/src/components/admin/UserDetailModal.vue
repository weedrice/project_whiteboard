<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { formatDate } from '@/utils/date'
import type { User } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  isOpen: boolean
  user: User | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const statusVariant = computed(() => {
  if (!props.user) return 'gray'
  switch (props.user.status) {
    case 'ACTIVE':
      return 'success'
    case 'SANCTIONED':
      return 'danger'
    case 'INACTIVE':
      return 'gray'
    default:
      return 'gray'
  }
})

const roleVariant = computed(() => {
  if (!props.user) return 'gray'
  switch (props.user.role) {
    case 'SUPER_ADMIN':
      return 'danger'
    case 'ADMIN':
    case 'BOARD_ADMIN':
      return 'warning'
    default:
      return 'gray'
  }
})
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.users.detail.title')" @close="$emit('close')">
    <div v-if="user" class="space-y-6">
      <!-- 기본 정보 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.users.detail.basicInfo') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.id') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ user.userId }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.loginId') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ user.loginId }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.displayName') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ user.displayName }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.email') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ user.email }}</dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.status') }}</dt>
            <dd class="mt-1">
              <BaseBadge :variant="statusVariant" size="sm">
                {{ t(`admin.users.status.${user.status}`) }}
              </BaseBadge>
            </dd>
          </div>
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.role') }}</dt>
            <dd class="mt-1">
              <BaseBadge :variant="roleVariant" size="sm">
                {{ t(`admin.users.role.${user.role}`) }}
              </BaseBadge>
            </dd>
          </div>
          <div v-if="user.points !== undefined">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('common.points') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ user.points.toLocaleString() }} P</dd>
          </div>
          <div v-if="user.isEmailVerified !== undefined">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.emailVerified') }}</dt>
            <dd class="mt-1">
              <BaseBadge :variant="user.isEmailVerified ? 'success' : 'gray'" size="sm">
                {{ user.isEmailVerified ? t('common.yes') : t('common.noValue') }}
              </BaseBadge>
            </dd>
          </div>
        </dl>
      </div>

      <!-- 날짜 정보 -->
      <div>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.users.detail.dateInfo') }}
        </h3>
        <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.createdAt') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(user.createdAt) }}</dd>
          </div>
          <div v-if="user.modifiedAt">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.modifiedAt') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(user.modifiedAt) }}</dd>
          </div>
          <div v-if="user.lastLoginAt">
            <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('admin.users.detail.lastLoginAt') }}</dt>
            <dd class="mt-1 text-sm text-gray-900 dark:text-white">{{ formatDate(user.lastLoginAt) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 추가 정보 -->
      <div v-if="user.bio">
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">
          {{ t('admin.users.detail.bio') }}
        </h3>
        <p class="text-sm text-gray-700 dark:text-gray-300">{{ user.bio }}</p>
      </div>
    </div>
  </BaseModal>
</template>
