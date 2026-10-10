# 独立版架构

```text
Android IME / 设置页
  ├─ PinyinDecoder + KeyboardController：离线输入与内部字段路由
  ├─ ApiCredentials：用户Key的Android Keystore加密存储
  ├─ DeepSeekClient：手机直连官方HTTPS接口，结构化结果校验
  ├─ NativeAgent：本机计划、执行、任务状态与产物缓存
  │   ├─ NativeData：有界TXT/CSV/XLSX/DOCX/PDF/图片材料解析
  │   ├─ NativeTaskRules：来源依据、数字、坐标、时间与类型校验
  │   └─ NativeExports：本机OOXML、PDF、图表、图片与ICS文件
  ├─ ChatMemory：本机隔离工作区与生成历史
  └─ DirectSend：用户点选后的版本限定WhatsApp交付状态机
```

没有内置HTTP服务、Mac局域网地址、反向端口映射或开发者APIKey。`AgentApi`保留为界面与本机执行器的内部协议适配层，不再发起项目服务器请求。

模型只返回内容与受限工具参数。本机代码决定实际操作，精确金额用BigDecimal计算；模型不能任意执行代码、读写路径、选收件人或发送消息。

任务执行计划与产物存放在应用私有目录。已经完成的产物与历史可保留，进行中的线程不等于可靠云端队列；系统结束应用进程会中断任务，错误必须显式呈现。

联系人记忆基于受限页面识别，不能跨账号/新窗口仅按同名自动恢复。系统文件选择流程使用一次有时限的本机事务，只恢复原窗口/原工作区；不把另一位联系人的结果接入当前输入框。
