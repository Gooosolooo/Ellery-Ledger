# Ellery Ledger · 本地识别版

当前测试版：0.2.0。界面参考 Focus Buddy 的圆角卡片和顶部标签布局，使用蓝色风格；“设置”中提供 Excel 导入导出和完整备份。

为三星 S24 Ultra / One UI 8.5 设计的 Android 初版。代码采用 Java、清晰职责拆分、常量命名、参数化数据库操作、资源关闭和 BigDecimal 金额处理等规范。

## 记账规则

- 手动点击快捷设置里的“识别当前账单”才截屏；也支持选择图片、将系统截图分享到 App。
- 内置 ML Kit 中文 OCR 模型，本地识别；合并清单中移除 INTERNET 权限。
- 识别仅生成待核对草稿，不会自动入账。通知只有“核对并填写评价”入口。
- 编辑店铺／品牌、商品、日期、金额、类别和备注，点击“确认并保存账目”才入账。
- 事项由“店铺／品牌 + 商品名称”组合。商品缺失可保存草稿；只填写其中一项时，入账前再次提示。
- 备注是用户自己的评价，识别器不会从截图填写备注。
- 默认尝试完整识别。首页可切换“只识别金额，其他信息手填”，切换只影响后续识别；两种模式均须人工确认。
- 未确认草稿在离开编辑页时保存。已确认账目必须点击确认按钮才能保存修改。
- 原分类：餐饮、享受、交通、提升、日用、社交、穿搭、咪用、大额。

## 导入、导出和备份

- Excel 导入：选择原来的 `.xlsx`，只读取 `1.明细` 的日期、事项、类别、金额、备注。导入前展示前五条预览及跳过行号，确认后批量导入旧账。原文件不被修改。
- 旧事项无法可靠拆分店铺和商品时，完整放进商品字段，保留原文。
- 缺失日期、事项、类别、有效金额的记录跳过并提示行号；公式金额读取文件中缓存结果。打开原文件重新计算并保存，可更新公式缓存。
- Excel 导出：新建一个含五列明细的 `.xlsx`，仅含已确认账目；日期、金额为数值单元格，备注和事项为文本。不会复制原文件的汇总查询、旅游页及样式。
- 完整备份：JSON 包含稳定标识、拆分后的店铺和商品、草稿、评价、原始 OCR 文字；恢复采用合并，同一标识不覆盖现有记录。
- Excel 导入按内容与原行号生成标识。同一原文件重复导入跳过已有标识；重新排序、更改内容、重新导入 App 导出的 Excel 仍可能产生重复。换机恢复请使用 JSON 备份。
- 备份由系统文件选择器决定保存位置，可选择本地文件夹或已接入的 OneDrive。该版本没有定时自动备份。请在重要修改后导出并将备份另存到手机以外的位置。
- JSON 备份是明文，包含评价和 OCR 文字；截图本身不保存。自动云备份关闭，卸载前需要手动备份。

## 首次设置

1. 安装测试 APK，允许通知，并在系统通知设置中允许弹出显示。
2. 打开 App 的“设置支付页面快捷截屏”，阅读用途说明后启用无障碍服务。
3. 编辑系统快捷设置面板，添加“识别当前账单”。
4. 停留在可截图的支付详情，点击“识别当前账单”，等面板收起后截屏识别。
5. 打开待核对通知，补充商品和评价，确认入账。

不启用无障碍时，仍可以手动截图并分享给 App。禁止截图的支付窗口无法识别，应切换到可截图的交易详情或手工录入。通知被关闭时草稿仍会保存，可从 App 列表查看。

## 当前识别边界

采用本地 OCR + 坐标分栏重建 + 字段别名和近似标签匹配。适配微信、支付宝账单，以及淘宝、拼多多常见订单详情；优先实付款和支付时间，商品名称支持店铺卡片回退。具体规则、搜索参考和验证范围见 RECOGNITION.md。仍需核对真实页面的识别结果。

没有实付标签时，会把唯一一行独立的货币金额或两位小数金额作为待核对候选；这也可能是页面上的其他金额，必须核对。多金额冲突时留空。仅金额模式不会填店铺、商品或日期。识别模式开关目前不包含在 JSON 账目备份中。

第一版只记录人民币正数支出，不自动处理退款、外币、分期及收入。快捷面板收起后的截屏等待时间是 650 毫秒，需在 S24 Ultra 真机上验证。

## 构建

需要 JDK 17、Android SDK Platform 37、Build Tools 36.0.0；工程仍以 Android 15 / API 35 为目标，最低 Android 11。SDK 37 是编译工具版本，不要求手机升级 Android 17。

设置 `ANDROID_HOME` 指向 SDK，在工程目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

测试包位置：`app/build/outputs/apk/debug/app-debug.apk`。此为调试签名，正式长期使用前应建立固定发布签名并妥善保管，以保证后续更新可保留原数据。

业务测试：

```powershell
.\scripts\test-core.ps1
```

Android 数据往返测试：构建 `:app:assembleDebugAndroidTest`，在专用模拟器安装 App 与测试 APK 后运行：

```powershell
adb shell am instrument -w cn.local.jiyibi.test/cn.local.jiyibi.LedgerInstrumentation
```

Android 测试覆盖 Excel 导出导入、JSON 完整备份、草稿确认和重复恢复，不使用用户真实账目。

## 技术依据

- [ML Kit 中文本地识别与内置模型](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [Android 手动无障碍截屏 API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [快捷设置 TileService](https://developer.android.com/reference/android/service/quicksettings/TileService)
- [AGP 9.4 构建兼容性](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
