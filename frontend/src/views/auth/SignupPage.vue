<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Smile, ChevronLeft } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AuthEmailVerificationSection from '@/components/auth/AuthEmailVerificationSection.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import AuthPasswordPairFields from '@/components/auth/AuthPasswordPairFields.vue'
import { useSignupRegistration } from '@/composables/useSignupRegistration'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const {
  form,
  fieldErrors,
  isLoading,
  isReregister,
  verification,
  formatTime,
  sendVerificationCode,
  verifyCode,
  handleSignup
} = useSignupRegistration({
  route,
  router,
  t
})

usePwaReloadBlocker(computed(() => (
  Object.values(form.value).some((value) => value.trim().length > 0)
  || verification.isCodeSent
  || verification.isVerified
)))
</script>

<template>
  <div class="relative flex h-full flex-col justify-center p-5 sm:p-8">
    <div class="absolute top-4 left-4">
      <router-link to="/login"
        class="nv-focus-ring flex min-h-11 items-center rounded-md px-1 nv-text-subtle transition-colors hover:text-[var(--nv-ink)]">
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>
    <div class="text-center mb-12 mt-16">
      <h1 class="text-3xl font-extrabold nv-title">
        {{ $t('auth.createAccountTitle') }}
      </h1>
    </div>

    <form class="space-y-6" @submit.prevent="handleSignup">
      <div class="mx-auto w-full space-y-4 sm:w-[80%]">
        <div>
          <BaseInput id="login-id" v-model="form.loginId" name="loginId" type="text" required
            :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel
            :error="fieldErrors.loginId"
            :disabled="isReregister">
            <template #prefix>
              <User class="h-5 w-5 nv-text-subtle" />
            </template>
          </BaseInput>
        </div>
        <AuthPasswordPairFields
          v-model:password="form.password"
          v-model:confirm-password="form.passwordConfirm"
          :password-label="$t('common.password')"
          :confirm-password-label="$t('auth.newPasswordConfirm')"
          :password-placeholder="$t('auth.placeholders.password')"
          :confirm-password-placeholder="$t('auth.newPasswordConfirm')"
          :password-error="fieldErrors.password"
          :confirm-password-error="fieldErrors.passwordConfirm"
          hide-labels
        />

        <div>
          <AuthEmailVerificationSection
            v-model:email="form.email"
            v-model:code="verification.code"
            id-prefix="signup"
            layout="inline"
            :loading="verification.loading"
            :code-sent="verification.isCodeSent"
            :verified="verification.isVerified"
            :email-disabled="verification.isVerified || verification.loading"
            :resend-cooldown="verification.resendCooldown"
            :time-left="verification.timeLeft"
            :email-label="$t('common.email')"
            :email-placeholder="$t('auth.placeholders.newEmail')"
            :code-label="t('auth.codePlaceholder')"
            :send-label="t('auth.sendCode')"
            :sent-label="t('common.sent')"
            :resend-label="t('auth.resendCode')"
            :verify-label="t('auth.verifyCode')"
            :verified-label="t('auth.codeVerified')"
            :expired-label="t('auth.codeExpired')"
            :format-time="formatTime"
            :email-error="fieldErrors.email"
            @send="sendVerificationCode"
            @verify="verifyCode"
          />
        </div>

        <div>
          <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
            :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel
            :error="fieldErrors.displayName">
            <template #prefix>
              <Smile class="h-5 w-5 nv-text-subtle" />
            </template>
          </BaseInput>
        </div>
      </div>


      <div class="flex justify-center mt-8">
        <BaseButton type="submit" :loading="isLoading" class="w-full sm:w-[80%]" variant="primary"
          :disabled="isReregister && form.loginId.includes('*')">
          {{ $t('auth.signup') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>
