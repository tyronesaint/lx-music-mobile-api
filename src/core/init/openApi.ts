import { openApiManager } from '@/core/openApi'
import settingState from '@/store/setting/state'

/**
 * 初始化开放API
 */
export default (setting: LX.AppSetting) => {
  const port = setting['openApi.port'] || 23330
  const bindLan = setting['openApi.bindLan'] || false

  if (setting['openApi.enable']) {
    openApiManager.startServer(port, bindLan)
  }

  // 监听设置变化
  global.state_event.on('configUpdated', (keys: Array<keyof LX.AppSetting>, newSetting: Partial<LX.AppSetting>) => {
    const wasEnabled = settingState.setting['openApi.enable']
    const nowEnabled = newSetting['openApi.enable'] ?? wasEnabled

    if (wasEnabled !== nowEnabled) {
      if (nowEnabled) {
        const port = newSetting['openApi.port'] as number || settingState.setting['openApi.port'] || 23330
        const bindLan = newSetting['openApi.bindLan'] as boolean || settingState.setting['openApi.bindLan'] || false
        openApiManager.startServer(port, bindLan)
      } else {
        openApiManager.stopServer()
      }
    }

    // 如果端口或bindLan变化，需要重启服务
    if (nowEnabled && (
      keys.includes('openApi.port') ||
      keys.includes('openApi.bindLan')
    )) {
      openApiManager.restart()
    }
  })
}
