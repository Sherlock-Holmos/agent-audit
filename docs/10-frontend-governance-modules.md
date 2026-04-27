# 前端治理模块说明

## 1. 模块目标
前端治理模块用于承载数据治理相关能力，提供统一入口与子域页面，降低单页复杂度并提升可维护性。

## 2. 页面结构
当前治理模块包含 5 个页面：
1. 治理中心：`/datasource/clean-rules`
2. 规则与策略：`/datasource/clean-rules/rules`
3. 主键同义词：`/datasource/clean-rules/synonyms`
4. NiFi 模板：`/datasource/clean-rules/nifi-templates`
5. 分层统计：`/datasource/clean-rules/layer-stats`

所有页面共享二级导航组件：`frontend/src/components/dataclean/GovernanceSubNav.vue`。

## 3. 代码组织

### 3.1 视图层
- `frontend/src/views/DatasourceCleanRuleView.vue`：治理中心入口。
- `frontend/src/views/DatasourceRuleStrategyView.vue`：规则与策略管理。
- `frontend/src/views/DatasourceFusionKeySynonymView.vue`：主键同义词管理。
- `frontend/src/views/DatasourceNifiTemplateView.vue`：NiFi 模板管理与触发测试。
- `frontend/src/views/DatasourceLayerStatsView.vue`：B/S/G 分层统计。

### 3.2 API 层（按子域拆分）
- `frontend/src/api/clean-rule.js`
- `frontend/src/api/clean-strategy.js`
- `frontend/src/api/fusion-key-synonym.js`
- `frontend/src/api/nifi-control-plane.js`
- `frontend/src/api/layer-stats.js`

已移除历史兼容入口 `frontend/src/api/cleanrule.js`，所有调用统一走子域 API。

## 4. 设计原则
1. 子域自治：每个页面只负责单一治理能力。
2. 导航统一：通过共享二级导航保证切换体验一致。
3. 低风险迁移：保留兼容导出层，避免一次性改动全部调用方。
4. 渐进演进：后续可将通用加载/错误处理抽到 composable。

## 5. 维护建议
1. 新增治理能力时，优先新增独立视图与独立 API 文件，不再堆叠到单页面。
2. 当跨页面出现重复逻辑时，抽离到 `composables` 或通用组件。
3. 保持路由命名和路径语义一致，便于权限配置与埋点统计。
4. 每次治理模块调整后执行 `frontend` 下 `npm run build` 做最小回归验证。

## 6. 表格治理规范（新增）
1. 业务页面和业务组件禁止直接使用 `el-table` 根标签；统一通过 `AppDataTable` 承载。
2. 列定义继续使用 `el-table-column` 作为 `AppDataTable` 插槽内容，这是标准用法。
3. `layout-storage-key` 统一采用 `app:table-layout:<domain>:<scene>` 命名，避免散乱字符串。
4. 仅允许 `frontend/src/components/shared/AppDataTable.vue` 内部直接声明 `el-table`。

### 6.1 自动检查命令
在 `frontend` 目录执行：

```bash
npm run check:table-governance
```

该命令会扫描 `frontend/src/**/*.vue`，若在白名单文件外发现 `el-table` 根标签将直接失败。
