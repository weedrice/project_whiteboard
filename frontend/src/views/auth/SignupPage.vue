<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { User, Smile, ChevronLeft } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AuthEmailVerificationSection from '@/components/auth/AuthEmailVerificationSection.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import AuthPasswordPairFields from '@/components/auth/AuthPasswordPairFields.vue'
import { useSignupRegistration } from '@/composables/useSignupRegistration'

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
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link to="/login"
        class="flex items-center nv-text-subtle hover:text-[var(--nv-text)] transition-colors">
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>
    <div class="text-center mb-12 mt-16">
      <h2 class="text-3xl font-extrabold nv-title">
        {{ $t('auth.createAccountTitle') }}
      </h2>
    </div>

    <form class="space-y-6" @submit.prevent="handleSignup">
      <div class="space-y-4 w-[80%] mx-auto">
        <div>
          <BaseInput id="login-id" v-model="form.loginId" name="loginId" type="text" required
            :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel
            :disabled="isReregister">
            <template #prefix>
              <User class="h-5 w-5 nv-text-subtle" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.loginId" class="text-xs nv-form-error mt-1 ml-1">
            {{ fieldErrors.loginId }}
          </p>
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
            @send="sendVerificationCode"
            @verify="verifyCode"
          />
          <p v-if="fieldErrors.email" class="text-xs nv-form-error mt-1 ml-1">
            {{ fieldErrors.email }}
          </p>
        </div>

        <div>
          <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
            :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel>
            <template #prefix>
              <Smile class="h-5 w-5 nv-text-subtle" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.displayName" class="text-xs nv-form-error mt-1 ml-1">
            {{ fieldErrors.displayName }}
          </p>
        </div>
      </div>


      <div class="flex justify-center mt-8">
        <BaseButton type="submit" :loading="isLoading" class="w-[80%]" variant="primary"
          :disabled="isReregister && form.loginId.includes('*')">
          {{ $t('auth.signup') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>
