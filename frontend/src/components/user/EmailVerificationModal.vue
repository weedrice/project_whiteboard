<script setup lang="ts">
import { ShieldCheck } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import type { EmailVerificationState } from '@/composables/useEmailVerificationState'

defineProps<{
  isOpen: boolean
  verification: EmailVerificationState
  formatVerifyTime: (seconds: number) => string
  isValidEmail: (value: string) => boolean
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'send-code'): void
  (event: 'verify-code'): void
  (event: 'update:email', value: string): void
  (event: 'update:code', value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('auth.emailNotVerified')" @close="$emit('close')">
    <div class="space-y-6 p-4">
      <div class="flex flex-col">
        <label class="block text-sm font-medium nv-text mb-1">
          {{ t('user.profile.email') }}
        </label>
        <div class="flex gap-2 items-end">
          <div class="flex-1 min-w-0">
            <BaseInput
              id="email-verification-email"
              :model-value="verification.email"
              name="emailVerificationEmail"
              autocomplete="email"
              :label="t('user.profile.email')"
              :placeholder="t('auth.emailPlaceholder')"
              :disabled="verification.isCodeSent"
              inputClass="h-11 min-h-[44px]"
              hideLabel
              :error="verification.email.trim() && !isValidEmail(verification.email.trim()) ? t('auth.validation.emailFormat') : ''"
              hideErrorText
              aria-describedby="email-verification-email-error"
              @update:model-value="$emit('update:email', String($event))"
            />
          </div>
          <BaseButton
            :disabled="verification.loading || (verification.isCodeSent && verification.resendCooldown > 0) || !isValidEmail(verification.email.trim())"
            :loading="verification.loading"
            class="h-11 min-h-[44px] shrink-0"
            @click="$emit('send-code')"
          >
            <span v-if="verification.isCodeSent && verification.resendCooldown > 0">
              {{ formatVerifyTime(verification.resendCooldown) }}
            </span>
            <span v-else>
              {{ verification.isCodeSent ? t('auth.resendCode') : t('auth.sendCode') }}
            </span>
          </BaseButton>
        </div>
        <p
          v-if="verification.email.trim() && !isValidEmail(verification.email.trim())"
          id="email-verification-email-error"
          class="text-xs sm:text-sm nv-form-error mt-1"
          role="alert"
        >
          {{ t('auth.validation.emailFormat') }}
        </p>
      </div>

      <div v-if="verification.isCodeSent" class="space-y-4">
        <div class="relative">
          <BaseInput
            id="email-verification-code"
            :model-value="verification.code"
            name="emailVerificationCode"
            inputmode="numeric"
            autocomplete="one-time-code"
            :label="t('auth.codePlaceholder')"
            :placeholder="t('auth.codePlaceholder')"
            :error="verification.timeLeft <= 0 ? t('auth.codeExpired') : ''"
            hideLabel
            :disabled="verification.timeLeft <= 0"
            @update:model-value="$emit('update:code', String($event))"
          >
            <template #prefix>
              <ShieldCheck class="h-5 w-5 nv-text-subtle" />
            </template>
          </BaseInput>
          <span
            class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
            :class="verification.timeLeft <= 60 ? 'nv-form-error' : 'nv-text-subtle'"
          >
            {{ formatVerifyTime(verification.timeLeft) }}
          </span>
        </div>

        <BaseButton
          :disabled="verification.loading || verification.timeLeft <= 0 || !verification.code"
          :loading="verification.loading"
          variant="primary"
          class="w-full"
          @click="$emit('verify-code')"
        >
          {{ t('auth.verifyCode') }}
        </BaseButton>
      </div>
    </div>
  </BaseModal>
</template>
