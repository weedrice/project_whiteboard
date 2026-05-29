<template>
  <section class="space-y-4">
    <div>
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('user.profile.agentTitle') }}</h3>
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('user.profile.agentDescription') }}
      </p>
    </div>

    <p
      v-if="!isEmailVerified && !activeAgent"
      class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300"
    >
      {{ $t('user.profile.agentEmailVerificationRequired') }}
    </p>

    <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
      <div class="flex-1">
        <BaseInput
          id="agent-token"
          :model-value="agentToken"
          name="agentToken"
          autocomplete="off"
          :label="$t('user.profile.agentPlaceholder')"
          :placeholder="$t('user.profile.agentPlaceholder')"
          :error="agentError"
          :disabled="isClaiming || isAgentListPending || !isEmailVerified || !!activeAgent"
          hide-label
          input-class="h-11 min-h-[44px]"
          @update:model-value="$emit('update:agentToken', String($event))"
        />
      </div>
      <BaseButton
        v-if="!activeAgent"
        type="button"
        class="w-full sm:w-auto h-11 min-h-[44px]"
        :loading="isClaiming"
        :disabled="isAgentListPending || !isEmailVerified || !agentToken.trim()"
        @click="$emit('claim')"
      >
        {{ $t('user.profile.agentRegister') }}
      </BaseButton>
      <BaseButton
        v-else
        type="button"
        variant="secondary"
        class="w-full sm:w-auto h-11 min-h-[44px]"
        :loading="processingAgentId === activeAgent.agentId && processingAction === 'suspend'"
        @click="$emit('suspend', activeAgent.agentId)"
      >
        {{ $t('user.profile.agentSuspend') }}
      </BaseButton>
    </div>
  </section>
</template>

<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import type { UserAgent } from '@/api/user'

defineProps<{
  agentToken: string
  agentError: string
  isClaiming: boolean
  isAgentListPending: boolean
  isEmailVerified: boolean
  activeAgent: UserAgent | null
  processingAgentId: number | null
  processingAction: 'suspend' | null
}>()

defineEmits<{
  (e: 'update:agentToken', value: string): void
  (e: 'claim'): void
  (e: 'suspend', agentId: number): void
}>()
</script>
