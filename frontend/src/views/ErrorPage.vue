<script setup>
import { useRouter } from 'vue-router'
import { AlertTriangle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const router = useRouter()

const props = defineProps({
  title: {
    type: String,
    default: () => t('common.messages.defaultTitle')
  },
  message: {
    type: String,
    default: () => t('common.messages.defaultMessage')
  }
})
</script>

<template>
  <div class="min-h-[50vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 text-center">
      <div>
        <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100">
          <AlertTriangle class="h-6 w-6 text-red-600" aria-hidden="true" />
        </div>
        <h2 class="mt-6 text-3xl font-extrabold text-gray-900">
          {{ title }}
        </h2>
        <p class="mt-2 text-sm text-gray-600">
          {{ message }}
        </p>
      </div>
      <div class="mt-5 sm:mt-8 sm:flex sm:justify-center">
        <div class="rounded-md shadow">
          <button
            @click="router.push('/')"
            class="w-full flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 md:py-4 md:text-lg md:px-10"
          >
            {{ $t('common.home') }}
          </button>
        </div>
        <div class="mt-3 sm:mt-0 sm:ml-3">
          <button
            @click="router.back()"
            class="w-full flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 md:py-4 md:text-lg md:px-10"
          >
            {{ $t('common.prevPage') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
