<template>
  <form @submit.prevent="$emit('submit')" class="space-y-3 sm:space-y-4">
    <div class="flex flex-col sm:flex-row sm:items-stretch gap-3 sm:gap-6">
      <div class="flex flex-col items-center shrink-0 sm:min-h-[88px]">
        <button
          type="button"
          class="shrink-0 border-2 border-gray-200 dark:border-gray-700 rounded-full overflow-hidden h-16 w-16 cursor-pointer focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
          :aria-label="$t('user.profile.choosePhoto')"
          @click="fileInputRef?.click()"
        >
          <img
            v-if="profileImageDisplayUrl"
            class="h-full w-full object-contain bg-white dark:bg-gray-700"
            :src="profileImageDisplayUrl"
            alt="Current profile photo"
            @error="$emit('update:profileImageError', true)"
          />
          <div
            v-else
            class="h-full w-full rounded-full bg-indigo-100 dark:bg-gray-700 flex items-center justify-center text-indigo-600 dark:text-gray-200 font-bold text-2xl"
          >
            {{ fallbackInitial }}
          </div>
        </button>
        <button
          type="button"
          class="mt-1.5 sm:mt-auto text-xs text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400"
          @click="fileInputRef?.click()"
        >
          {{ $t('user.profile.choosePhoto') }}
        </button>
        <input
          id="profile-photo-input"
          ref="fileInputRef"
          type="file"
          name="profileImage"
          class="sr-only"
          accept="image/*"
          :aria-label="$t('user.profile.choosePhoto')"
          @change="$emit('file-change', $event)"
        />
      </div>
      <div class="flex-1 w-full min-w-0">
        <BaseInput
          :model-value="displayName"
          :label="$t('user.profile.displayName')"
          :error="displayNameError"
          :placeholder="$t('user.profile.displayNamePlaceholder')"
          @update:model-value="$emit('update:displayName', String($event))"
        />
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

const props = defineProps<{
  displayName: string
  fallbackDisplayName?: string
  profileImageDisplayUrl: string
  profileImageError: boolean
  displayNameError?: string
}>()

defineEmits<{
  (e: 'update:displayName', value: string): void
  (e: 'update:profileImageError', value: boolean): void
  (e: 'file-change', event: Event): void
  (e: 'submit'): void
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const fallbackInitial = computed(() => (props.displayName || props.fallbackDisplayName)?.[0] || 'U')
</script>
