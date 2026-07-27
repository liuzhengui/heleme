# WaterReminder 后续开发计划

> 更新时间：2026-07-23

---

## 一、代码优化项

### 1. WaterReminderService 重命名
- **文件**: `service/WaterReminderService.kt`
- **问题**: 命名误导，它是 SharedPreferences 包装类而非 Android Service
- **方案**: 重命名为 `PreferenceManager`，考虑移到 `util/` 包

### 2. 数据库迁移策略
- **文件**: `data/AppDatabase.kt`
- **问题**: `fallbackToDestructiveMigration()` 升级数据库时直接删除所有用户数据
- **方案**: 实现版本间 Migration，至少为当前 v4 兜底

### 3. FullscreenReminderActivity 重复调度
- **文件**: `notification/FullscreenReminderActivity.kt` (L109-L125)
- **问题**: `scheduleAfterDrink` 被调用了两次，存在冗余
- **方案**: 移除重复调用

### 4. showCheckInDialog / showCustomDrinkDialog 代码合并
- **文件**: `ui/home/HomeFragment.kt` (L110-L191)
- **问题**: 两个方法 90% 相同，只有 ChipGroup 点击行为不同
- **方案**: 抽取公共参数化的 BottomSheet 构建方法

### 5. Widget CoroutineScope 泄漏风险
- **文件**: `widget/WaterWidgetLargeProvider.kt`
- **问题**: `updateWidgets()` 每次新建不受控的 CoroutineScope
- **方案**: 使用 AppWidgetProvider 已有的 scope 或加取消逻辑

### 6. RecordChipAdapter 改用 ListAdapter + DiffUtil
- **文件**: `ui/home/HomeFragment.kt` (RecordChipAdapter 内部类)
- **问题**: `notifyDataSetChanged()` 全量刷新
- **方案**: 改用 `ListAdapter` + `DiffUtil.ItemCallback`

### 7. 提取 dpToPx 扩展函数
- **文件**: `ui/home/HomeFragment.kt` (L306)
- **问题**: dp 转 px 扩展定义在 Fragment 内部类中
- **方案**: 提取到 `util/` 包作为公共扩展

### 8. 无障碍支持
- **问题**: 多处 ImageView 缺少 `contentDescription`
- **方案**: 为所有功能性图片添加 `contentDescription`，装饰性图片标记为不重要

### 9. 单元测试覆盖
- **方案**:
  - ViewModel 层：`MainViewModel`、`RecordListViewModel`、`PersonTypeViewModel`
  - Repository 层：数据库操作逻辑
  - ReminderScheduler：闹钟调度逻辑（需要 Robolectric）

---

## 二、新增功能（按优先级）

### P0 - 高优先级

#### 【通知栏快捷打卡】
通知上添加 `addAction()`，用户可在通知栏直接点击"喝了200ml"完成打卡，无需解锁手机。

- 修改文件：`notification/NotificationHelper.kt`
- 新增：通知 Action 的 BroadcastReceiver

#### 【数据导出 CSV】
在设置/记录页增加导出按钮，将喝水记录导出为 CSV 文件，包含日期、时间、饮水量、人员类型。

- 修改文件：`ui/record/RecordListActivity.kt` 或 `RecordListFragment.kt`
- 新增：`util/CsvExporter.kt`

#### 【统计洞察文字版】
- 日均饮水量（本周/本月）
- 喝水最多/最少的日期
- 哪个时段喝水最频繁
- 本周达标率

- 修改文件：`ui/record/RecordListFragment.kt`
- 新增：统计计算逻辑在 `RecordListViewModel.kt`

### P1 - 中优先级

#### 【日历热力图】
类似 GitHub 贡献图，以月份为维度展示每日达标情况（绿=达标，灰=未达标，浅绿=接近），一图看清喝水习惯。

- 新增：自定义 View 或利用现有图表库
- 修改文件：`ui/record/RecordListFragment.kt`

#### 【静默时段】
用户可设置"免打扰时间段"（如 14:00-15:00 开会），期间不发送喝水提醒通知。

- 修改文件：`data/entity/PersonType.kt` 增加静默时段字段
- 修改文件：`receiver/ReminderReceiver.kt` 增加静默判断

#### 【Android 13+ 主题图标】
在 `ic_launcher.xml` 中添加 `monochrome` 层，支持 Material You 动态取色。

- 修改文件：`res/mipmap-anydpi-v26/ic_launcher.xml`

#### 【Widget 配置页面】
添加 Widget 时弹出配置 Activity，让用户选择展示哪个 PersonType 的数据。

- 新增：Widget 配置 Activity
- 修改文件：`xml/water_widget_*_info.xml`

### P2 - 低优先级

#### 【多种饮料类型】
支持记录白水、茶、咖啡、果汁等，不同饮料有不同的补水系数（如咖啡利尿，实际补水打折）。

- 新增：`BeverageType` 实体和 DAO
- 修改文件：`data/entity/WaterRecord.kt` 增加饮料类型字段

#### 【备份与恢复】
- 利用 Android Auto Backup（云备份，简单实现）
- 或手动 JSON 导出/导入（包括设置和记录）

#### 【提示音自定义】
在设置中允许用户选择不同的提醒铃声，甚至自定义铃声。

- 修改文件：`notification/NotificationHelper.kt`
- 新增：铃声选择 Preference

#### 【智能目标调整】
根据近期平均饮水量、季节（天气）等因素，智能建议用户调整每日目标。

---

## 三、技术债务

| 项目 | 说明 |
|------|------|
| Room Schema Export | `exportSchema = false`，建议开启并提交 schema 文件用于迁移验证 |
| ProGuard/R8 规则 | 当前未配置混淆规则，建议检查 MPAndroidChart、Glide 等库的 ProGuard 规则 |
| baselineProfile | 可添加 baseline profile 加速冷启动 |
| CI/CD | 可配置 GitHub Actions 自动构建 APK |

---

## 四、版本规划

| 版本 | 内容 |
|------|------|
| v1.1 | P0 功能：通知快捷打卡 + 数据导出 + 统计洞察 |
| v1.2 | P1 功能：日历热力图 + 静默时段 + 主题图标 |
| v1.3 | 代码优化项 + 单元测试 |
| v2.0 | P2 功能 + 技术债务清理 |
