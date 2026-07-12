import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, reactive } from 'vue'
import { useRouteFocusManagement } from '../useRouteFocusManagement'

const mountHarness = (route: { path: string; hash: string }) => mount(defineComponent({
  setup() {
    return useRouteFocusManagement(route)
  },
  template: '<p role="status">{{ routeAnnouncement }}</p>',
}))

afterEach(() => {
  document.body.innerHTML = ''
})

describe('useRouteFocusManagement', () => {
  it('focuses and announces the new page heading when the route path changes', async () => {
    const route = reactive({ path: '/first', hash: '' })
    const heading = document.createElement('h1')
    heading.textContent = 'Second page'
    document.body.appendChild(heading)
    const wrapper = mountHarness(route)

    route.path = '/second'
    await flushPromises()

    expect(document.activeElement).toBe(heading)
    expect(heading.getAttribute('tabindex')).toBe('-1')
    expect(wrapper.get('[role="status"]').text()).toBe('Second page')
  })

  it('focuses a hash target while announcing the page heading', async () => {
    const route = reactive({ path: '/guide', hash: '' })
    const heading = document.createElement('h1')
    heading.textContent = 'Guide'
    const section = document.createElement('section')
    section.id = 'details'
    document.body.append(heading, section)
    const wrapper = mountHarness(route)

    route.hash = '#details'
    await flushPromises()

    expect(document.activeElement).toBe(section)
    expect(wrapper.text()).toBe('Guide')
  })

  it('falls back safely when a route contains a malformed encoded hash', async () => {
    const route = reactive({ path: '/guide', hash: '' })
    const heading = document.createElement('h1')
    heading.textContent = 'Guide'
    document.body.appendChild(heading)
    const wrapper = mountHarness(route)

    route.hash = '#%'
    await flushPromises()

    expect(document.activeElement).toBe(heading)
    expect(wrapper.text()).toBe('Guide')
  })
})
