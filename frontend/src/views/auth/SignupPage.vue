<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { Lock, User, Mail, Smile, ChevronLeft, CheckCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
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
        class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors">
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>
    <div class="text-center mb-12 mt-16">
      <h2 class="text-3xl font-extrabold text-gray-900 dark:text-white">
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
              <User class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.loginId" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.loginId }}
          </p>
        </div>
        <div>
          <BaseInput id="password" v-model="form.password" name="password" type="password" required
            :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
            <template #prefix>
              <Lock class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.password" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.password }}
          </p>
        </div>
        <div>
          <BaseInput id="password-confirm" v-model="form.passwordConfirm" name="passwordConfirm" type="password"
            required :placeholder="$t('auth.newPasswordConfirm')" :label="$t('auth.newPasswordConfirm')" hideLabel>
            <template #prefix>
              <Lock class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.passwordConfirm" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.passwordConfirm }}
          </p>
        </div>

        <!-- Email Verification -->
        <div>
          <div class="flex gap-2 items-start">
            <div class="flex-grow">
              <BaseInput id="email" v-model="form.email" name="email" type="email" required
                :placeholder="$t('auth.placeholders.newEmail')" :label="$t('common.email')" hideLabel
                :disabled="verification.isVerified || verification.loading">
                <template #prefix>
                  <Mail class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
            </div>
            <BaseButton v-if="!verification.isVerified" type="button" @click="sendVerificationCode"
              :disabled="verification.resendCooldown > 0 || verification.loading" :loading="verification.loading">
              <span v-if="verification.resendCooldown > 0">
                {{ t('common.sent') }}
              </span>
              <span v-else-if="verification.isCodeSent">
                {{ t('auth.resendCode') }}
              </span>
              <span v-else>
                {{ t('auth.sendCode') }}
              </span>
            </BaseButton>
            <span v-else class="flex items-center text-green-500 text-sm font-medium whitespace-nowrap py-2 px-3">
              <CheckCircle class="h-4 w-4 mr-1" />
              {{ t('auth.codeVerified') }}
            </span>
          </div>
          <p v-if="fieldErrors.email" class="text-xs text-red-500 mt-1 ml-1">
            {{ fieldErrors.email }}
          </p>

          <div v-if="verification.isCodeSent && !verification.isVerified"
            class="flex gap-2 items-start mt-4 animate-fade-in-down">
            <div class="flex-grow relative">
              <BaseInput v-model="verification.code" :placeholder="t('auth.codePlaceholder')" hideLabel
                :disabled="verification.timeLeft <= 0">
                <template #prefix>
                  <CheckCircle class="h-5 w-5 text-gray-400" />
                </template>
              </BaseInput>
              <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium"
                :class="verification.timeLeft <= 60 ? 'text-red-500' : 'text-gray-500'">
                {{ formatTime(verification.timeLeft) }}
              </span>
            </div>
            <BaseButton type="button" @click="verifyCode" :disabled="verification.loading || verification.timeLeft <= 0"
              :loading="verification.loading">
              {{ t('auth.verifyCode') }}
            </BaseButton>
          </div>
          <p v-if="verification.isCodeSent && verification.timeLeft <= 0 && !verification.isVerified"
            class="text-xs text-red-500 mt-1 ml-1">
            {{ t('auth.codeExpired') }}
          </p>
        </div>

        <div>
          <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
            :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel>
            <template #prefix>
              <Smile class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
          <p v-if="fieldErrors.displayName" class="text-xs text-red-500 mt-1 ml-1">
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

<style scoped>
.animate-fade-in-down {
  animation: fadeInDown 0.3s ease-out;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
