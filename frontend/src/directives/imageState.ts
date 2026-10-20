import type { ObjectDirective } from 'vue'
import i18n from '@/i18n'
import { createImageStateController, type ImageStateOptions } from '@/utils/imageState'

const controllers = new WeakMap<HTMLImageElement, ReturnType<typeof createImageStateController>>()

export const vImageState: ObjectDirective<HTMLImageElement, ImageStateOptions | undefined> = {
  beforeMount(image, binding) {
    const controller = createImageStateController(image, () => i18n.global.t('common.imageLoadError'))
    controllers.set(image, controller)
    controller.refresh(binding.value)
  },
  mounted(image, binding) {
    controllers.get(image)?.refresh(binding.value)
  },
  updated(image, binding) {
    controllers.get(image)?.refresh(binding.value)
  },
  beforeUnmount(image) {
    controllers.get(image)?.destroy()
    controllers.delete(image)
  },
}
