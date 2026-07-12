import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import AdminAuditLogTable from '../AdminAuditLogTable.vue'
import type { ModerationAuditLog } from '@/types/admin'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => ({
      'admin.dashboard.auditActions.POST_BLIND': 'Hide post',
      'admin.dashboard.auditTargets.POST': 'Post',
    } as Record<string, string>)[key] ?? key,
    locale: ref('en'),
  }),
}))

const audit: ModerationAuditLog = {
  auditId: 1,
  actorType: 'USER',
  actorUserId: 7,
  actorDisplayName: 'Moderator',
  adminId: null,
  action: 'POST_BLIND',
  targetType: 'POST',
  targetId: 11,
  boardId: 2,
  boardName: 'Vue',
  boardUrl: 'vue',
  reason: 'Policy',
  createdAt: '2026-07-12T10:00:00',
}

const mountTable = (showBoardUrl = false) => mount(AdminAuditLogTable, {
  props: {
    audits: [audit],
    caption: 'Moderation audit logs',
    emptyText: 'No logs',
    showBoardUrl,
  },
})

describe('AdminAuditLogTable', () => {
  it('provides a caption and scoped column headers', () => {
    const wrapper = mountTable()

    expect(wrapper.get('caption').text()).toBe('Moderation audit logs')
    expect(wrapper.get('caption').classes()).toContain('sr-only')
    expect(wrapper.findAll('th').every((header) => header.attributes('scope') === 'col')).toBe(true)
    expect(wrapper.text()).toContain('Moderator')
    expect(wrapper.text()).toContain('Hide post')
    expect(wrapper.text()).toContain('Post #11')
    expect(wrapper.get('[role="region"]').attributes()).toMatchObject({
      'aria-label': 'Moderation audit logs',
      tabindex: '0',
    })
    expect(wrapper.text()).not.toContain('admin.dashboard.auditBoardUrl')
  })

  it('optionally shows the board URL column', () => {
    const wrapper = mountTable(true)

    expect(wrapper.text()).toContain('admin.dashboard.auditBoardUrl')
    expect(wrapper.text()).toContain('vue')
  })
})
