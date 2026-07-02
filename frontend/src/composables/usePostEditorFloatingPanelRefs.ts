import type { Ref } from 'vue'

function assignElementRef(target: Ref<HTMLElement | null>) {
  return (value: HTMLElement | null) => {
    target.value = value
  }
}

export function usePostEditorFloatingPanelRefs(refs: {
  slashPopoverRef: Ref<HTMLElement | null>
  colorPanelRef: Ref<HTMLElement | null>
  linkPopoverRef: Ref<HTMLElement | null>
  imageAltPopoverRef: Ref<HTMLElement | null>
  tablePopoverRef: Ref<HTMLElement | null>
}) {
  return {
    assignSlashPopover: assignElementRef(refs.slashPopoverRef),
    assignColorPanel: assignElementRef(refs.colorPanelRef),
    assignLinkPopover: assignElementRef(refs.linkPopoverRef),
    assignImageAltPopover: assignElementRef(refs.imageAltPopoverRef),
    assignTablePopover: assignElementRef(refs.tablePopoverRef),
  }
}
