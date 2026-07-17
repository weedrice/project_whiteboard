export interface NotificationStreamConnection {
  connectionId: string
  sessionGeneration: number
}

type ConnectionListener = (connection: NotificationStreamConnection | null) => void

let currentConnection: NotificationStreamConnection | null = null
const listeners = new Set<ConnectionListener>()

export function getNotificationStreamConnection() {
  return currentConnection
}

export function setNotificationStreamConnection(connection: NotificationStreamConnection | null) {
  currentConnection = connection
  listeners.forEach((listener) => listener(connection))
}

export function subscribeNotificationStreamConnection(listener: ConnectionListener) {
  listeners.add(listener)
  listener(currentConnection)
  return () => listeners.delete(listener)
}

export function resetNotificationStreamConnectionForTest() {
  currentConnection = null
  listeners.clear()
}
