# lx-music-mobile 开放 API 功能实现说明

## 概述

本次实现为 lx-music-mobile 安卓版添加了与桌面版相同的开放 API 支持，实现了本地 HTTP 服务供第三方调用，并配置了 GitHub Actions 自动构建。

## 实现的功能

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/status` | GET | 获取播放状态 |
| `/lyric` | GET | 获取当前歌词 |
| `/play` | GET | 恢复播放 |
| `/pause` | GET | 暂停播放 |
| `/skip-next` | GET | 下一首 |
| `/skip-prev` | GET | 上一首 |
| `/seek` | GET | 跳转到指定时间 (`?time=秒`) |
| `/volume` | GET | 设置音量 (`?volume=0-1`) |
| `/mute` | GET | 静音 |
| `/collect` | GET | 收藏当前歌曲 |
| `/uncollect` | GET | 取消收藏 |

### 设置项

在应用设置中添加了以下选项：
- **启用开放 API** (`openApi.enable`): 是否启用 HTTP 服务
- **端口** (`openApi.port`): HTTP 服务端口，默认 23330
- **鉴权令牌** (`openApi.authToken`): 可选，设置后需要在请求头中携带 `Authorization: Bearer <token>`

## 文件结构

### 新增文件

```
android/app/src/main/java/cn/toside/music/mobile/openapi/
├── OpenApiServer.java    # HTTP 服务器实现 (NanoHTTPD)
├── OpenApiModule.java    # React Native Bridge 模块
└── OpenApiPackage.java   # React Native 包注册

src/core/openApi/
└── index.ts              # JS 端管理器

src/core/init/
└── openApi.ts            # 初始化逻辑

.github/workflows/
└── build-unsigned.yml    # GitHub Actions 构建未签名 APK
```

### 修改文件

```
android/app/src/main/AndroidManifest.xml    # 添加网络权限
android/app/build.gradle                    # 添加 NanoHTTPD 依赖 + 修改签名配置
android/app/src/main/java/.../MainApplication.java  # 注册 OpenApiPackage
src/types/app_setting.d.ts                  # 添加设置类型定义
src/config/defaultSetting.ts                # 添加默认设置
src/core/init/index.ts                      # 集成初始化
```

---

## GitHub Actions 自动构建（重要）

### 使用方法

1. **创建 GitHub 仓库**
   - 在 GitHub 上创建一个新仓库

2. **上传代码**
   ```bash
   # 解压下载的压缩包
   tar -xzvf lx-music-mobile-openapi.tar.gz
   cd lx-music-mobile-master
   
   # 初始化 Git 并推送到 GitHub
   git init
   git add .
   git commit -m "feat: 添加开放 API 支持"
   git branch -M main
   git remote add origin https://github.com/你的用户名/你的仓库名.git
   git push -u origin main
   ```

3. **触发构建**
   - 自动触发：推送到 `main` 或 `master` 分支时自动构建
   - 手动触发：在 GitHub 仓库页面，点击 **Actions** → **Build Unsigned APK** → **Run workflow**

4. **下载 APK**
   - 构建完成后，在 Actions 页面点击对应的 workflow run
   - 在页面底部的 **Artifacts** 区域下载 APK 文件
   - 会生成以下架构的 APK：
     - `arm64-v8a` (大多数现代手机)
     - `armeabi-v7a` (旧款手机)
     - `x86_64` (模拟器/x86 设备)
     - `x86` (旧款模拟器)
     - `universal` (通用版，包含所有架构)

### 构建说明

- **无需签名密钥**：此工作流使用 debug 签名，可直接安装使用
- **构建时间**：约 10-15 分钟
- **APK 说明**：
  - 使用 debug 签名，可直接安装
  - 如需发布到应用商店，需自行配置签名

---

## 本地构建说明

### 环境要求

- Node.js 18+
- Java JDK 17+
- Android SDK (API 21+)
- React Native 开发环境

### 构建步骤

1. 安装依赖：
```bash
npm install
# 或
pnpm install
```

2. 构建 APK：
```bash
cd android
./gradlew assembleRelease
```

构建完成后，APK 文件位于 `android/app/build/outputs/apk/release/` 目录。

---

## 使用说明

### 启用开放 API

1. 打开应用，进入设置页面
2. 找到"开放 API"设置项
3. 启用并配置端口（默认 23330）
4. 可选：设置鉴权令牌

### API 调用示例

```bash
# 获取播放状态
curl http://<手机IP>:23330/status

# 播放/暂停
curl http://<手机IP>:23330/play
curl http://<手机IP>:23330/pause

# 切歌
curl http://<手机IP>:23330/skip-next
curl http://<手机IP>:23330/skip-prev

# 跳转进度
curl "http://<手机IP>:23330/seek?time=60"

# 设置音量
curl "http://<手机IP>:23330/volume?volume=0.5"
```

### 鉴权

如果设置了鉴权令牌，需要在请求头中携带：
```bash
curl -H "Authorization: Bearer your-token" http://<手机IP>:23330/status
```

---

## 与 KTV 后端集成

此实现专为与 KTV 后端服务集成而设计，可在 Android 手机上运行后端服务供 Android TV 使用：

1. 在 Android 手机上安装并运行 lx-music-mobile
2. 启用开放 API 功能
3. 配置 KTV 后端连接到此手机 IP 和端口
4. Android TV 连接到 KTV 后端即可控制播放

---

## 注意事项

1. **网络权限**：应用需要网络权限才能提供 HTTP 服务
2. **防火墙**：确保手机防火墙允许指定端口的入站连接
3. **电池优化**：建议将应用加入电池优化白名单，防止后台被杀
4. **局域网**：HTTP 服务仅监听局域网，不支持公网访问

---

## 未来改进

- [ ] 实现 SSE (`/subscribe-player-status`) 用于实时状态推送
- [ ] 添加更多桌面版兼容端点
- [ ] 支持自定义绑定 IP 地址
- [ ] 添加 HTTPS 支持
