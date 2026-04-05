<template>
  <GovernancePageShell>

    <GovernanceCardSection title="NiFi 模板管理" card-style="margin-top: 0">
      <template #actions>
        <el-space>
          <el-button @click="loadNifiTemplates">刷新</el-button>
          <el-button :loading="reconcilingTasks" @click="reconcileTasks">任务对账</el-button>
          <el-button type="warning" :loading="bootstrappingTemplates" @click="bootstrapTemplates">一键初始化清洗/融合模板</el-button>
          <el-button type="info" @click="openBlueprintDialog('CLEAN')">创建清洗蓝图</el-button>
          <el-button type="info" @click="openBlueprintDialog('FUSION')">创建融合蓝图</el-button>
          <el-button type="primary" @click="openBlueprintDialog('CUSTOM')">创建蓝图</el-button>
          <el-button type="primary" @click="openTemplateCreate">新增模板</el-button>
        </el-space>
      </template>
      <GovernanceTable
        :data="nifiTemplates"
        :loading="loadingNifiTemplates"
        layout-storage-key="governance-nifi-template-table"
        :column-keys="['flowType', 'processGroupId', 'versionNo', 'enabled', 'updatedAt', 'parameterSchema', 'actions']"
      >
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column
            column-key="flowType"
            prop="flowType"
            label="Flow 类型"
            :width="resolveWidth('flowType', 140)"
            :min-width="resolveMinWidth('flowType', 140)"
          />
          <el-table-column
            column-key="processGroupId"
            prop="processGroupId"
            label="Process Group ID"
            :width="resolveWidth('processGroupId', 240)"
            :min-width="resolveMinWidth('processGroupId', 240)"
          />
          <el-table-column
            column-key="versionNo"
            prop="versionNo"
            label="版本"
            :width="resolveWidth('versionNo', 100)"
            :min-width="resolveMinWidth('versionNo', 100)"
            align="center"
          />
          <el-table-column column-key="enabled" label="启用" :width="resolveWidth('enabled', 100)" :min-width="resolveMinWidth('enabled', 100)" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            column-key="updatedAt"
            prop="updatedAt"
            label="更新时间"
            :width="resolveWidth('updatedAt', 180)"
            :min-width="resolveMinWidth('updatedAt', 180)"
          />
          <el-table-column column-key="parameterSchema" label="参数规则" :width="resolveWidth('parameterSchema', 220)" :min-width="resolveMinWidth('parameterSchema', 220)">
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
          <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions', 160)" :min-width="resolveMinWidth('actions', 160)" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openTemplateEditor(scope.row)">编辑</el-button>
              <el-button type="success" link @click="openTemplateRun(scope.row)">触发测试</el-button>
            </template>
          </el-table-column>
        </template>
      </GovernanceTable>
    </GovernanceCardSection>

    <GovernanceCardSection title="任务对账历史" card-style="margin-top: 12px">
      <template #actions>
        <el-space wrap>
          <el-select v-model="singleReconcile.taskType" style="width: 140px" placeholder="任务类型">
            <el-option label="清洗任务" value="CLEAN" />
            <el-option label="融合任务" value="FUSION" />
          </el-select>
          <el-input-number v-model="singleReconcile.taskId" :min="1" :step="1" :precision="0" style="width: 160px" />
          <el-button :loading="reconcilingSingleTask" @click="reconcileSingleTask">单任务对账</el-button>
          <el-button :loading="loadingReconcileHistory" @click="loadReconcileHistory">刷新历史</el-button>
        </el-space>
      </template>
      <GovernanceTable
        :data="reconcileHistory"
        :loading="loadingReconcileHistory"
        layout-storage-key="governance-nifi-reconcile-history-table"
        :column-keys="['createdAt', 'triggerType', 'triggerUser', 'reconcileMode', 'taskType', 'taskId', 'result']"
      >
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column column-key="createdAt" prop="createdAt" label="时间" :width="resolveWidth('createdAt', 180)" :min-width="resolveMinWidth('createdAt', 180)" />
          <el-table-column column-key="triggerType" prop="triggerType" label="触发来源" :width="resolveWidth('triggerType', 100)" :min-width="resolveMinWidth('triggerType', 100)" align="center" />
          <el-table-column column-key="triggerUser" prop="triggerUser" label="触发用户" :width="resolveWidth('triggerUser', 140)" :min-width="resolveMinWidth('triggerUser', 140)" />
          <el-table-column column-key="reconcileMode" prop="reconcileMode" label="模式" :width="resolveWidth('reconcileMode', 90)" :min-width="resolveMinWidth('reconcileMode', 90)" align="center" />
          <el-table-column column-key="taskType" prop="taskType" label="任务类型" :width="resolveWidth('taskType', 100)" :min-width="resolveMinWidth('taskType', 100)" align="center" />
          <el-table-column column-key="taskId" prop="taskId" label="任务ID" :width="resolveWidth('taskId', 100)" :min-width="resolveMinWidth('taskId', 100)" align="center" />
          <el-table-column column-key="result" label="结果摘要" :width="resolveWidth('result', 420)" :min-width="resolveMinWidth('result', 420)">
            <template #default="scope">
              <el-space wrap>
                <el-tag v-if="scope.row.result?.completedClean !== undefined" type="success">清洗完成 {{ scope.row.result.completedClean || 0 }}</el-tag>
                <el-tag v-if="scope.row.result?.completedFusion !== undefined" type="success">融合完成 {{ scope.row.result.completedFusion || 0 }}</el-tag>
                <el-tag v-if="scope.row.result?.failedTimeout !== undefined" type="danger">超时失败 {{ scope.row.result.failedTimeout || 0 }}</el-tag>
                <el-tag v-if="scope.row.result?.stillRunning !== undefined" type="warning">仍运行 {{ scope.row.result.stillRunning || 0 }}</el-tag>
                <el-tag v-if="scope.row.result?.outcome" :type="scope.row.result.outcome === 'COMPLETED' ? 'success' : (scope.row.result.outcome === 'FAILED_TIMEOUT' ? 'danger' : 'info')">{{ scope.row.result.outcome }}</el-tag>
                <span v-if="scope.row.result?.taskId">#{{ scope.row.result.taskId }}</span>
              </el-space>
            </template>
          </el-table-column>
        </template>
      </GovernanceTable>
    </GovernanceCardSection>

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

    <el-dialog v-model="blueprintVisible" width="860px" title="NiFi 流程蓝图创建" destroy-on-close>
      <el-form label-width="140px">
        <el-form-item label="蓝图预设">
          <el-select v-model="blueprintForm.preset" style="width: 240px" @change="applyBlueprintPreset">
            <el-option label="清洗蓝图" value="CLEAN" />
            <el-option label="融合蓝图" value="FUSION" />
            <el-option label="自定义蓝图" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程组名称">
          <el-input v-model="blueprintForm.groupName" placeholder="例如：AUDIT_CLEAN" />
        </el-form-item>
        <el-form-item label="父流程组ID">
          <el-input v-model="blueprintForm.parentProcessGroupId" placeholder="留空则使用默认流程组或 root" />
        </el-form-item>
        <el-form-item label="启动后自动运行">
          <el-switch v-model="blueprintForm.startAfterCreate" />
        </el-form-item>
        <el-form-item label="蓝图 JSON">
          <el-input
            v-model="blueprintForm.blueprintJson"
            type="textarea"
            :rows="16"
            :placeholder="blueprintJsonPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blueprintVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingBlueprint" @click="createBlueprint">创建</el-button>
      </template>
    </el-dialog>
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceCardSection from '../components/dataclean/GovernanceCardSection.vue'
import GovernanceTable from '../components/dataclean/GovernanceTable.vue'
import { bootstrapNifiEtlTemplates, listNifiFlowTemplates, listNifiReconcileHistory, provisionNifiFlowBlueprint, reconcileNifiRunningTasks, reconcileNifiTask, saveNifiFlowTemplate, triggerNifiFlow } from '../api/nifi-control-plane'
import { useAsyncTask } from '../composables/useAsyncTask'

const nifiTemplates = ref([])
const templateEditorVisible = ref(false)
const templateEditorTitle = ref('新增模板')
const templateRunVisible = ref(false)
const blueprintVisible = ref(false)
const reconcileHistory = ref([])

const { loading: loadingNifiTemplates, run: runLoadNifiTemplates } = useAsyncTask()
const { loading: savingTemplate, run: runSaveTemplate } = useAsyncTask()
const { loading: runningTemplate, run: runTemplateTrigger } = useAsyncTask()
const { loading: bootstrappingTemplates, run: runTemplateBootstrap } = useAsyncTask()
const { loading: reconcilingTasks, run: runTaskReconcile } = useAsyncTask()
const { loading: loadingReconcileHistory, run: runLoadReconcileHistory } = useAsyncTask()
const { loading: reconcilingSingleTask, run: runSingleTaskReconcile } = useAsyncTask()
const { loading: creatingBlueprint, run: runCreateBlueprint } = useAsyncTask()

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

const singleReconcile = reactive({
  taskType: 'CLEAN',
  taskId: 1
})

const blueprintForm = reactive({
  preset: 'CLEAN',
  groupName: 'AUDIT_CLEAN',
  parentProcessGroupId: '',
  startAfterCreate: false,
  blueprintJson: ''
})

const blueprintJsonPlaceholder = '预设模式下可直接生成默认蓝图；自定义模式下在这里输入完整 JSON'

function buildPresetBlueprintJson(preset) {
  if (preset === 'CUSTOM') {
    return JSON.stringify({
      groupName: 'AUDIT_FLOW',
      parameterContext: {
        name: 'AUDIT_FLOW_PARAMS',
        parameters: {
          ownerUsername: 'anonymous',
          taskId: '1',
          dataServiceBaseUrl: 'http://localhost:8082'
        }
      },
      controllerServices: [],
      processors: [],
      connections: []
    }, null, 2)
  }

  return JSON.stringify({
    preset,
    groupName: preset === 'FUSION' ? 'AUDIT_FUSION' : 'AUDIT_CLEAN'
  }, null, 2)
}

async function loadNifiTemplates() {
  const result = await runLoadNifiTemplates(() => listNifiFlowTemplates(), {
    errorMessage: '加载 NiFi 模板失败',
    onError: () => {
      nifiTemplates.value = []
    }
  })
  if (result) {
    nifiTemplates.value = result.data?.data || []
  }
}

async function loadReconcileHistory() {
  const result = await runLoadReconcileHistory(() => listNifiReconcileHistory({ limit: 100 }), {
    errorMessage: '加载对账历史失败',
    onError: () => {
      reconcileHistory.value = []
    }
  })
  if (result) {
    reconcileHistory.value = result.data?.data || []
  }
}

async function bootstrapTemplates() {
  const result = await runTemplateBootstrap(() => bootstrapNifiEtlTemplates(), {
    errorMessage: '初始化 NiFi 模板失败',
    successMessage: '清洗/融合模板初始化完成'
  })
  if (result) {
    await loadNifiTemplates()
  }
}

function applyBlueprintPreset(preset) {
  const normalizedPreset = preset || blueprintForm.preset || 'CLEAN'
  blueprintForm.groupName = normalizedPreset === 'FUSION' ? 'AUDIT_FUSION' : (normalizedPreset === 'CUSTOM' ? 'AUDIT_FLOW' : 'AUDIT_CLEAN')
  blueprintForm.blueprintJson = buildPresetBlueprintJson(normalizedPreset)
}

function openBlueprintDialog(preset = 'CLEAN') {
  blueprintForm.preset = preset
  applyBlueprintPreset(preset)
  blueprintVisible.value = true
}

async function createBlueprint() {
  let blueprint = {}
  try {
    blueprint = blueprintForm.blueprintJson.trim() ? JSON.parse(blueprintForm.blueprintJson) : {}
  } catch {
    ElMessage.warning('蓝图 JSON 格式不合法')
    return
  }

  const result = await runCreateBlueprint(() => provisionNifiFlowBlueprint({
    preset: blueprintForm.preset === 'CUSTOM' ? '' : blueprintForm.preset,
    groupName: blueprintForm.groupName.trim(),
    parentProcessGroupId: blueprintForm.parentProcessGroupId.trim(),
    startAfterCreate: blueprintForm.startAfterCreate,
    ...blueprint
  }), {
    errorMessage: '创建蓝图失败',
    successMessage: '蓝图已创建'
  })
  if (!result) {
    return
  }
  const payload = result.data?.data || {}
  ElMessage.success(`已创建流程组：${payload.processGroupId || 'UNKNOWN'}`)
  blueprintVisible.value = false
}

async function reconcileTasks() {
  const result = await runTaskReconcile(() => reconcileNifiRunningTasks({ limit: 100 }), {
    errorMessage: '任务对账失败'
  })
  if (!result) {
    return
  }
  const payload = result.data?.data || {}
  ElMessage.success(
    `对账完成：清洗完成 ${payload.completedClean || 0}，融合完成 ${payload.completedFusion || 0}，超时失败 ${payload.failedTimeout || 0}，仍运行 ${payload.stillRunning || 0}`
  )
  await loadReconcileHistory()
}

async function reconcileSingleTask() {
  const taskId = Number(singleReconcile.taskId)
  if (!taskId || taskId <= 0) {
    ElMessage.warning('请输入有效的任务ID')
    return
  }
  const result = await runSingleTaskReconcile(() => reconcileNifiTask({
    taskType: singleReconcile.taskType,
    taskId
  }), {
    errorMessage: '单任务对账失败'
  })
  if (!result) {
    return
  }
  const payload = result.data?.data || {}
  ElMessage.success(`单任务对账完成：${payload.outcome || 'UNKNOWN'}`)
  await loadReconcileHistory()
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

  const result = await runTemplateTrigger(() => triggerNifiFlow({
      flowType: templateRunForm.flowType,
      processGroupId: templateRunForm.processGroupId,
      parameters
    }), {
    errorMessage: '触发失败'
  })
  if (!result) {
    return
  }

  const payload = result.data?.data || {}
  const dispatch = payload.dispatchStatus || 'UNKNOWN'
  if (dispatch === 'SUBMITTED') {
    ElMessage.success(`触发成功，状态：${dispatch}`)
  } else {
    ElMessage.warning(`触发完成，状态：${dispatch}`)
  }
  templateRunVisible.value = false
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

  const result = await runSaveTemplate(() => saveNifiFlowTemplate({
      flowType,
      processGroupId,
      parameterSchema: { requiredKeys },
      enabled: templateEditorForm.enabled,
      remark: templateEditorForm.remark.trim()
    }), {
    errorMessage: '模板保存失败',
    successMessage: '模板已保存'
  })
  if (result) {
    templateEditorVisible.value = false
    await loadNifiTemplates()
  }
}

onMounted(async () => {
  await loadNifiTemplates()
  await loadReconcileHistory()
})
</script>
