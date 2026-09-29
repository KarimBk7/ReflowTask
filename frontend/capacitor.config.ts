import type { CapacitorConfig } from '@capacitor/cli'

/**
 * The phone app: the web app built for the device (npm run build:device), where everything is
 * planned and stored on the phone. Build it with `npm run build:device && npx cap sync android`.
 */
const config: CapacitorConfig = {
  appId: 'dev.karimbk.reflowtask',
  appName: 'ReflowTask',
  webDir: 'dist-device',
  plugins: {
    LocalNotifications: {
      iconColor: '#4253d4',
    },
  },
}

export default config
