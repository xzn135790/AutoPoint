# 悬浮录制与准星取点设计

## 背景

当前自动连点器 MVP 已经具备方案管理、步骤编辑、悬浮控制条和无障碍手势执行能力，但坐标主要依赖用户在主界面手动输入。这不符合实际使用方式：用户通常是在目标 App 画面上定位点击点，而不是提前知道数字化坐标。

本次调整目标是让应用真正围绕“在其他 App 上配置和执行”来工作。主 App 继续承担方案管理和权限入口，悬浮窗成为主要操作入口，支持录制坐标和准星取点。

## 范围

本次实现以下能力：

- 在其他 App 上打开悬浮控制条。
- 悬浮控制条支持执行、取点、录制三类操作。
- 准星取点模式下，通过可拖动准星添加点击点，或设置滑动起点和终点。
- 录制模式下，通过透明全屏悬浮层采集点击和滑动坐标，批量生成步骤。
- 新增步骤写入当前选中方案，并沿用现有持久化机制。
- 执行步骤仍走现有 `AutoClickAccessibilityService` 和 `AutoClickEngine`。

不做以下内容：

- 不绕过 Android 权限限制。
- 不后台监听其他 App 的真实触摸事件。
- 不识别目标 App 控件或文字。
- 不做脚本市场、云同步、远程控制等扩展。

## Android 行为边界

录制模式采用透明全屏悬浮层接收触摸事件。录制层显示期间，触摸事件由本应用采集，下面的目标 App 不会同时响应。这是可控且稳定的实现方式。

准星取点模式不会拦截全屏触摸，只显示一个可拖动准星和悬浮控制面板。用户把准星拖到目标位置后，通过按钮生成步骤。该模式适合精确修正录制结果。

## 用户流程

1. 用户在主 App 选择或创建方案。
2. 用户点击“打开悬浮控制条”，跳转到目标 App。
3. 悬浮控制条默认处于执行模式，可开始、暂停、停止当前方案。
4. 用户切到取点模式：
   - 拖动准星到目标位置。
   - 点击“添加点击”追加点击步骤。
   - 点击“滑动起点”记录起点，再拖动准星后点击“滑动终点”追加滑动步骤。
5. 用户切到录制模式：
   - 显示透明全屏录制层和小型录制面板。
   - 单次点按记录为点击步骤。
   - 按下到抬起的位移超过阈值记录为滑动步骤。
   - 可撤销上一步、停止录制、保存。
6. 用户回到执行模式，点击开始后按当前方案执行。

## 悬浮控制条

`FloatingControlService` 负责管理悬浮窗生命周期和模式状态。

模式包括：

- `EXECUTE`：执行模式，保留现有开始、暂停、停止能力。
- `CROSSHAIR`：取点模式，显示准星和添加步骤按钮。
- `RECORDING`：录制模式，显示透明全屏录制层和录制面板。

悬浮面板建议保持紧凑，避免遮挡目标 App。面板按钮包括：

- 模式切换：执行、取点、录制。
- 执行模式：开始、暂停/继续、停止。
- 取点模式：添加点击、滑动起点、滑动终点、隐藏/显示准星。
- 录制模式：停止录制、撤销上一步、保存。

## 准星取点

准星是一个可拖动的 `TYPE_APPLICATION_OVERLAY` 小视图，中心点即记录坐标。

坐标获取规则：

- 使用准星视图在屏幕上的窗口位置加上准星中心偏移，得到屏幕绝对坐标。
- 点击步骤记录 `startX/startY`，`endX/endY` 与起点相同。
- 滑动步骤先暂存起点，再在终点按钮点击时生成完整步骤。
- 如果用户未设置滑动起点就点击滑动终点，悬浮面板提示“请先设置滑动起点”。

默认参数：

- 点击延时：沿用 `ClickStep.click()` 默认值。
- 滑动延时和持续时间：沿用 `ClickStep.swipe()` 默认值。
- 随机偏差：默认 0，后续仍可在主界面编辑。

## 录制覆盖层

录制覆盖层是全屏透明 `TYPE_APPLICATION_OVERLAY` 视图，用于采集触摸。

触摸判断规则：

- `ACTION_DOWN` 记录起点坐标和时间。
- `ACTION_UP` 记录终点坐标和时间。
- 起终点距离小于阈值时生成点击步骤。
- 起终点距离大于或等于阈值时生成滑动步骤。
- 滑动持续时间使用按下到抬起耗时，并设置下限，避免 0ms 手势。

建议阈值：

- 位移阈值：24px。
- 点击持续时间下限：50ms。
- 滑动持续时间下限：120ms。

录制层需要提供明确的退出入口，避免用户误以为手机不可操作。录制面板固定显示“停止录制”和“撤销”。

## 数据流

新增步骤写入当前选中方案：

1. `FloatingControlService` 读取 `ClickProfileStore.loadProfiles()` 和 `loadSelectedProfileId()`。
2. 找到当前方案。
3. 追加新生成的 `ClickStep`。
4. 调用 `saveProfiles(profiles, selectedProfileId)` 保存。
5. 悬浮面板显示已添加步骤数量或最近添加结果。

为降低重复代码，可在 `ClickProfileStore` 增加小范围便捷方法，例如：

- `appendStepToSelectedProfile(ClickStep step)`
- `removeLastStepFromSelectedProfile()`

如果新增便捷方法，应保持现有 JSON 存储结构不变。

## 错误处理

- 没有悬浮窗权限时，不启动悬浮服务，提示用户授权。
- 当前方案不存在时，自动回退到第一个方案；如果列表为空，创建默认方案。
- 录制层已显示时，禁止重复进入录制模式。
- 未设置滑动起点时点击滑动终点，提示用户先设置起点。
- 无障碍服务未开启时，取点和录制仍可用，但执行时提示开启无障碍服务。
- 保存步骤失败时，提示“保存失败，请返回主界面检查方案”。

## 测试与验证

自动验证：

- 保留现有 `ClickProfileTest` 和 `AutoClickEngineTest`。
- 如新增 `ClickProfileStore` 便捷方法，优先保持逻辑简单；涉及 Android `SharedPreferences` 的部分以手动验证为主。
- 运行 `:app:testDebugUnitTest`。
- 运行 `:app:assembleDebug`。

手动验证：

- 在红米 Android 13+ 设备上授予悬浮窗权限。
- 打开目标 App 后显示悬浮控制条。
- 准星模式添加点击步骤后能执行到对应位置。
- 准星模式设置滑动起点和终点后能执行滑动。
- 录制模式下点击被记录为点击步骤，拖动被记录为滑动步骤。
- 录制层显示时下面 App 不响应触摸，停止录制后恢复正常。
- 撤销上一步不会删除其他方案或无关步骤。

## 影响范围

主要修改：

- `app/src/main/java/com/example/myapplication3/service/FloatingControlService.java`
- `app/src/main/res/layout/view_floating_control.xml`
- `app/src/main/java/com/example/myapplication3/store/ClickProfileStore.java`

可能新增：

- `app/src/main/res/layout/view_recording_overlay.xml`
- `app/src/main/res/layout/view_crosshair.xml`

保持不变：

- `ClickStep` 和 `ClickProfile` 的 JSON 结构。
- `AutoClickEngine` 的执行调度逻辑。
- `AutoClickAccessibilityService` 的手势派发入口。
