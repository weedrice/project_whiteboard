<template>
  <Teleport to="body">
    <div v-if="isOpen" class="modal-overlay" :style="{ zIndex: String(zIndex) }" @click.self="handleBackdropClick" role="dialog" aria-modal="true"
      :aria-labelledby="titleId" :aria-describedby="description ? descriptionId : undefined">
      <div :class="['modal-container', sizeClass, { 'modal-container-mobile-full': mobileFull && !mobileFitContent, 'modal-container-mobile-fit': mobileFitContent }]" ref="modalRef">
        <!-- Modal content -->
        <div class="modal-content">
          <!-- Modal header -->
          <div :class="['modal-header', headerClass]">
            <component :is="titleTag" :id="titleId" class="text-xl font-medium nv-title">
              {{ title || 'Modal' }}
            </component>
            <BaseButton
              @click="close"
              variant="ghost"
              size="sm"
              :class="['ml-auto p-1.5 rounded-lg', closeButtonClass]"
              :aria-label="closeAriaLabel || $t('common.close')"
            >
              <svg aria-hidden="true" class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"
                xmlns="http://www.w3.org/2000/svg">
                <path fill-rule="evenodd"
                  d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                  clip-rule="evenodd"></path>
              </svg>
              <span class="sr-only">{{ closeAriaLabel || $t('common.close') }}</span>
            </BaseButton>
          </div>
          <p v-if="description" :id="descriptionId" class="sr-only">{{ description }}</p>
          <!-- Modal body -->
          <div :class="['modal-body', bodyClass]">
            <slot></slot>
          </div>
          <!-- Modal footer -->
          <div v-if="$slots.footer" :class="['modal-footer', footerAlignClass, footerClass]">
            <slot name="footer"></slot>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script lang="ts">
const modalStack: symbol[] = []

function registerOpenModal(modalId: symbol) {
  if (!modalStack.includes(modalId)) {
    modalStack.push(modalId)
  }
}

function unregisterOpenModal(modalId: symbol) {
  const index = modalStack.indexOf(modalId)
  if (index !== -1) {
    modalStack.splice(index, 1)
  }
}

function isTopOpenModal(modalId: symbol) {
  return modalStack[modalStack.length - 1] === modalId
}
</script>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick, toRef, useId } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useEventListener } from '@/composables/useEventListener'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { useBodyScrollLock } from '@/composables/useBodyScrollLock'

const props = withDefaults(defineProps<{
  isOpen: boolean
  title?: string
  description?: string
  titleTag?: 'h1' | 'h2' | 'h3'
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
  mobileFitContent?: boolean
  zIndex?: number
  closeAriaLabel?: string
  closeButtonClass?: string
  headerClass?: string
  bodyClass?: string
  footerClass?: string
  footerAlign?: 'start' | 'center' | 'end' | 'between'
  closeOnBackdrop?: boolean
  closeOnEscape?: boolean
}>(), {
  size: 'md',
  description: '',
  titleTag: 'h2',
  mobileFull: false,
  mobileFitContent: false,
  zIndex: 50,
  closeAriaLabel: '',
  closeButtonClass: '',
  headerClass: '',
  bodyClass: '',
  footerClass: '',
  footerAlign: 'end',
  closeOnBackdrop: true,
  closeOnEscape: true,
})

const emit = defineEmits<{
  (e: 'close'): void
}>()

const sizeClass = computed(() => {
  const sizes: Record<string, string> = {
    'sm': 'max-w-sm',
    'md': 'max-w-md',
    'lg': 'max-w-lg',
    'xl': 'max-w-xl',
    '2xl': 'max-w-2xl',
    'full': 'max-w-[90vw]'
  }
  return sizes[props.size] || 'max-w-md'
})

const footerAlignClass = computed(() => {
  const classes = {
    start: 'justify-start',
    center: 'justify-center',
    end: 'justify-end',
    between: 'justify-between'
  }
  return classes[props.footerAlign]
})

const modalRef = ref<HTMLElement | null>(null)
const modalId = useId()
const modalStackId = Symbol(`base-modal-${modalId}`)
const titleId = `${modalId}-title`
const descriptionId = `${modalId}-description`
const { trapFocus, restoreFocus } = useFocusTrap(modalRef, () => props.isOpen)
useBodyScrollLock(toRef(props, 'isOpen'))

const close = () => {
  emit('close')
}

const handleBackdropClick = () => {
  if (props.closeOnBackdrop) {
    close()
  }
}

const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.isOpen && props.closeOnEscape && isTopOpenModal(modalStackId)) {
    close()
  }
}

// Focus management
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    registerOpenModal(modalStackId)
    nextTick(() => {
      if (!props.isOpen) return
      trapFocus()
    })
  } else {
    unregisterOpenModal(modalStackId)
    restoreFocus()
  }
}, { immediate: true })

useEventListener(() => document, 'keydown', handleKeyDown)

onUnmounted(() => {
  // Ensure body scroll is restored
  unregisterOpenModal(modalStackId)
  restoreFocus()
})
</script>
