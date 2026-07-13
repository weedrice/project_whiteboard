<script setup lang="ts">
import { Calendar, CheckCircle, Clock, Mail, ShieldCheck, User, XCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import ProfileInfoRow from '@/components/user/ProfileInfoRow.vue'
import { getOptimizedProfileImageUrl, handleImageError } from '@/utils/image'
import { formatDate } from '@/utils/date'
import type { User as ProfileUser } from '@/types'
import type { UserAgent } from '@/api/user'

defineProps<{
  profile: ProfileUser | null
  agents: UserAgent[]
  getAgentStatusLabel: (status: UserAgent['status']) => string
}>()

defineEmits<{
  (event: 'edit'): void
  (event: 'verify-email'): void
}>()

const { t } = useI18n()
</script>

<template>
  <BaseCard no-padding class="mb-6">
    <div class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-4">
      <div class="flex items-center min-w-0 flex-1">
        <UserAvatar
          :image-url="profile?.profileImageUrl ? getOptimizedProfileImageUrl(profile.profileImageUrl) : null"
          :name="profile?.displayName || 'U'"
          alt="Profile"
          size-class="h-16 w-16"
          fallback-class="font-bold text-2xl"
          class="mr-4"
          @image-error="handleImageError"
        />
        <div class="min-w-0">
          <h3 class="text-lg leading-6 font-medium nv-title truncate">{{ profile?.displayName }}</h3>
          <p class="mt-1 max-w-2xl text-sm nv-text-subtle">{{ t('user.profile.personalDetails') }}</p>
        </div>
      </div>
      <BaseButton
        class="w-full sm:w-auto min-h-[44px] sm:min-h-0 flex items-center justify-center"
        @click="$emit('edit')"
      >
        {{ t('user.profile.edit') }}
      </BaseButton>
    </div>
    <div class="border-t nv-border px-4 py-5 sm:p-0">
      <dl class="sm:divide-y sm:divide-[var(--nv-line)]">
        <ProfileInfoRow :icon="User" :label="t('user.profile.displayName')">
          {{ profile?.displayName }}
        </ProfileInfoRow>
        <ProfileInfoRow :icon="Mail" :label="t('user.profile.email')">
          {{ profile?.email }}
          <span
            v-if="profile?.isEmailVerified"
            class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium nv-status-success"
          >
            <CheckCircle class="h-3 w-3 mr-1" /> {{ t('user.profile.verified') }}
          </span>
          <button
            v-else
            type="button"
            class="ml-2 inline-flex min-h-11 items-center rounded-full px-2.5 py-0.5 text-xs font-medium nv-status-danger nv-hover-surface transition-colors cursor-pointer sm:min-h-0"
            @click="$emit('verify-email')"
          >
            <XCircle class="h-3 w-3 mr-1" /> {{ t('user.profile.notVerified') }}
          </button>
        </ProfileInfoRow>
        <ProfileInfoRow
          :icon="ShieldCheck"
          :label="t('user.profile.agentCode')"
          icon-class="h-4 w-4 mr-1.5"
          content-class="mt-1 sm:mt-0"
        >
          <div v-if="agents.length > 0" class="flex flex-wrap gap-2">
            <span
              v-for="agent in agents"
              :key="agent.agentId"
              class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium"
              :class="agent.status === 'ACTIVE'
                ? 'nv-status-success'
                : agent.status === 'SUSPENDED'
                  ? 'nv-status-danger'
                  : 'nv-surface-muted nv-text-muted'"
            >
              <span>{{ agent.name }}</span>
              <span>{{ getAgentStatusLabel(agent.status) }}</span>
            </span>
          </div>
          <span v-else class="text-sm nv-text-subtle">
            {{ t('user.profile.agentEmpty') }}
          </span>
        </ProfileInfoRow>
        <ProfileInfoRow :icon="Calendar" :label="t('user.profile.joined')">
          {{ profile?.createdAt ? formatDate(profile.createdAt) : '' }}
        </ProfileInfoRow>
        <ProfileInfoRow :icon="Clock" :label="t('user.profile.lastLogin')">
          {{ profile?.lastLoginAt ? formatDate(profile.lastLoginAt) : '' }}
        </ProfileInfoRow>
      </dl>
    </div>
  </BaseCard>
</template>
