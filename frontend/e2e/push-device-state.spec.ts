import { expect, test } from '@playwright/test'
import { installMockApi, mockSettings } from './fixtures/mockApi'

for (const initiallyRegistered of [false, true]) {
  test(initiallyRegistered
    ? 'repairs after refreshed settings report that another device removed all subscriptions'
    : 'repairs this browser when only another device remains registered', async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    await page.addInitScript(() => {
      const subscription = {
        endpoint: 'https://push.example/device-b',
        options: { applicationServerKey: new Uint8Array([1, 2, 3]).buffer },
        toJSON: () => ({ keys: { p256dh: 'test-key', auth: 'test-auth' } }),
        unsubscribe: async () => true,
      }
      Object.defineProperty(navigator.serviceWorker, 'getRegistration', {
        configurable: true,
        value: async () => ({ active: {}, pushManager: { getSubscription: async () => subscription } }),
      })
      Object.defineProperty(Notification, 'permission', { configurable: true, get: () => 'granted' })
      Object.defineProperty(Notification, 'requestPermission', { configurable: true, value: async () => 'granted' })
    })
    let currentEndpointRegistered = initiallyRegistered
    let accountEnabled = true
    let settingsUpdates = 0
    let settingsReadsAfterRemoval = 0
    let statusLookups = 0
    let settings = { ...mockSettings }
    const registrations: Array<{ endpoint: string }> = []
    await page.route('**/api/v1/users/me/settings', (route) => {
      if (route.request().method() === 'PUT') {
        settings = { ...settings, ...route.request().postDataJSON() }
        settingsUpdates++
      } else if (!accountEnabled) {
        settingsReadsAfterRemoval++
      }
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ success: true, data: { ...settings, pushEnabled: accountEnabled } }),
      })
    })
    await page.route('**/api/v1/push/public-key', (route) => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { enabled: true, publicKey: 'AQID' } }),
    }))
    await page.route('**/api/v1/users/me/push-subscriptions**', (route) => {
      const payload = route.request().postDataJSON() as { endpoint: string }
      expect(payload.endpoint).toBe('https://push.example/device-b')
      const statusLookup = new URL(route.request().url()).pathname.endsWith('/status')
      if (statusLookup) statusLookups++
      if (!statusLookup) {
        registrations.push(payload)
        currentEndpointRegistered = true
        accountEnabled = true
      }
      return route.fulfill({
        status: statusLookup ? 200 : 201, contentType: 'application/json',
        body: JSON.stringify({ success: true, data: statusLookup ? currentEndpointRegistered : { subscriptionId: 2, endpoint: payload.endpoint } }),
      })
    })

    await page.goto('/mypage/settings#notifications')
    const repair = page.getByRole('button', { name: '브라우저 푸시 복구', exact: true })
    if (initiallyRegistered) {
      await expect(repair).toBeDisabled()
      await expect.poll(() => statusLookups).toBe(1)
      currentEndpointRegistered = false
      accountEnabled = false
      await page.locator('#settings-general-tab').click()
      await page.locator('#general select').first().selectOption(settings.theme === 'LIGHT' ? 'DARK' : 'LIGHT')
      await page.locator('#general').getByRole('button').click()
      await expect.poll(() => settingsUpdates).toBe(1)
      await expect.poll(() => settingsReadsAfterRemoval).toBeGreaterThan(0)
      await page.locator('#settings-notifications-tab').click()
      expect(statusLookups).toBe(1)
      await expect(repair).toBeEnabled()

      // Re-registering only the other device must not revive this device's old status.
      accountEnabled = true
      await page.locator('#settings-general-tab').click()
      await page.locator('#general select').first().selectOption(settings.theme === 'LIGHT' ? 'DARK' : 'LIGHT')
      const refreshedSettings = page.waitForResponse((response) =>
        response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/v1/users/me/settings')
      await page.locator('#general').getByRole('button').click()
      await expect.poll(() => settingsUpdates).toBe(2)
      const response = await refreshedSettings
      await response.finished()
      expect((await response.json()).data.pushEnabled).toBe(true)
      await expect(page.locator('#general').getByRole('status')).toHaveText('설정이 저장되었습니다.')
      await page.locator('#settings-notifications-tab').click()
      expect(statusLookups).toBe(1)
    }
    await expect(repair).toBeEnabled()
    await repair.click()
    await expect.poll(() => registrations.length).toBe(1)
    await expect(repair).toBeDisabled()
  })
}
