<template>
  <form @submit.prevent="$emit('submit')" class="space-y-3 sm:space-y-4">
    <div class="flex flex-col sm:flex-row sm:items-stretch gap-3 sm:gap-6">
      <div class="flex flex-col items-center shrink-0 sm:min-h-[88px]">
          <button
            type="button"
            class="shrink-0 border-2 nv-border rounded-full overflow-hidden h-16 w-16 cursor-pointer nv-focus-ring"
          :aria-label="$t('user.profile.choosePhoto')"
          @click="fileInputRef?.click()"
        >
          <img
            v-if="profileImageDisplayUrl"
            class="h-full w-full object-contain nv-surface-muted"
            :src="profileImageDisplayUrl"
            :alt="$t('user.profile.currentPhotoAlt')"
            @error="$emit('update:profileImageError', true)"
          />
          <div
            v-else
            class="h-full w-full rounded-full nv-avatar-fallback flex items-center justify-center font-bold text-2xl"
          >
            {{ fallbackInitial }}
          </div>
        </button>
        <button
          type="button"
            class="mt-1.5 sm:mt-auto text-xs nv-text-subtle profile-photo-button"
          @click="fileInputRef?.click()"
        >
          {{ $t('user.profile.choosePhoto') }}
        </button>
        <button
          v-if="canRemoveProfileImage"
          type="button"
          class="mt-1 text-xs nv-form-error profile-photo-button"
          @click="$emit('remove-photo')"
        >
          {{ $t('user.profile.removePhoto') }}
        </button>
        <p v-if="profileImageCostText" class="mt-1 max-w-[8rem] text-center text-xs nv-text-subtle">
          {{ profileImageCostText }}
        </p>
        <input
          id="profile-photo-input"
          ref="fileInputRef"
          type="file"
          name="profileImage"
          class="sr-only"
          :accept="IMAGE_UPLOAD_ACCEPT"
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
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'

const props = defineProps<{
  displayName: string
  fallbackDisplayName?: string
  profileImageDisplayUrl: string
  profileImageError: boolean
  displayNameError?: string
  profileImageCostText?: string
  canRemoveProfileImage?: boolean
}>()

defineEmits<{
  (e: 'update:displayName', value: string): void
  (e: 'update:profileImageError', value: boolean): void
  (e: 'file-change', event: Event): void
  (e: 'remove-photo'): void
  (e: 'submit'): void
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const fallbackInitial = computed(() => (props.displayName || props.fallbackDisplayName)?.[0] || 'U')
</script>

<style scoped>
.profile-photo-button:hover {
  color: var(--nv-text);
}
</style>
