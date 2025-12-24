<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Lock, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const { t } = useI18n()
const toastStore = useToastStore()

const router = useRouter()
const authStore = useAuthStore()

const loginId = ref('')
const password = ref('')
const error = ref('')
const isLoading = ref(false)

async function handleLogin() {
  error.value = ''

  if (!loginId.value) {
    toastStore.addToast(t('auth.placeholders.loginId'), 'error')
    return
  }
  if (!password.value) {
    toastStore.addToast(t('auth.placeholders.password'), 'error')
    return
  }

  isLoading.value = true

  try {
    await authStore.login({
      loginId: loginId.value,
      password: password.value
    })
    router.push('/')
  } catch (err) {
    const message = err.response?.data?.error?.message || t('auth.loginFailed')
    toastStore.addToast(message, 'error', 3000, 'top-center')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-8 h-full flex flex-col justify-center">
    <div class="text-center mb-8">
      <h2 class="text-3xl font-extrabold text-gray-900 dark:text-white">
        {{ $t('common.login') }}
      </h2>
      <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
        {{ $t('common.or') }}
        <router-link to="/signup"
          class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
          {{ $t('auth.createAccount') }}
        </router-link>
      </p>
    </div>

    <form class="space-y-6" @submit.prevent="handleLogin">
      <div class="space-y-4 w-[80%] mx-auto">
        <div>
          <BaseInput id="login-id" v-model="loginId" name="loginId" type="text" required
            :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel>
            <template #prefix>
              <User class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
        </div>
        <div>
          <BaseInput id="password" v-model="password" name="password" type="password" required
            :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
            <template #prefix>
              <Lock class="h-5 w-5 text-gray-400" />
            </template>
          </BaseInput>
        </div>
      </div>

      <div class="w-[80%] mx-auto flex justify-end">
        <router-link v-if="false" to="/find"
          class="text-sm text-gray-600 hover:text-indigo-500 dark:text-gray-400 dark:hover:text-indigo-400">
          {{ $t('auth.findIdPassword') }}
        </router-link>
      </div>


      <div class="flex justify-center mt-8">
        <BaseButton type="submit" :loading="isLoading" class="w-[80%]" variant="primary">
          {{ $t('common.login') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>
