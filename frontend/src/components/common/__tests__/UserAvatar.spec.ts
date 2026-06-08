import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UserAvatar from '../ui/UserAvatar.vue'

describe('UserAvatar', () => {
  it('renders the profile image when an image URL is provided', () => {
    const wrapper = mount(UserAvatar, {
      props: {
        imageUrl: '/profile.png',
        name: 'Novi',
        alt: 'Novi profile',
      },
    })

    const image = wrapper.get('img')
    expect(image.attributes('src')).toBe('/profile.png')
    expect(image.attributes('alt')).toBe('Novi profile')
    expect(wrapper.text()).toBe('')
  })

  it('renders an uppercase fallback initial when no image URL is provided', () => {
    const wrapper = mount(UserAvatar, {
      props: {
        name: 'novi',
      },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toBe('N')
  })

  it('emits image-error when the profile image fails to load', async () => {
    const wrapper = mount(UserAvatar, {
      props: {
        imageUrl: '/profile.png',
      },
    })

    await wrapper.get('img').trigger('error')

    expect(wrapper.emitted('image-error')).toHaveLength(1)
  })
})
