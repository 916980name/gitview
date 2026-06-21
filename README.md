# GitView

本地加密 Git 仓库同步与 Markdown 查看工具。通过局域网将电脑上的 Git 仓库安全同步到 Android 手机，所有文件使用 AES-256-GCM 加密存储，密钥由设备硬件安全模块（StrongBox/TEE）保护。

## 电脑端：启用 Git Daemon

在包含 Git 仓库的目录下运行 `git daemon`，手机即可通过局域网 clone/pull。

### 快速开始

```bash
# 进入你的项目父目录（包含一个或多个 git 仓库）
cd ~/projects

# 启动 git daemon，导出该目录下所有仓库
git daemon --reuseaddr --base-path=. --export-all --enable=receive-pack
```

| 参数 | 说明 |
|------|------|
| `--reuseaddr` | 允许快速重启 |
| `--base-path=.` | 以此目录为根路径 |
| `--export-all` | 导出所有仓库（否则需要每个仓库有 `git-daemon-export-ok` 文件） |
| `--enable=receive-pack` | 启用接收（本 App 仅读取，此参数可省略） |

> ⚠️ `git daemon` 仅监听局域网，不暴露到互联网。防火墙通常不会拦截。

### 查看电脑 IP 地址

```bash
# macOS / Linux
ifconfig | grep "inet " | grep -v 127.0.0.1

# 或
ipconfig getifaddr en0    # macOS Wi-Fi
hostname -I                # Linux
```

记下类似 `192.168.1.100` 的地址。

### 使用示例

```
电脑 IP:    192.168.1.100
仓库路径:   ~/projects/my-docs
git daemon: 在 ~/projects 目录下启动
手机 URL:   git://192.168.1.100/my-docs.git
```

### 仅导出特定仓库

如果不希望导出所有仓库，可进入单个仓库目录：

```bash
cd ~/projects/my-docs
git daemon --reuseaddr --base-path=. --export-all
```

手机 URL 则为：`git://192.168.1.100/`

## 手机端：使用 GitView

### 添加仓库

1. 确保手机与电脑连接**同一 WiFi**
2. 电脑端已启动 `git daemon`
3. 打开 GitView，点击右下角 **+** 按钮
4. 输入仓库 URL（格式 `git://<电脑IP>/<路径>.git`）
5. 点击「Clone Repository」
6. 等待 clone 完成，仓库将出现在列表中

### 同步更新

- 在仓库列表中点击仓库右侧的 **🔄 刷新图标**
- 如有新提交，文件将增量更新
- 显示变更文件数量

### 浏览文件

1. 点击仓库进入文件树
2. 点击文件夹展开/折叠
3. 点击 `.md` 文件查看渲染后的 Markdown

### 删除仓库

- 长按仓库卡片 → 确认删除

### 查看加密状态

- 点击右上角 **⚙️ 设置**
- 查看当前加密方案：StrongBox / TEE / 软件降级
- 显示加密算法、密钥长度等信息

## 安全架构

```
┌──────────────────────────────────────────┐
│  电脑 (局域网)                            │
│  git daemon --export-all                 │
│  仅监听内网，不暴露到公网                 │
└─────────────┬────────────────────────────┘
              │ git://192.168.x.x/repo.git
              │ (明文，但不出局域网)
              ▼
┌──────────────────────────────────────────┐
│  手机                                     │
│                                           │
│  JGit clone/pull ──▶ temp/ (明文临时)     │
│       │                                   │
│       ▼                                   │
│  AES-256-GCM 加密 ──▶ repos/ (密文持久)  │
│       │                                   │
│       ▼                                   │
│  加密等级: StrongBox > TEE > 软件         │
│                                           │
│  ┌─────────────────────────────────┐      │
│  │ Keystore (TEE/StrongBox)        │      │
│  │   KEK 永不出硬件                │      │
│  │   仅 App 启动时解密 DEK          │      │
│  └─────────────────────────────────┘      │
└──────────────────────────────────────────┘
```

- **传输层**：局域网 `git://`，明文但不出内网
- **存储层**：`/data/data/.../files/` Internal Storage，Linux DAC + SELinux 隔离
- **加密层**：AES-256-GCM，KEK/DEK 双密钥，硬件安全模块保护
- **防篡改**：GCM 认证加密，任何字节改动导致解密失败
- **临时清理**：关闭仓库或 App 被杀时清除临时明文

## 注意事项

- 手机和电脑需在同一 WiFi 局域网
- 仅支持 `git://` 协议（不支持 `https://` 或 `ssh://`）
- 仅读取操作（clone/pull），不会向远程推送
- 用户修改锁屏密码会导致密钥失效，需重新 clone 所有仓库
- 仓库较大时首次 clone 和同步可能需要一些时间
