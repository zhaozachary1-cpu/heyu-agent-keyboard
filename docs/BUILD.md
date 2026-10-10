# 构建独立Android输入法

支持macOS或Linux/WSL。安装JDK17或更新版本、Python3、Android SDK平台36、Build Tools36.0.0、NDK29.0.14206865及zip工具。配置`ANDROID_HOME`和`ANDROID_NDK_HOME`后执行`bash build.sh`。

依赖：PdfBox-Android 2.0.27.0及其Bouncy Castle 1.72依赖。`android-dependencies.json`固定版本和SHA-256，`scripts/fetch-android-deps.py`只下载清单内Maven Central文件。不要跳过校验。构建目录包含本地缓存，不提交仓库。

构建不要求DeepSeek Key；APK只包含应用代码、词典、固定提示模板和PDF字体资源。模型Key在运行时由用户输入。

## 签名

默认使用`.signing/agent.keystore`，不存在时为本机生成测试签名，密码仅为本地开发默认值。不要公开该文件。正式分发可设置：

- `HEYU_KEYSTORE`：自己的持久签名文件路径
- `HEYU_KEY_ALIAS`：别名
- `HEYU_STORE_PASSWORD`：密码，通过环境或CI秘密注入，不写进脚本或日志

保留签名私钥备份，否则不能给已有安装进行兼容升级。GitHub Release APK和自行构建APK签名可能不同，无法直接互相覆盖；不要为了升级而无提示清除用户数据。

## 检查与安装

```sh
bash scripts/test-release.sh
adb -s YOUR_DEVICE_SERIAL install -r output/heyu-agent-1.0.0.apk
```

ADB仅用于安装/测试。使用时不需要ADB、USB、端口映射或Mac服务。手机仍需能访问DeepSeek API的网络。

发布前运行`python3 scripts/check-publish.py`，检查Git暂存文件范围、敏感数据以及APK是否混入旧服务配置或QA入口。
