<template>
  <Teleport to="body">
    <div v-if="isOpen" 
      class="modal-overlay" 
      @click.self="close"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="descriptionId">
      <div class="modal-container" ref="modalRef">
        <!-- Modal content -->
        <div class="modal-content">
          <!-- Modal header -->
          <div class="modal-header">
            <h3 :id="titleId" class="text-xl font-medium text-gray-900 dark:text-white">
              {{ title || 'Modal' }}
            </h3>
            <BaseButton 
              @click="close" 
              variant="ghost" 
              size="sm" 
              class="ml-auto p-1.5 rounded-lg"
              :aria-label="$t('common.close') || 'Close modal'">
              <svg aria-hidden="true" class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"
                xmlns="http://www.w3.org/2000/svg">
                <path fill-rule="evenodd"
                  d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                  clip-rule="evenodd"></path>
              </svg>
              <span class="sr-only">{{ $t('common.close') || 'Close modal' }}</span>
            </BaseButton>
          </div>
          <!-- Modal body -->
          <div :id="descriptionId" class="modal-body">
            <slot></slot>
          </div>
          <!-- Modal footer -->
          <div v-if="$slots.footer" class="modal-footer">
            <slot name="footer"></slot>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  isOpen: boolean
  title?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { t } = useI18n()
const modalRef = ref<HTMLElement | null>(null)
const titleId = `modal-title-${Math.random().toString(36).substr(2, 9)}`
const descriptionId = `modal-description-${Math.random().toString(36).substr(2, 9)}`

let previouslyFocusedElement: HTMLElement | null = null

const close = () => {
  emit('close')
}

// Keyboard navigation: Escape key to close
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.isOpen) {
    close()
  }
  
  // Focus trap: Tab key within modal
  if (event.key === 'Tab' && props.isOpen && modalRef.value) {
    const focusableElements = modalRef.value.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )
    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]

    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault()
      lastElement?.focus()
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault()
      firstElement?.focus()
    }
  }
}

// Focus management
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    // Store previously focused element
    previouslyFocusedElement = document.activeElement as HTMLElement
    
    nextTick(() => {
      // Focus first focusable element in modal
      if (modalRef.value) {
        const focusableElements = modalRef.value.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        focusableElements[0]?.focus()
      }
      
      // Prevent body scroll when modal is open
      document.body.style.overflow = 'hidden'
    })
  } else {
    // Restore body scroll
    document.body.style.overflow = ''
    
    // Restore focus to previously focused element
    if (previouslyFocusedElement) {
      previouslyFocusedElement.focus()
      previouslyFocusedElement = null
    }
  }
})

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  // Ensure body scroll is restored
  document.body.style.overflow = ''
})
</script>

