import { NativeModules, NativeEventEmitter } from 'react-native'
import settingState from '@/store/setting/state'
import playerState from '@/store/player/state'
import { playNext, playPrev, play, pause } from '@/core/player/player'
import { setVolume, setCurrentTime } from '@/plugins/player/utils'
import { updateSetting } from '@/core/common'

const { OpenApiModule } = NativeModules

export type OpenApiStatus = {
  status: boolean
  message: string
  address: string
}

type PlayerStatusForApi = {
  // 播放状态
  status: boolean  // 是否正在播放
  // 当前歌曲信息
  name: string
  singer: string
  albumName: string
  // 歌词
  lyricLineText: string  // 当前歌词行
  lyric: string  // 完整歌词
  tlyric: string  // 翻译歌词
  rlyric: string  // 罗马音歌词
  lxlyric: string  // LX格式歌词
  // 播放进度
  duration: number  // 总时长(秒)
  progress: number  // 当前进度(秒)
  playbackRate: number  // 播放速率
  // 音量
  volume: number
}

class OpenApiManager {
  private isInitialized = false
  private eventListener: any = null
  private eventEmitter: NativeEventEmitter | null = null
  private currentStatus: OpenApiStatus = {
    status: false,
    message: '',
    address: '',
  }

  async startServer(port: number, bindLan: boolean): Promise<OpenApiStatus> {
    if (!OpenApiModule) {
      console.warn('OpenApiModule is not available')
      return { status: false, message: 'OpenApiModule not available', address: '' }
    }

    try {
      // 先停止现有服务
      await this.stopServer()

      const result = await OpenApiModule.startServer(port, bindLan)
      this.currentStatus = result
      this.isInitialized = result.status

      if (result.status) {
        this.setupEventListeners()
        this.syncPlayerStatus()
        console.log(`OpenAPI server started on ${result.address}`)
      } else {
        console.error(`OpenAPI server failed to start: ${result.message}`)
      }

      return result
    } catch (error: any) {
      console.error('Failed to start OpenAPI server:', error)
      return { status: false, message: error?.message || 'Unknown error', address: '' }
    }
  }

  async stopServer(): Promise<void> {
    if (!OpenApiModule) return

    this.removeEventListeners()

    try {
      await OpenApiModule.stopServer()
      this.isInitialized = false
      this.currentStatus = { status: false, message: '', address: '' }
      console.log('OpenAPI server stopped')
    } catch (error: any) {
      console.error('Failed to stop OpenAPI server:', error)
    }
  }

  async getStatus(): Promise<OpenApiStatus> {
    if (!OpenApiModule) {
      return { status: false, message: 'OpenApiModule not available', address: '' }
    }

    try {
      const status = await OpenApiModule.getStatus()
      this.currentStatus = status
      return status
    } catch (error: any) {
      return { status: false, message: error?.message || 'Unknown error', address: '' }
    }
  }

  private setupEventListeners() {
    if (!OpenApiModule) return

    this.eventEmitter = new NativeEventEmitter(OpenApiModule)
    
    // 监听来自原生层的控制命令
    this.eventListener = this.eventEmitter.addListener('OpenApiControl', (event: { action: string; data?: any }) => {
      this.handleControlEvent(event)
    })

    // 监听播放状态变化并同步
    global.state_event.on('playStateChanged', this.onPlayStateChanged)
    global.state_event.on('playMusicInfoChanged', this.onPlayMusicInfoChanged)
    global.state_event.on('playProgressChanged', this.onPlayProgressChanged)
    global.state_event.on('playerMusicInfoChanged', this.onPlayerMusicInfoChanged)
  }

  private removeEventListeners() {
    if (this.eventListener) {
      this.eventListener.remove()
      this.eventListener = null
    }
    
    global.state_event.off('playStateChanged', this.onPlayStateChanged)
    global.state_event.off('playMusicInfoChanged', this.onPlayMusicInfoChanged)
    global.state_event.off('playProgressChanged', this.onPlayProgressChanged)
    global.state_event.off('playerMusicInfoChanged', this.onPlayerMusicInfoChanged)
  }

  private onPlayStateChanged = () => this.syncPlayerStatus()
  private onPlayMusicInfoChanged = () => this.syncPlayerStatus()
  private onPlayProgressChanged = () => this.syncPlayerStatus()
  private onPlayerMusicInfoChanged = () => this.syncPlayerStatus()

  private handleControlEvent(event: { action: string; data?: any }) {
    console.log('OpenAPI control event:', event.action, event.data)
    switch (event.action) {
      case 'play':
        play()
        break
      case 'pause':
        void pause()
        break
      case 'skip-next':
        void playNext()
        break
      case 'skip-prev':
        void playPrev()
        break
      case 'seek': {
        const offset = event.data?.offset
        if (typeof offset === 'number' && offset >= 0) {
          void setCurrentTime(offset)
        }
        break
      }
      case 'volume': {
        const volume = event.data?.volume
        if (typeof volume === 'number' && volume >= 0 && volume <= 100) {
          const vol = volume / 100
          updateSetting({ 'player.volume': vol })
          void setVolume(vol)
        }
        break
      }
      case 'mute': {
        const mute = event.data?.mute
        if (mute === true) {
          updateSetting({ 'player.volume': 0 })
          void setVolume(0)
        } else if (mute === false) {
          const lastVol = settingState.setting['player.volume'] || 0.5
          updateSetting({ 'player.volume': lastVol })
          void setVolume(lastVol)
        }
        break
      }
      case 'collect':
        // TODO: 实现收藏功能
        break
      case 'uncollect':
        // TODO: 实现取消收藏功能
        break
    }
  }

  private syncPlayerStatus() {
    if (!this.isInitialized || !OpenApiModule) return

    const status = this.getPlayerStatus()
    OpenApiModule.updatePlayerStatus(status)
  }

  private getPlayerStatus(): PlayerStatusForApi {
    const { isPlay, progress, playMusicInfo, musicInfo } = playerState
    
    // 获取歌曲信息
    let name = ''
    let singer = ''
    let albumName = ''
    let lyric = ''
    let tlyric = ''
    let rlyric = ''
    let lxlyric = ''

    if (playMusicInfo.musicInfo) {
      const info = playMusicInfo.musicInfo as LX.Music.MusicInfo
      name = info.name || ''
      singer = info.singer || ''
      albumName = info.meta?.albumName || ''
    }

    // 从 musicInfo 获取歌词
    if (musicInfo) {
      lyric = musicInfo.lrc || ''
      tlyric = musicInfo.tlrc || ''
      rlyric = musicInfo.rlrc || ''
      lxlyric = musicInfo.lxlrc || ''
    }

    // 获取当前歌词行
    let lyricLineText = ''
    if (playerState.lastLyric) {
      lyricLineText = playerState.lastLyric
    }

    return {
      status: isPlay,
      name,
      singer,
      albumName,
      lyricLineText,
      lyric,
      tlyric,
      rlyric,
      lxlyric,
      duration: progress.maxPlayTime || 0,
      progress: progress.nowPlayTime || 0,
      playbackRate: 1,
      volume: Math.round((settingState.setting['player.volume'] || 1) * 100),
    }
  }

  // 重新启动服务（用于设置变更后）
  async restart() {
    const setting = settingState.setting
    const enable = setting['openApi.enable']
    const port = setting['openApi.port'] || 23330
    const bindLan = setting['openApi.bindLan'] || false

    await this.stopServer()

    if (enable) {
      await this.startServer(port, bindLan)
    }
  }

  // 根据设置初始化
  async initFromSetting() {
    const setting = settingState.setting
    const enable = setting['openApi.enable']
    const port = setting['openApi.port'] || 23330
    const bindLan = setting['openApi.bindLan'] || false

    if (enable) {
      await this.startServer(port, bindLan)
    }
  }
}

export const openApiManager = new OpenApiManager()
export default openApiManager
