<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="section-header">
          <span>清洗规则管理</span>
          <el-button type="primary" @click="ruleUploadVisible = true">上传规则</el-button>
        </div>
      </template>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-table :data="rules" v-loading="loading" border style="width: 100%">
        <el-table-column prop="name" label="规则名称" min-width="180" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.category === 'SYSTEM' ? 'info' : 'success'">
              {{ scope.row.category === 'SYSTEM' ? '系统' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="160" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggle(scope.row.id, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openRuleEditor(scope.row)">在线查看</el-button>
            <el-popconfirm title="确认删除该规则？" @confirm="handleDelete(scope.row)">
              <template #reference>
                <el-button type="danger" link :disabled="scope.row.category === 'SYSTEM'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="ruleEditorVisible" width="760px" title="规则在线查看与编辑" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleEditorForm.name" :disabled="ruleEditorReadonly" />
        </el-form-item>
        <el-form-item label="规则文件">
          <el-input v-model="ruleEditorForm.fileName" :disabled="ruleEditorReadonly" />
        </el-form-item>
        <el-form-item label="规则内容">
          <el-input
            v-model="ruleEditorForm.content"
            type="textarea"
            :rows="10"
            :disabled="ruleEditorReadonly"
            placeholder="支持 DSL 行式规则或 JSON 规则内容"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleEditorForm.remark" :disabled="ruleEditorReadonly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleEditorVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="updatingRule"
          :disabled="ruleEditorReadonly"
          @click="saveRuleEditor"
        >保存修改</el-button>
      </template>
    </el-dialog>

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="section-header">
          <span>清洗策略管理</span>
          <el-button type="primary" @click="strategyUploadVisible = true">新增策略</el-button>
        </div>
      </template>
    </el-card>

    <el-card shadow="never">
      <el-table :data="strategies" v-loading="loadingStrategies" border style="width: 100%">
        <el-table-column prop="name" label="策略名称" min-width="180" />
        <el-table-column prop="code" label="策略编码" min-width="180" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.builtIn ? 'info' : 'success'">
              {{ scope.row.builtIn ? '系统' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggleStrategy(scope.row.id, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openStrategyEditor(scope.row)">在线查看</el-button>
            <el-popconfirm title="确认删除该策略？" @confirm="handleDeleteStrategy(scope.row)">
              <template #reference>
                <el-button type="danger" link :disabled="scope.row.builtIn">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="section-header">
          <span>融合主键同义词管理</span>
          <el-space>
            <el-button @click="openSynonymHistorySearch">历史查询</el-button>
            <el-button type="primary" @click="openSynonymCreate">新增映射</el-button>
          </el-space>
        </div>
      </template>
      <el-table :data="synonyms" v-loading="loadingSynonyms" border style="width: 100%">
        <el-table-column prop="canonicalKey" label="标准主键" min-width="180" />
        <el-table-column label="同义字段" min-width="320">
          <template #default="scope">
            <el-space wrap>
              <el-tag v-for="item in scope.row.aliases || []" :key="`${scope.row.id}-${item}`" type="info">{{ item }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.builtIn ? 'info' : 'success'">
              {{ scope.row.builtIn ? '系统' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggleSynonym(scope.row.id, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openSynonymEditor(scope.row)">编辑</el-button>
            <el-button type="primary" link @click="openSynonymHistory(scope.row)">历史</el-button>
            <el-popconfirm title="确认删除该映射？" @confirm="handleDeleteSynonym(scope.row)">
              <template #reference>
                <el-button type="danger" link :disabled="scope.row.builtIn">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="section-header">
          <span>NiFi 模板管理</span>
          <el-space>
            <el-button @click="loadNifiTemplates">刷新</el-button>
            <el-button type="primary" @click="openTemplateCreate">新增模板</el-button>
          </el-space>
        </div>
      </template>
      <el-table :data="nifiTemplates" v-loading="loadingNifiTemplates" border style="width: 100%">
        <el-table-column prop="flowType" label="Flow 类型" width="140" />
        <el-table-column prop="processGroupId" label="Process Group ID" min-width="220" />
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="参数规则" min-width="220">
          <template #default="scope">
            <el-space wrap>
              <el-tag
                v-for="key in (scope.row.parameterSchema?.requiredKeys || [])"
                :key="`${scope.row.id}-${key}`"
                type="warning"
              >{{ key }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button type="primary" link @click="openTemplateEditor(scope.row)">编辑</el-button>
            <el-button type="success" link @click="openTemplateRun(scope.row)">触发测试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="section-header">
          <span>Bronze/Silver/Gold 分层统计</span>
          <el-space>
            <el-select v-model="layerFilter.taskType" clearable placeholder="任务类型" style="width: 130px">
              <el-option label="CLEAN" value="CLEAN" />
              <el-option label="FUSION" value="FUSION" />
            </el-select>
            <el-input-number v-model="layerFilter.taskId" :min="1" :step="1" placeholder="任务ID" style="width: 140px" />
            <el-button type="primary" :loading="loadingLayerStats" @click="queryLayerStats">查询</el-button>
            <el-button @click="resetLayerFilter">重置</el-button>
          </el-space>
        </div>
      </template>
      <el-row :gutter="12" style="margin-bottom: 10px">
        <el-col :span="6">
          <el-statistic title="Bronze 行数" :value="layerSummary.bronzeRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="Silver 行数" :value="layerSummary.silverRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="Gold 行数" :value="layerSummary.goldRows || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="任务数" :value="layerSummary.taskCount || 0" />
        </el-col>
      </el-row>
      <el-table :data="layerDetails" v-loading="loadingLayerStats" border style="width: 100%">
        <el-table-column prop="taskType" label="任务类型" width="120" />
        <el-table-column prop="taskId" label="任务ID" width="120" />
        <el-table-column prop="bronzeRows" label="Bronze" width="140" align="right" />
        <el-table-column prop="silverRows" label="Silver" width="140" align="right" />
        <el-table-column prop="goldRows" label="Gold" width="140" align="right" />
      </el-table>
    </el-card>

    <el-dialog v-model="strategyEditorVisible" width="760px" title="策略在线查看与编辑" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="策略名称">
          <el-input v-model="strategyEditorForm.name" :disabled="strategyEditorReadonly" />
        </el-form-item>
        <el-form-item label="策略编码">
          <el-input v-model="strategyEditorForm.code" :disabled="strategyEditorReadonly" />
        </el-form-item>
        <el-form-item label="策略内容">
          <el-input
            v-model="strategyEditorForm.content"
            type="textarea"
            :rows="10"
            :disabled="strategyEditorReadonly"
            placeholder="可写入策略执行说明或逻辑内容"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="strategyEditorForm.remark" :disabled="strategyEditorReadonly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="strategyEditorVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="updatingStrategy"
          :disabled="strategyEditorReadonly"
          @click="saveStrategyEditor"
        >保存修改</el-button>
      </template>
    </el-dialog>

    <RuleUploadDialog
      v-model="ruleUploadVisible"
      :submitting="uploading"
      @submit="handleRuleUpload"
    />

    <StrategyUploadDialog
      v-model="strategyUploadVisible"
      :submitting="creatingStrategy"
      @submit="handleStrategyUpload"
    />

    <el-dialog v-model="synonymEditorVisible" width="700px" :title="synonymEditorTitle" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="标准主键">
          <el-input v-model="synonymEditorForm.canonicalKey" :disabled="synonymEditorBuiltIn" placeholder="例如：整改单位ID" />
        </el-form-item>
        <el-form-item label="同义字段">
          <el-input
            v-model="synonymEditorForm.aliasesText"
            type="textarea"
            :rows="4"
            placeholder="多个值请用英文逗号分隔，例如：单位ID,org_id,organization_id"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="synonymEditorForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="synonymEditorVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSynonym" @click="saveSynonymEditor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="synonymHistoryVisible" width="920px" title="主键映射变更历史" destroy-on-close>
      <div class="history-header">
        <el-space wrap>
          <el-input
            v-model="historyCanonicalKey"
            placeholder="输入标准主键进行历史检索（支持已删除映射）"
            style="width: 320px"
            clearable
          />
          <el-button type="primary" :loading="loadingSynonymHistory" @click="querySynonymHistoryByCanonicalKey">查询</el-button>
          <el-tag v-if="historyCanonicalKey" type="info">标准主键：{{ historyCanonicalKey }}</el-tag>
        </el-space>
      </div>
      <el-table :data="synonymHistoryRows" v-loading="loadingSynonymHistory" border max-height="520">
        <el-table-column prop="versionNo" label="版本" width="80" align="center" />
        <el-table-column prop="actionType" label="动作" width="110" align="center" />
        <el-table-column prop="actorUsername" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="操作时间" width="180" />
        <el-table-column label="变更前" min-width="180">
          <template #default="scope">
            <el-popover placement="left" width="420" trigger="click">
              <template #reference>
                <el-button link type="primary">查看</el-button>
              </template>
              <pre class="json-pre-mini">{{ toPrettyJson(scope.row.beforeData) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="变更后" min-width="180">
          <template #default="scope">
            <el-popover placement="left" width="420" trigger="click">
              <template #reference>
                <el-button link type="primary">查看</el-button>
              </template>
              <pre class="json-pre-mini">{{ toPrettyJson(scope.row.afterData) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="templateEditorVisible" width="720px" :title="templateEditorTitle" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="Flow 类型">
          <el-input v-model="templateEditorForm.flowType" placeholder="例如：INGEST" />
        </el-form-item>
        <el-form-item label="Process Group ID">
          <el-input v-model="templateEditorForm.processGroupId" placeholder="请输入 NiFi process group id" />
        </el-form-item>
        <el-form-item label="必填参数键">
          <el-input
            v-model="templateEditorForm.requiredKeysText"
            type="textarea"
            :rows="3"
            placeholder="多个键以逗号分隔，例如：sourceId,triggerBy"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="templateEditorForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="templateEditorForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateEditorVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplateEditor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateRunVisible" width="720px" title="NiFi 模板触发测试" destroy-on-close>
      <el-form label-width="130px">
        <el-form-item label="Flow 类型">
          <el-input v-model="templateRunForm.flowType" disabled />
        </el-form-item>
        <el-form-item label="Process Group ID">
          <el-input v-model="templateRunForm.processGroupId" disabled />
        </el-form-item>
        <el-form-item label="参数(JSON)">
          <el-input
            v-model="templateRunForm.parametersJson"
            type="textarea"
            :rows="8"
            placeholder='例如：{"sourceId": 1001, "triggerBy": "Holmes"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateRunVisible = false">取消</el-button>
        <el-button type="primary" :loading="runningTemplate" @click="runTemplateFlow">执行触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RuleUploadDialog from '../components/dataclean/RuleUploadDialog.vue'
import StrategyUploadDialog from '../components/dataclean/StrategyUploadDialog.vue'
import {
  createFusionKeySynonym,
  createCleanStrategy,
  deleteCleanRule,
  deleteCleanStrategy,
  deleteFusionKeySynonym,
  getFusionKeySynonymDetail,
  listLayerStats,
  listNifiFlowTemplates,
  getCleanRuleDetail,
  getCleanStrategyDetail,
  saveNifiFlowTemplate,
  triggerNifiFlow,
  listFusionKeySynonyms,
  listFusionKeySynonymHistory,
  listFusionKeySynonymHistoryByCanonicalKey,
  listCleanRules,
  listCleanStrategies,
  toggleFusionKeySynonym,
  toggleCleanRule,
  toggleCleanStrategy,
  updateFusionKeySynonym,
  updateCleanRule,
  updateCleanStrategy,
  uploadCleanRule
} from '../api/cleanrule'

const loading = ref(false)
const uploading = ref(false)
const rules = ref([])
const ruleUploadVisible = ref(false)
const ruleEditorVisible = ref(false)
const updatingRule = ref(false)
const ruleEditorReadonly = ref(false)
const editingRuleId = ref('')
const loadingStrategies = ref(false)
const creatingStrategy = ref(false)
const strategyUploadVisible = ref(false)
const strategies = ref([])
const strategyEditorVisible = ref(false)
const strategyEditorReadonly = ref(false)
const updatingStrategy = ref(false)
const editingStrategyId = ref('')
const loadingSynonyms = ref(false)
const synonyms = ref([])
const synonymEditorVisible = ref(false)
const synonymEditorTitle = ref('新增主键映射')
const synonymEditorBuiltIn = ref(false)
const savingSynonym = ref(false)
const editingSynonymId = ref('')
const synonymHistoryVisible = ref(false)
const loadingSynonymHistory = ref(false)
const synonymHistoryRows = ref([])
const historyCanonicalKey = ref('')
const loadingNifiTemplates = ref(false)
const nifiTemplates = ref([])
const templateEditorVisible = ref(false)
const templateEditorTitle = ref('新增模板')
const savingTemplate = ref(false)
const templateRunVisible = ref(false)
const runningTemplate = ref(false)
const layerDetails = ref([])
const loadingLayerStats = ref(false)

const layerFilter = reactive({
  taskType: '',
  taskId: null
})

const layerSummary = reactive({
  bronzeRows: 0,
  silverRows: 0,
  goldRows: 0,
  taskCount: 0
})

const ruleEditorForm = reactive({
  name: '',
  fileName: '',
  content: '',
  remark: ''
})

const strategyEditorForm = reactive({
  name: '',
  code: '',
  content: '',
  remark: ''
})

const synonymEditorForm = reactive({
  canonicalKey: '',
  aliasesText: '',
  remark: ''
})

const templateEditorForm = reactive({
  flowType: 'INGEST',
  processGroupId: '',
  requiredKeysText: '',
  enabled: true,
  remark: ''
})

const templateRunForm = reactive({
  flowType: 'INGEST',
  processGroupId: '',
  parametersJson: '{}'
})

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

async function loadRules() {
  loading.value = true
  try {
    const { data } = await listCleanRules()
    rules.value = data.data || []
  } catch (error) {
    rules.value = []
    ElMessage.error(errorMessage(error, '加载清洗规则失败'))
  } finally {
    loading.value = false
  }
}

async function loadStrategies() {
  loadingStrategies.value = true
  try {
    const { data } = await listCleanStrategies()
    strategies.value = data.data || []
  } catch (error) {
    strategies.value = []
    ElMessage.error(errorMessage(error, '加载清洗策略失败'))
  } finally {
    loadingStrategies.value = false
  }
}

async function loadSynonyms() {
  loadingSynonyms.value = true
  try {
    const { data } = await listFusionKeySynonyms()
    synonyms.value = data.data || []
  } catch (error) {
    synonyms.value = []
    ElMessage.error(errorMessage(error, '加载主键映射失败'))
  } finally {
    loadingSynonyms.value = false
  }
}

async function loadNifiTemplates() {
  loadingNifiTemplates.value = true
  try {
    const { data } = await listNifiFlowTemplates()
    nifiTemplates.value = data.data || []
  } catch (error) {
    nifiTemplates.value = []
    ElMessage.error(errorMessage(error, '加载 NiFi 模板失败'))
  } finally {
    loadingNifiTemplates.value = false
  }
}

function resetTemplateEditor() {
  templateEditorTitle.value = '新增模板'
  templateEditorForm.flowType = 'INGEST'
  templateEditorForm.processGroupId = ''
  templateEditorForm.requiredKeysText = ''
  templateEditorForm.enabled = true
  templateEditorForm.remark = ''
}

function openTemplateCreate() {
  resetTemplateEditor()
  templateEditorVisible.value = true
}

function openTemplateEditor(row) {
  templateEditorTitle.value = '编辑模板'
  templateEditorForm.flowType = row.flowType || 'INGEST'
  templateEditorForm.processGroupId = row.processGroupId || ''
  templateEditorForm.requiredKeysText = Array.isArray(row.parameterSchema?.requiredKeys)
    ? row.parameterSchema.requiredKeys.join(', ')
    : ''
  templateEditorForm.enabled = row.enabled !== false
  templateEditorForm.remark = row.remark || ''
  templateEditorVisible.value = true
}

function openTemplateRun(row) {
  templateRunForm.flowType = row.flowType || 'INGEST'
  templateRunForm.processGroupId = row.processGroupId || ''
  templateRunForm.parametersJson = '{\n  "sourceId": 1,\n  "triggerBy": "Holmes"\n}'
  templateRunVisible.value = true
}

async function runTemplateFlow() {
  let parameters = {}
  try {
    parameters = templateRunForm.parametersJson.trim() ? JSON.parse(templateRunForm.parametersJson) : {}
  } catch {
    ElMessage.warning('参数 JSON 格式不合法')
    return
  }

  runningTemplate.value = true
  try {
    const { data } = await triggerNifiFlow({
      flowType: templateRunForm.flowType,
      processGroupId: templateRunForm.processGroupId,
      parameters
    })
    const result = data.data || {}
    const dispatch = result.dispatchStatus || 'UNKNOWN'
    if (dispatch === 'SUBMITTED') {
      ElMessage.success(`触发成功，状态：${dispatch}`)
    } else {
      ElMessage.warning(`触发完成，状态：${dispatch}`)
    }
    templateRunVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error, '触发失败'))
  } finally {
    runningTemplate.value = false
  }
}

async function saveTemplateEditor() {
  const flowType = templateEditorForm.flowType.trim().toUpperCase()
  const processGroupId = templateEditorForm.processGroupId.trim()
  if (!flowType || !processGroupId) {
    ElMessage.warning('Flow 类型和 Process Group ID 不能为空')
    return
  }
  const requiredKeys = templateEditorForm.requiredKeysText
    .split(/[,，|+]/)
    .map((it) => it.trim())
    .filter(Boolean)

  savingTemplate.value = true
  try {
    await saveNifiFlowTemplate({
      flowType,
      processGroupId,
      parameterSchema: { requiredKeys },
      enabled: templateEditorForm.enabled,
      remark: templateEditorForm.remark.trim()
    })
    ElMessage.success('模板已保存')
    templateEditorVisible.value = false
    await loadNifiTemplates()
  } catch (error) {
    ElMessage.error(errorMessage(error, '模板保存失败'))
  } finally {
    savingTemplate.value = false
  }
}

async function queryLayerStats() {
  loadingLayerStats.value = true
  try {
    const params = {}
    if (layerFilter.taskType) {
      params.taskType = layerFilter.taskType
    }
    if (layerFilter.taskId) {
      params.taskId = layerFilter.taskId
    }
    const { data } = await listLayerStats(params)
    const payload = data.data || {}
    Object.assign(layerSummary, payload.summary || {
      bronzeRows: 0,
      silverRows: 0,
      goldRows: 0,
      taskCount: 0
    })
    layerDetails.value = payload.details || []
  } catch (error) {
    layerDetails.value = []
    Object.assign(layerSummary, {
      bronzeRows: 0,
      silverRows: 0,
      goldRows: 0,
      taskCount: 0
    })
    ElMessage.error(errorMessage(error, '加载分层统计失败'))
  } finally {
    loadingLayerStats.value = false
  }
}

function resetLayerFilter() {
  layerFilter.taskType = ''
  layerFilter.taskId = null
  queryLayerStats()
}

async function handleRuleUpload(payload) {
  uploading.value = true
  try {
    await uploadCleanRule(payload)
    ElMessage.success('规则上传成功')
    ruleUploadVisible.value = false
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function handleToggle(id, enabled) {
  try {
    await toggleCleanRule(id, enabled)
    ElMessage.success('规则状态已更新')
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '更新失败'))
  }
}

async function handleDelete(rule) {
  try {
    await deleteCleanRule(rule.id)
    ElMessage.success('删除成功')
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

async function openRuleEditor(rule) {
  try {
    const { data } = await getCleanRuleDetail(rule.id)
    const detail = data.data || {}
    editingRuleId.value = String(detail.id || '')
    ruleEditorForm.name = detail.name || ''
    ruleEditorForm.fileName = detail.fileName || ''
    ruleEditorForm.content = detail.content || ''
    ruleEditorForm.remark = detail.remark || ''
    ruleEditorReadonly.value = detail.category === 'SYSTEM'
    ruleEditorVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '获取规则详情失败'))
  }
}

async function saveRuleEditor() {
  if (!editingRuleId.value) return
  if (!ruleEditorForm.name.trim() || !ruleEditorForm.fileName.trim() || !ruleEditorForm.content.trim()) {
    ElMessage.warning('规则名称、规则文件和规则内容不能为空')
    return
  }

  updatingRule.value = true
  try {
    await updateCleanRule(editingRuleId.value, {
      name: ruleEditorForm.name.trim(),
      fileName: ruleEditorForm.fileName.trim(),
      content: ruleEditorForm.content,
      remark: ruleEditorForm.remark.trim()
    })
    ElMessage.success('规则已更新')
    ruleEditorVisible.value = false
    await loadRules()
  } catch (error) {
    ElMessage.error(errorMessage(error, '规则更新失败'))
  } finally {
    updatingRule.value = false
  }
}

async function handleStrategyUpload(payload) {
  creatingStrategy.value = true
  try {
    await createCleanStrategy(payload)
    ElMessage.success('策略新增成功')
    strategyUploadVisible.value = false
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '新增失败'))
  } finally {
    creatingStrategy.value = false
  }
}

async function handleToggleStrategy(id, enabled) {
  try {
    await toggleCleanStrategy(id, enabled)
    ElMessage.success('策略状态已更新')
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '更新失败'))
  }
}

async function handleDeleteStrategy(strategy) {
  try {
    await deleteCleanStrategy(strategy.id)
    ElMessage.success('删除成功')
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

async function openStrategyEditor(strategy) {
  try {
    const { data } = await getCleanStrategyDetail(strategy.id)
    const detail = data.data || {}
    editingStrategyId.value = String(detail.id || '')
    strategyEditorForm.name = detail.name || ''
    strategyEditorForm.code = detail.code || ''
    strategyEditorForm.content = detail.content || ''
    strategyEditorForm.remark = detail.remark || ''
    strategyEditorReadonly.value = !!detail.builtIn
    strategyEditorVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '获取策略详情失败'))
  }
}

async function saveStrategyEditor() {
  if (!editingStrategyId.value) return
  if (!strategyEditorForm.name.trim() || !strategyEditorForm.code.trim()) {
    ElMessage.warning('策略名称和编码不能为空')
    return
  }

  updatingStrategy.value = true
  try {
    await updateCleanStrategy(editingStrategyId.value, {
      name: strategyEditorForm.name.trim(),
      code: strategyEditorForm.code.trim().toUpperCase(),
      content: strategyEditorForm.content,
      remark: strategyEditorForm.remark.trim()
    })
    ElMessage.success('策略已更新')
    strategyEditorVisible.value = false
    await loadStrategies()
  } catch (error) {
    ElMessage.error(errorMessage(error, '策略更新失败'))
  } finally {
    updatingStrategy.value = false
  }
}

function resetSynonymEditor() {
  editingSynonymId.value = ''
  synonymEditorTitle.value = '新增主键映射'
  synonymEditorBuiltIn.value = false
  synonymEditorForm.canonicalKey = ''
  synonymEditorForm.aliasesText = ''
  synonymEditorForm.remark = ''
}

function openSynonymCreate() {
  resetSynonymEditor()
  synonymEditorVisible.value = true
}

async function openSynonymEditor(row) {
  try {
    const { data } = await getFusionKeySynonymDetail(row.id)
    const detail = data.data || {}
    editingSynonymId.value = String(detail.id || '')
    synonymEditorTitle.value = '编辑主键映射'
    synonymEditorBuiltIn.value = !!detail.builtIn
    synonymEditorForm.canonicalKey = detail.canonicalKey || ''
    synonymEditorForm.aliasesText = Array.isArray(detail.aliases) ? detail.aliases.join(', ') : ''
    synonymEditorForm.remark = detail.remark || ''
    synonymEditorVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '获取映射详情失败'))
  }
}

async function saveSynonymEditor() {
  if (!synonymEditorForm.canonicalKey.trim()) {
    ElMessage.warning('标准主键不能为空')
    return
  }
  const aliases = synonymEditorForm.aliasesText
    .split(/[,，|+]/)
    .map((it) => it.trim())
    .filter(Boolean)

  savingSynonym.value = true
  try {
    const payload = {
      canonicalKey: synonymEditorForm.canonicalKey.trim(),
      aliases,
      remark: synonymEditorForm.remark.trim()
    }
    if (editingSynonymId.value) {
      await updateFusionKeySynonym(editingSynonymId.value, payload)
      ElMessage.success('主键映射已更新')
    } else {
      await createFusionKeySynonym(payload)
      ElMessage.success('主键映射已新增')
    }
    synonymEditorVisible.value = false
    resetSynonymEditor()
    await loadSynonyms()
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存主键映射失败'))
  } finally {
    savingSynonym.value = false
  }
}

async function handleToggleSynonym(id, enabled) {
  try {
    await toggleFusionKeySynonym(id, enabled)
    ElMessage.success('映射状态已更新')
    await loadSynonyms()
  } catch (error) {
    ElMessage.error(errorMessage(error, '更新失败'))
  }
}

async function handleDeleteSynonym(row) {
  try {
    await deleteFusionKeySynonym(row.id)
    ElMessage.success('删除成功')
    await loadSynonyms()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

async function openSynonymHistory(row) {
  synonymHistoryVisible.value = true
  loadingSynonymHistory.value = true
  historyCanonicalKey.value = row.canonicalKey || ''
  try {
    const { data } = await listFusionKeySynonymHistory(row.id, { limit: 100 })
    synonymHistoryRows.value = data.data || []
  } catch (error) {
    synonymHistoryRows.value = []
    ElMessage.error(errorMessage(error, '加载映射历史失败'))
  } finally {
    loadingSynonymHistory.value = false
  }
}

function openSynonymHistorySearch() {
  synonymHistoryVisible.value = true
  historyCanonicalKey.value = ''
  synonymHistoryRows.value = []
}

async function querySynonymHistoryByCanonicalKey() {
  if (!historyCanonicalKey.value.trim()) {
    ElMessage.warning('请输入标准主键')
    return
  }
  loadingSynonymHistory.value = true
  try {
    const { data } = await listFusionKeySynonymHistoryByCanonicalKey({
      canonicalKey: historyCanonicalKey.value.trim(),
      limit: 200
    })
    synonymHistoryRows.value = data.data || []
  } catch (error) {
    synonymHistoryRows.value = []
    ElMessage.error(errorMessage(error, '加载映射历史失败'))
  } finally {
    loadingSynonymHistory.value = false
  }
}

function toPrettyJson(value) {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return String(value ?? '')
  }
}

onMounted(async () => {
  await Promise.allSettled([loadRules(), loadStrategies(), loadSynonyms(), loadNifiTemplates(), queryLayerStats()])
})
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-header {
  margin-bottom: 10px;
}

.json-pre-mini {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
