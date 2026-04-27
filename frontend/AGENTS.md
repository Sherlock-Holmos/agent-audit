# Frontend AGENTS

## 1. 架构定位
- 职责：承载审计整改系统的全部用户界面、路由、状态管理和前端 API 调用。
- 结构：Vue 3 单页应用，采用“布局壳 + 业务视图 + 通用组件 + API 分层”的组织方式。
- 入口：`src/main.js` 初始化应用，`src/App.vue` 提供全局布局与登录态分流，`src/router.js` 负责路由与权限跳转。
- 边界：前端只通过网关 `/api/**` 访问后端，不直连各微服务端口。

## 2. 技术栈
- 语言与框架：JavaScript、Vue 3、Vue Router、Pinia。
- UI 与交互：Element Plus、@element-plus/icons-vue。
- 请求层：Axios，统一 `baseURL=/api`，请求拦截器自动注入认证与用户上下文。
- 图表与可视化：ECharts。
- 构建工具：Vite。

## 3. API 与接口层
- `src/api/auth.js`：登录、注册、个人信息、注销等身份相关接口。
- `src/api/dashboard.js`：驾驶舱数据接口。
- `src/api/datasource.js`、`src/api/dataclean.js`、`src/api/datafusion.js`：数据源、清洗、融合相关接口。
- `src/api/clean-rule.js`、`src/api/clean-strategy.js`、`src/api/fusion-key-synonym.js`、`src/api/layer-stats.js`、`src/api/nifi-control-plane.js`：治理模块接口。
- `src/api/rectification.js`：整改业务域接口。
- `src/api/assistant.js`：智能问答接口。
- 所有请求默认走网关 `/api`。
- 请求头由拦截器自动带上 `Authorization`、`X-User-Name`、`X-User-Role`。
- 统一处理 401 / 404 登录态失效回跳。
- 长耗时接口要单独评估超时，避免共用 10 秒默认值造成误判。

## 4. 关键配置
- 开发服务器端口：`5173`。
- 代理：`/api -> http://localhost:18081`。
- 构建分包：`vendor-vue`、`vendor-ui`、`vendor-chart`、`vendor-http`。
- 认证态存储：`localStorage.token`、`localStorage.user`。
- 运行时主题状态：Pinia 中的应用设置（折叠侧边栏、主题模式等）。

## 5. 设计规则
- 总体风格：审计驾驶舱、信息密度高、偏企业后台，不做花哨装饰。
- 色彩基调：以白色、浅灰、蓝色为主；强调色用深蓝或浅蓝，不采用紫色系默认模板。
- 布局基线：固定侧边栏 + 固定顶部标题栏 + 可滚动内容区，页面结构稳定。
- 卡片使用：表单、表格、图表优先放入卡片或带边界容器内，保持层次清晰。
- 字体与层级：标题更重、正文更轻，使用明确的字号差异表达信息层级。
- 交互节奏：优先使用抽屉、弹窗、标签页、步骤式提交等明确动作，不堆叠冗长表单在单页。
- 表格体验：统一通过 `AppDataTable` 承载，强调列宽记忆、空态提示、分页一致性。
- 视觉统一：图表、统计卡、表单、列表的圆角、间距、阴影强度保持一致，避免页面拼贴感。
- 动效克制：只保留必要的 hover、折叠、切换、弹出动画，不做高频闪烁或夸张运动。
- 新页面优先复用现有布局和共享组件，不重复造轮子。
- 业务表格禁止直接写 `el-table` 根标签，统一使用 `AppDataTable`。
- 需要可复用的表单、弹窗、工具栏、表格列，优先拆成独立组件。
- 视图层只做数据编排和交互事件，复杂逻辑下沉到 composable 或 API 层。
- 路由与菜单名称保持语义一致，便于权限、埋点和用户理解。
- 新增页面前先确认所在业务域，避免跨域组件耦合。
- 新增样式优先沿用现有变量、间距和卡片风格，避免引入不一致的新视觉体系。
- 全局样式修改必须评估登录页、主布局、弹窗和表格是否会被连带影响。
- 配置修改后优先执行 `npm run build`，表格治理变更额外执行 `npm run check:table-governance`。

## 6. 页面组织
- 审计管理员：`/audit-admin/**`
- 审计人员：`/auditor/**`
- 被审单位管理员：`/org-admin/**`
- 被审单位经办人：`/org-operator/**`
- 数据域与治理域：`/datasource/**`
- 公共页面：`/login`、`/register`、`/help`、`/ai`、`/messages`
- 登录页与注册页保持轻量，避免共享主布局。
- 业务页面优先使用右侧详情抽屉和弹窗，而不是整页跳转。
- 带搜索、筛选、创建按钮的页面优先把动作收拢到右上角工具条。
- 复杂操作需要明确的确认对话框和错误提示。
- 空态文案要具体，能指导用户下一步动作。

## 7. 表格与弹窗统一规范（强约束）
- 适用范围：所有新增或改造的业务页面（尤其 `/org-operator/**`、`/org-admin/**`）。
- 表格根组件强制使用 `AppDataTable`，禁止在业务页面直接以 `el-table` 作为根承载。
- 页面已有外层 `el-card` 时，`AppDataTable` 必须使用 `:with-card="false"`，避免卡片嵌套造成风格不一致。
- 必须配置稳定的 `layout-storage-key`，命名建议：`app:table-layout:{domain}:{page}`。
- 表格列顺序与视觉风格应与同角色近邻页面保持一致，优先复用既有列定义与状态标签映射。
- 空态、加载态、分页行为必须保持一致，不得在单页中引入独立表格交互规则。
- 新增表格样式优先沿用 `AppDataTable` 与现有页面样式，不新增与主视觉冲突的表头/行高/边框体系。

- 弹窗优先复用现有页面的 `el-dialog` 逻辑与交互参数，不新建仅单页使用的“专用弹窗逻辑分支”。
- 详情/编辑/提交类弹窗默认使用：`destroy-on-close`、`align-center`、`:lock-scroll="true"`。
- 表单型弹窗默认使用：`:close-on-click-modal="false"`，避免误触关闭导致输入丢失。
- 禁止通过 `top + transform` 等自定义定位方式覆盖弹窗居中策略，统一使用 `align-center`。
- 若主布局为 `.main-scroll` 容器滚动，弹窗打开时需同步锁定主内容滚动；关闭与组件卸载时必须恢复滚动状态。
- 弹窗内若无明确需求，不为内容区单独引入滚动容器；优先保持信息分段与字段精简，避免“弹窗内再滚动”。

- 复用决策规则：
- 已有同类弹窗组件可覆盖需求时，必须复用并通过 props/slots 扩展，不复制一份新实现。
- 若确需新建弹窗组件，至少满足“可在 2 个以上页面复用”且命名落在 `src/components/**` 业务分组下。
- 页面改造后必须执行 `npm run build`；涉及表格治理能力变更时额外执行 `npm run check:table-governance`。
