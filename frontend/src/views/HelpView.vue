<template>
  <div class="help-page">
    <el-row :gutter="20">
      <!-- 左侧目录 -->
      <el-col :span="5">
        <el-card shadow="never" class="toc-card" :body-style="{ padding: '0' }">
          <template #header>
            <div class="toc-header">
              <el-icon><DocumentCopy /></el-icon>
              <span>帮助目录</span>
            </div>
          </template>
          <el-menu
            :default-active="activeSection"
            class="toc-menu"
            @select="scrollTo"
          >
            <el-menu-item index="intro">
              <el-icon><InfoFilled /></el-icon>
              <span>系统简介</span>
            </el-menu-item>
            <el-sub-menu index="rules">
              <template #title>
                <el-icon><Document /></el-icon>
                <span>清洗规则规范</span>
              </template>
              <el-menu-item index="rules-overview">规则概述</el-menu-item>
              <el-menu-item index="rules-dsl">DSL 行式格式</el-menu-item>
              <el-menu-item index="rules-json">JSON 格式</el-menu-item>
              <el-menu-item index="rules-types">支持的动作类型</el-menu-item>
              <el-menu-item index="rules-examples">编写示例</el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="strategies">
              <template #title>
                <el-icon><SetUp /></el-icon>
                <span>清洗策略指南</span>
              </template>
              <el-menu-item index="strategies-overview">策略概述</el-menu-item>
              <el-menu-item index="strategies-builtin">系统内置策略</el-menu-item>
              <el-menu-item index="strategies-custom">自定义策略</el-menu-item>
              <el-menu-item index="strategies-naming">命名规范</el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="workflow">
              <template #title>
                <el-icon><Connection /></el-icon>
                <span>清洗任务流程</span>
              </template>
              <el-menu-item index="workflow-steps">执行步骤</el-menu-item>
              <el-menu-item index="workflow-tips">最佳实践</el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="faq">
              <template #title>
                <el-icon><QuestionFilled /></el-icon>
                <span>常见问题</span>
              </template>
              <el-menu-item index="faq-rule-not-work">规则不生效</el-menu-item>
              <el-menu-item index="faq-strategy-limit">策略限制</el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :span="19">
        <div class="help-content" ref="contentRef">

          <!-- 系统简介 -->
          <section id="intro" class="help-section">
            <h1 class="page-title">帮助中心</h1>
            <el-alert
              type="info"
              show-icon
              :closable="false"
              description="本帮助文档包含清洗规则与清洗策略的完整编写规范，建议首次使用时完整阅读。"
              style="margin-bottom: 20px"
            />
            <p class="desc-text">
              审计整改智能驾驶舱支持通过<strong>清洗规则</strong>和<strong>清洗策略</strong>对数据进行标准化处理。
              清洗规则定义具体的字段转换动作，清洗策略定义整体的处理算法框架，两者组合后在清洗任务中执行。
            </p>
          </section>

          <el-divider />

          <!-- 清洗规则规范 -->
          <section id="rules-overview" class="help-section">
            <h2 class="section-title">
              <el-icon color="#409eff"><Document /></el-icon>
              清洗规则规范
            </h2>
            <h3 class="sub-title" id="rules-overview-h">规则概述</h3>
            <p class="desc-text">
              清洗规则（Clean Rule）是对数据行中字段值进行转换的原子操作定义。
              每条规则包含一组动作（Action），每个动作指定作用字段和具体操作类型。
            </p>
            <p class="desc-text">
              规则内容支持两种格式：
            </p>
            <el-row :gutter="16" class="format-cards">
              <el-col :span="12">
                <el-card shadow="hover" class="format-card">
                  <div class="format-icon format-icon-dsl">DSL</div>
                  <div class="format-name">行式 DSL 格式</div>
                  <div class="format-desc">每行一条动作，用竖线 <code>|</code> 分隔字段，简洁直观，适合人工编写。</div>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="hover" class="format-card">
                  <div class="format-icon format-icon-json">JSON</div>
                  <div class="format-name">JSON 格式</div>
                  <div class="format-desc">标准 JSON 数组或含 <code>actions</code> 键的对象，适合程序生成或配置管理。</div>
                </el-card>
              </el-col>
            </el-row>
          </section>

          <section id="rules-dsl" class="help-section">
            <h3 class="sub-title">DSL 行式格式</h3>
            <p class="desc-text">每行表示一条动作，格式为：</p>
            <div class="code-block">
              <pre><code>动作类型|字段名|值（或来源值）|目标值</code></pre>
            </div>
            <el-table :data="dslSyntaxRows" border size="small" style="margin: 12px 0">
              <el-table-column prop="col" label="列" width="90" align="center" />
              <el-table-column prop="name" label="名称" width="100" />
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.required ? 'danger' : 'info'" size="small">
                    {{ scope.row.required ? '必填' : '可选' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="说明" />
            </el-table>
            <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 10px">
              <template #default>
                <ul class="alert-list">
                  <li>以 <code>#</code> 开头的行视为注释，会被忽略。</li>
                  <li>空行会被跳过。</li>
                  <li>字段名填 <code>*</code> 表示对所有字段生效。</li>
                </ul>
              </template>
            </el-alert>
          </section>

          <section id="rules-json" class="help-section">
            <h3 class="sub-title">JSON 格式</h3>
            <p class="desc-text">支持两种 JSON 结构：</p>
            <p class="desc-text"><strong>① JSON 数组</strong>（推荐）：</p>
            <div class="code-block">
              <pre><code v-pre>[
  { "type": "fill_null", "field": "*",    "value": "UNKNOWN" },
  { "type": "trim",      "field": "name"                     },
  { "type": "replace",   "field": "status", "from": "active", "to": "ACTIVE" }
]</code></pre>
            </div>
            <p class="desc-text"><strong>② 含 actions 键的对象</strong>：</p>
            <div class="code-block">
              <pre><code v-pre>{
  "version": "1.0",
  "description": "标准化空值与状态字段",
  "actions": [
    { "type": "fill_null", "field": "city", "value": "N/A" },
    { "type": "uppercase", "field": "status" }
  ]
}</code></pre>
            </div>
            <el-table :data="jsonFieldRows" border size="small" style="margin: 12px 0">
              <el-table-column prop="field" label="字段" width="100" />
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.required ? 'danger' : 'info'" size="small">
                    {{ scope.row.required ? '必填' : '可选' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="说明" />
            </el-table>
          </section>

          <section id="rules-types" class="help-section">
            <h3 class="sub-title">支持的动作类型</h3>
            <el-table :data="actionTypeRows" border size="small">
              <el-table-column prop="type" label="type" width="150">
                <template #default="scope">
                  <el-tag type="primary" size="small">{{ scope.row.type }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="说明" min-width="200" />
              <el-table-column prop="fieldNote" label="field 说明" width="140" />
              <el-table-column prop="extra" label="额外参数" min-width="160" />
            </el-table>
          </section>

          <section id="rules-examples" class="help-section">
            <h3 class="sub-title">编写示例</h3>
            <el-tabs type="border-card">
              <el-tab-pane label="空值填充规则（DSL）">
                <div class="code-block">
                  <pre><code># 对所有字段填充空值为 UNKNOWN
fill_null|*|UNKNOWN

# 单独处理 city 字段
fill_null|city|N/A

# 对 phone 字段的空值填充占位符
fill_null|phone|00000000000</code></pre>
                </div>
              </el-tab-pane>
              <el-tab-pane label="字段标准化规则（DSL）">
                <div class="code-block">
                  <pre><code># 去除所有字段首尾空格
trim|*

# email 转小写
lowercase|email

# status 转大写
uppercase|status

# 去除 phone 字段的 +86 前缀
replace|phone|+86|

# 统一 gender 字段写法
replace|gender|male|M
replace|gender|female|F</code></pre>
                </div>
              </el-tab-pane>
              <el-tab-pane label="复合规则（JSON）">
                <div class="code-block">
                  <pre><code v-pre>[
  { "type": "trim",      "field": "*" },
  { "type": "fill_null", "field": "*",      "value": "UNKNOWN" },
  { "type": "lowercase", "field": "email" },
  { "type": "uppercase", "field": "status" },
  { "type": "replace",   "field": "gender", "from": "male",   "to": "M" },
  { "type": "replace",   "field": "gender", "from": "female", "to": "F" },
  { "type": "remove_field", "field": "internal_flag" }
]</code></pre>
                </div>
              </el-tab-pane>
              <el-tab-pane label="数据脱敏示例（DSL）">
                <div class="code-block">
                  <pre><code># 删除敏感字段（数据脱敏/剔除）
remove_field|password
remove_field|id_card
remove_field|bank_no

# 替换手机号后4位（仅替换固定后缀示例）
replace|phone|1234|****</code></pre>
                </div>
              </el-tab-pane>
            </el-tabs>
          </section>

          <el-divider />

          <!-- 清洗策略指南 -->
          <section id="strategies-overview" class="help-section">
            <h2 class="section-title">
              <el-icon color="#67c23a"><SetUp /></el-icon>
              清洗策略指南
            </h2>
            <h3 class="sub-title" id="strategies-overview-h">策略概述</h3>
            <p class="desc-text">
              清洗策略（Clean Strategy）定义了数据清洗的<strong>处理算法框架</strong>，决定如何整体处理标准化表中的数据集。
              策略在清洗任务中必须指定，它控制去重方式、异常值处理方法和字段标准化的整体逻辑顺序。
            </p>
            <p class="desc-text">
              策略与规则的关系：<strong>策略是框架，规则是细节</strong>。
              一次清洗任务中，策略决定数据处理的主流程，规则在策略执行后对每行数据进行字段级别的精细转换。
            </p>
            <el-alert type="success" :closable="false" show-icon style="margin-bottom: 12px">
              <template #default>
                推荐做法：先选合适的系统策略处理去重/异常，再添加对应的用户规则做字段级标准化。
              </template>
            </el-alert>
          </section>

          <section id="strategies-builtin" class="help-section">
            <h3 class="sub-title">系统内置策略</h3>
            <el-table :data="builtinStrategyRows" border size="small">
              <el-table-column prop="code" label="策略编码" width="200">
                <template #default="scope">
                  <el-tag type="info" size="small">{{ scope.row.code }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="策略名称" width="150" />
              <el-table-column prop="desc" label="处理逻辑说明" min-width="260" />
              <el-table-column prop="scenario" label="适用场景" min-width="200" />
            </el-table>
            <el-alert type="warning" :closable="false" show-icon style="margin-top: 12px">
              <template #default>
                系统内置策略不可删除、不可修改编码；可启用/禁用，但禁用后清洗任务将无法选择该策略。
              </template>
            </el-alert>
          </section>

          <section id="strategies-custom" class="help-section">
            <h3 class="sub-title">自定义策略</h3>
            <p class="desc-text">
              用户可在"清洗规则管理 → 清洗策略管理"中创建自定义策略，自定义策略用于在清洗任务中替代或扩展系统策略。
            </p>
            <p class="desc-text"><strong>当前自定义策略的执行行为：</strong></p>
            <ul class="desc-list">
              <li>自定义策略<strong>不执行</strong>系统内置策略的特定算法（如去重）。</li>
              <li>策略的 <code>content</code> 字段目前用于说明文档和备注，不作为执行脚本。</li>
              <li>若需对数据进行转换，请通过<strong>清洗规则</strong>配合策略使用。</li>
              <li>建议将自定义策略的 content 写成人可读的业务描述，方便团队协作和审计追溯。</li>
            </ul>
            <div class="code-block">
              <pre><code># 示例：自定义策略 content 的推荐写法（纯说明文档）
策略目标：处理审计数据字段标准化
执行顺序：
  1. trim 所有字段空格
  2. 将 status 字段统一为大写
  3. 填充空值字段为 UNKNOWN
  4. 删除 internal_note 字段
对应规则：字段标准化规则_v2、空值填充标准规则

备注：本策略用于季度审计数据报告前的预处理</code></pre>
            </div>
          </section>

          <section id="strategies-naming" class="help-section">
            <h3 class="sub-title">命名规范</h3>
            <el-table :data="namingRuleRows" border size="small">
              <el-table-column prop="item" label="项目" width="150" />
              <el-table-column prop="rule" label="规范要求" min-width="220" />
              <el-table-column prop="good" label="正确示例" min-width="200">
                <template #default="scope">
                  <el-tag type="success" size="small">{{ scope.row.good }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="bad" label="错误示例" min-width="200">
                <template #default="scope">
                  <el-tag type="danger" size="small">{{ scope.row.bad }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <el-divider />

          <!-- 清洗任务流程 -->
          <section id="workflow-steps" class="help-section">
            <h2 class="section-title">
              <el-icon color="#e6a23c"><Connection /></el-icon>
              清洗任务执行流程
            </h2>
            <h3 class="sub-title">执行步骤</h3>
            <el-steps direction="vertical" :active="5" finish-status="success" style="max-width: 640px">
              <el-step title="选择数据源" description="选择一个或多个已接入的数据库/文件数据源作为清洗对象。" />
              <el-step title="选择清洗规则" description="从规则库中选取一个或多个已启用的规则，系统规则优先执行" />
              <el-step title="选择清洗策略" description="指定数据整体处理的算法框架（去重/异常剔除/标准化等）。" />
              <el-step title="执行清洗" description="系统创建标准化数据表，将数据写入后依次执行策略算法和规则动作。" />
              <el-step title="查看结果" description="任务完成后可在执行记录中查看清洗行数和状态。" />
              <el-step title="数据融合（可选）" description="多个清洗任务的标准化表可进一步融合为统一主数据集，用于仪表盘分析。" />
            </el-steps>
          </section>

          <section id="workflow-tips" class="help-section">
            <h3 class="sub-title">最佳实践</h3>
            <el-row :gutter="16">
              <el-col :span="12" v-for="tip in bestPracticeTips" :key="tip.title">
                <el-card shadow="hover" class="tip-card" style="margin-bottom: 12px">
                  <div class="tip-title">
                    <el-icon :color="tip.color" style="margin-right: 6px"><component :is="tip.icon" /></el-icon>
                    {{ tip.title }}
                  </div>
                  <div class="tip-desc">{{ tip.desc }}</div>
                </el-card>
              </el-col>
            </el-row>
          </section>

          <el-divider />

          <!-- 常见问题 -->
          <section id="faq-rule-not-work" class="help-section">
            <h2 class="section-title">
              <el-icon color="#f56c6c"><QuestionFilled /></el-icon>
              常见问题
            </h2>
            <h3 class="sub-title">规则不生效</h3>
            <el-collapse>
              <el-collapse-item title="Q: 上传规则后执行清洗，字段没有变化？" name="1">
                <ul class="desc-list">
                  <li>确认规则已在"清洗规则管理"页面将开关设置为<strong>启用</strong>状态。</li>
                  <li>确认在创建清洗任务时已将该规则勾选添加进任务。</li>
                  <li>检查规则 content 内容格式是否正确（DSL 格式请确认竖线分隔，JSON 格式请确认语法合法）。</li>
                  <li>检查字段名是否与数据源中实际字段名完全一致（大小写敏感）。</li>
                </ul>
              </el-collapse-item>
              <el-collapse-item title="Q: DSL 格式中 replace 替换没有效果？" name="2">
                <ul class="desc-list">
                  <li><code>replace</code> 执行精确字符串匹配（非正则），请确认 <code>from</code> 的值与字段中的实际文本完全一致。</li>
                  <li>如果 <code>to</code> 列留空，表示删除匹配到的字符串而非替换为空字符串（效果相同），语法正确。</li>
                  <li>DSL 格式中 <code>replace</code> 的列顺序必须是：<code>replace|字段|旧值|新值</code>，共 4 列。</li>
                </ul>
              </el-collapse-item>
              <el-collapse-item title="Q: JSON 格式上传后提示语法错误，如何排查？" name="3">
                <ul class="desc-list">
                  <li>可在浏览器控制台执行 <code>JSON.parse('...')</code> 验证 JSON 合法性。</li>
                  <li>常见错误：末尾多余逗号、属性名未加引号、单引号替代双引号。</li>
                  <li>推荐使用 VS Code 或 JSONLint 工具格式化和验证后再粘贴上传。</li>
                </ul>
              </el-collapse-item>
            </el-collapse>
          </section>

          <section id="faq-strategy-limit" class="help-section">
            <h3 class="sub-title">策略限制</h3>
            <el-collapse>
              <el-collapse-item title="Q: 为什么无法删除系统策略？" name="4">
                <p class="desc-text" style="margin: 0">
                  系统内置策略（builtIn = true）是平台运营保障策略，保证清洗任务至少有可选策略。
                  如不希望某策略出现在选择列表中，可将其<strong>禁用</strong>但不可删除。
                </p>
              </el-collapse-item>
              <el-collapse-item title="Q: 编码已存在，但我想用相同的编码重新建策略？" name="5">
                <ul class="desc-list">
                  <li>策略编码在同一账户下全局唯一，不允许重复。</li>
                  <li>如需更新策略内容，请用"在线查看/编辑"功能直接修改已有策略，而不是新建同编码策略。</li>
                  <li>旧策略应先删除后再创建同编码的新策略（删除不可恢复，请谨慎操作）。</li>
                </ul>
              </el-collapse-item>
              <el-collapse-item title="Q: 清洗任务可以同时关联多条规则吗？" name="6">
                <ul class="desc-list">
                  <li>可以，一个清洗任务支持关联多条规则，系统按规则 ID 从小到大顺序执行每条规则的全部动作。</li>
                  <li>规则之间的动作互不覆盖，会依次叠加应用到同一行数据上。</li>
                  <li>建议将互相关联的动作（如先 trim 再 replace）放在同一条规则中，以减少顺序依赖问题。</li>
                </ul>
              </el-collapse-item>
            </el-collapse>
          </section>

        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import {
  Connection,
  Document,
  DocumentCopy,
  InfoFilled,
  QuestionFilled,
  SetUp,
  SuccessFilled,
  WarningFilled,
  Promotion,
  Star
} from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'

const activeSection = ref('intro')
const contentRef = ref(null)

function scrollTo(index) {
  activeSection.value = index
  const el = document.getElementById(index)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const dslSyntaxRows = [
  { col: '第 1 列', name: '动作类型', required: true, desc: '必须是支持的 type 关键字（见下方动作类型表）' },
  { col: '第 2 列', name: '字段名', required: true, desc: '目标字段名，* 代表所有字段' },
  { col: '第 3 列', name: '值 / 旧值', required: false, desc: 'fill_null 时为填充值；replace 时为旧值（from）' },
  { col: '第 4 列', name: '新值（to）', required: false, desc: '仅 replace 类型使用，表示替换后的目标值' }
]

const jsonFieldRows = [
  { field: 'type', required: true, desc: '动作类型，必须是支持的关键字（如 fill_null、trim 等）' },
  { field: 'field', required: false, desc: '目标字段名，缺省时等同于 *（所有字段）' },
  { field: 'value', required: false, desc: 'fill_null 类型的填充值，默认 UNKNOWN' },
  { field: 'from', required: false, desc: 'replace 类型的源字符串' },
  { field: 'to', required: false, desc: 'replace 类型的目标字符串，缺省时删除匹配片段' }
]

const actionTypeRows = [
  { type: 'fill_null', desc: '将指定字段中的空值（null 或空字符串）填充为指定值', fieldNote: '支持 *', extra: 'value: 填充值（默认 UNKNOWN）' },
  { type: 'trim', desc: '去除字段值首尾的空白字符（空格、制表符、换行）', fieldNote: '支持 *', extra: '无' },
  { type: 'lowercase', desc: '将字段值转换为全小写', fieldNote: '支持 *', extra: '无' },
  { type: 'uppercase', desc: '将字段值转换为全大写', fieldNote: '支持 *', extra: '无' },
  { type: 'replace', desc: '将字段值中匹配 from 的子串全部替换为 to（精确字符串匹配，非正则）', fieldNote: '支持 *', extra: 'from: 源串；to: 目标串（可为空）' },
  { type: 'remove_field', desc: '从数据记录中删除指定字段（字段脱敏/裁剪）', fieldNote: '不支持 *', extra: '无' }
]

const builtinStrategyRows = [
  {
    code: 'DEDUP_AND_FILL',
    name: '去重+空值补齐',
    desc: '①按照标准化 JSON 内容去重（完全相同行保留一条）；②将 normalized_json 中所有为空的值替换为 UNKNOWN',
    scenario: '多数据源合并后存在重复行，且字段存在大量空值的场景'
  },
  {
    code: 'STANDARDIZE',
    name: '字段标准化',
    desc: '①对字段名做小写标准化；②去除超长记录（normalized_json 超过 8000 字符的行会被剔除）',
    scenario: '字段格式不统一、数据过于冗余或字段名来自不同拼写习惯的场景'
  },
  {
    code: 'OUTLIER_REMOVE',
    name: '异常值剔除',
    desc: '删除 normalized_json 为空或内容过短（小于 3 个字符）的记录，剔除无意义数据行',
    scenario: '原始数据中存在大量空行、占位行或格式化失败行的场景'
  }
]

const namingRuleRows = [
  { item: '策略编码', rule: '全大写英文字母与下划线，字母开头，不含空格或特殊符号', good: 'DEDUP_AUDIT_V2', bad: 'dedup audit v2' },
  { item: '策略名称', rule: '中英文均可，需简明描述处理目标，建议不超过 20 字', good: '季度审计数据去重+标准化', bad: '策略1' },
  { item: '规则名称', rule: '中英文均可，反映规则的动作意图，建议包含版本或场景标识', good: '字段标准化规则_审计_v1', bad: '新规则' },
  { item: '规则文件名', rule: '与规则内容格式对应，DSL 规则用 .txt，JSON 规则用 .json', good: 'field_normalize.json', bad: 'rule.data' }
]

const bestPracticeTips = [
  {
    icon: SuccessFilled,
    color: '#67c23a',
    title: '先测试再大批量',
    desc: '建议先在小数据集（几十行）上测试规则和策略的效果，确认输出符合预期后再应用到全量数据。'
  },
  {
    icon: Star,
    color: '#e6a23c',
    title: '规则保持单一职责',
    desc: '每条规则只做一类事情（如只做空值填充，不要混入字段删除），便于后续维护和排查问题。'
  },
  {
    icon: Promotion,
    color: '#409eff',
    title: '利用版本标识',
    desc: '规则名中加入版本号（如 _v1、_v2），更新时不要直接覆盖，而是新建规则并禁用旧版，便于回滚。'
  },
  {
    icon: WarningFilled,
    color: '#f56c6c',
    title: '敏感字段使用 remove_field',
    desc: '对身份证、手机号等敏感字段，在清洗规则中使用 remove_field 确保数据出库前完成脱敏处理。'
  },
  {
    icon: Connection,
    color: '#909399',
    title: '策略与规则组合使用',
    desc: '系统策略负责去重和异常剔除框架，用户规则负责字段级精细化转换，两者互补不要混淆职责。'
  },
  {
    icon: DocumentCopy,
    color: '#b39ddb',
    title: '记录策略 content',
    desc: '在策略的 content 字段写明该策略的业务背景、执行顺序和对应规则名，方便团队理解和审计回溯。'
  }
]

onMounted(() => {
  // Highlight the first visible section on initial load
})
</script>

<style scoped>
.help-page {
  padding: 4px 0;
}

.toc-card {
  position: sticky;
  top: 12px;
}

.toc-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.toc-menu {
  border: none;
  background: transparent;
}

.toc-menu :deep(.el-menu-item),
.toc-menu :deep(.el-sub-menu__title) {
  height: 36px;
  line-height: 36px;
  font-size: 13px;
}

.help-content {
  padding: 0 4px;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 16px;
  color: var(--el-text-color-primary);
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
}

.sub-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
  border-left: 4px solid var(--el-color-primary);
  padding-left: 10px;
}

.help-section {
  margin-bottom: 32px;
  scroll-margin-top: 16px;
}

.desc-text {
  color: var(--el-text-color-regular);
  line-height: 1.8;
  margin: 0 0 10px;
}

.desc-list {
  color: var(--el-text-color-regular);
  line-height: 1.9;
  padding-left: 20px;
  margin: 6px 0;
}

.desc-list li {
  margin-bottom: 4px;
}

.alert-list {
  padding-left: 16px;
  margin: 0;
  line-height: 1.8;
}

.alert-list li {
  margin-bottom: 4px;
}

.code-block {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 14px 18px;
  margin: 10px 0;
  overflow-x: auto;
}

.code-block pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre;
}

.code-block code {
  background: transparent;
  font-size: inherit;
}

.format-cards {
  margin-top: 12px;
}

.format-card {
  text-align: center;
  padding: 8px 0;
  cursor: default;
}

.format-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  margin: 0 auto 10px;
  color: #fff;
  line-height: 52px;
}

.format-icon-dsl {
  background: linear-gradient(135deg, #409eff, #66b1ff);
}

.format-icon-json {
  background: linear-gradient(135deg, #67c23a, #85ce61);
}

.format-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.format-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.tip-card {
  height: 100%;
}

.tip-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  color: var(--el-text-color-primary);
}

.tip-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
</style>
