import { memo, useCallback, useEffect, useState, useRef } from 'react'
import { View } from 'react-native'

import CheckBoxItem from '../../components/CheckBoxItem'
import InputItem from '../../components/InputItem'
import Section from '../../components/Section'
import { updateSetting } from '@/core/common'
import { useSettingValue } from '@/store/setting/hook'
import { useI18n } from '@/lang'
import { createStyle, toast } from '@/utils/tools'
import { openApiManager } from '@/core/openApi'
import Text from '@/components/common/Text'
import { useTheme } from '@/store/theme/hook'
import { getWIFIIPV4Address } from '@/utils/nativeModules/utils'

export default memo(() => {
  const t = useI18n()
  const theme = useTheme()
  const isEnableOpenApi = useSettingValue('openApi.enable')
  const port = useSettingValue('openApi.port')
  const bindLan = useSettingValue('openApi.bindLan')
  const [address, setAddress] = useState('')
  const [serverStatus, setServerStatus] = useState<string>('')
  const isUnmountedRef = useRef(true)

  useEffect(() => {
    isUnmountedRef.current = false
    void getWIFIIPV4Address().then(addr => {
      if (isUnmountedRef.current) return
      setAddress(addr || '')
    })

    // 获取服务器状态
    openApiManager.getStatus().then(status => {
      if (isUnmountedRef.current) return
      setServerStatus(status.status 
        ? t('openapi_status_running', { address: status.address }) 
        : t('openapi_status_stopped'))
    })

    return () => {
      isUnmountedRef.current = true
    }
  }, [t])

  // 监听服务状态变化
  useEffect(() => {
    const checkStatus = async () => {
      const status = await openApiManager.getStatus()
      if (!isUnmountedRef.current) {
        setServerStatus(status.status 
          ? t('openapi_status_running', { address: status.address }) 
          : t('openapi_status_stopped'))
      }
    }
    
    // 定期检查状态
    const interval = setInterval(checkStatus, 3000)
    return () => clearInterval(interval)
  }, [t])

  const handleSetEnable = useCallback((enable: boolean) => {
    updateSetting({ 'openApi.enable': enable })
    
    if (enable) {
      toast(t('openapi_starting'))
      // 启动服务
      setTimeout(() => {
        openApiManager.startServer(port || 23330, bindLan || false)
      }, 100)
    } else {
      toast(t('openapi_stopping'))
      openApiManager.stopServer()
    }
    
    // 更新状态
    setTimeout(async () => {
      const status = await openApiManager.getStatus()
      if (!isUnmountedRef.current) {
        setServerStatus(status.status 
          ? t('openapi_status_running', { address: status.address }) 
          : t('openapi_status_stopped'))
      }
    }, 1000)
  }, [t, port, bindLan])

  const handleSetBindLan = useCallback((enable: boolean) => {
    updateSetting({ 'openApi.bindLan': enable })
    
    if (isEnableOpenApi) {
      toast(t('openapi_restarting'))
      setTimeout(() => {
        openApiManager.restart()
      }, 100)
    }
  }, [t, isEnableOpenApi])

  const handlePortChange = useCallback((value: string, callback: (v: string) => void) => {
    const portNum = parseInt(value, 10)
    if (value && (isNaN(portNum) || portNum < 1 || portNum > 65535)) {
      toast(t('openapi_port_invalid'))
      return
    }
    if (portNum) {
      updateSetting({ 'openApi.port': portNum })
      callback(String(portNum))
    }
  }, [t])

  return (
    <Section title={t('setting_openapi')}>
      <View style={styles.infoContent}>
        <CheckBoxItem check={isEnableOpenApi} label={t('openapi_enable')} onChange={handleSetEnable} />
        <CheckBoxItem check={bindLan} label={t('openapi_bind_lan')} onChange={handleSetBindLan} />
        {address && bindLan ? (
          <Text style={styles.text} size={13}>{t('openapi_address', { address, port })}</Text>
        ) : null}
        <Text style={styles.text} size={13}>{t('openapi_status', { status: serverStatus })}</Text>
      </View>
      <View style={styles.inputContent}>
        <InputItem
          label={t('openapi_port_label')}
          value={String(port || 23330)}
          onChanged={handlePortChange}
          inputMode="numeric"
          placeholder="23330"
          editable={!isEnableOpenApi}
        />
      </View>
      <View style={styles.helpContent}>
        <Text style={styles.helpText} size={12} color={theme['c-primary-font']}>
          {t('openapi_help')}
        </Text>
      </View>
    </Section>
  )
})


const styles = createStyle({
  infoContent: {
    marginTop: 5,
  },
  text: {
    marginLeft: 25,
    marginTop: 5,
  },
  inputContent: {
    marginTop: 8,
  },
  helpContent: {
    marginTop: 15,
    paddingHorizontal: 15,
  },
  helpText: {
    lineHeight: 18,
    opacity: 0.6,
  },
})
