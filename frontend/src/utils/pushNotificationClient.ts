export interface PushNotificationWindowClient {
  url: string
  focus: () => Promise<unknown>
}

export async function focusOrOpenPushNotificationTarget(
  clients: readonly PushNotificationWindowClient[],
  targetUrl: string,
  openWindow: (url: string) => Promise<unknown>,
): Promise<void> {
  const exactClient = clients.find((client) => client.url === targetUrl)
  if (exactClient) {
    await exactClient.focus()
    return
  }

  // A different app route may contain an unsaved draft. The service worker cannot
  // inspect page-local blockers, so only exact targets are safe to reuse.
  await openWindow(targetUrl)
}
