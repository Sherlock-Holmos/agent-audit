<template>
  <GovernancePageShell>

    <GovernanceCardSection title="NiFi 模板管理" card-style="margin-top: 0">
      <template #actions>
        <el-space>
          <el-button @click="loadNifiTemplates">刷新</el-button>
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
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceCardSection from '../components/dataclean/GovernanceCardSection.vue'
import GovernanceTable from '../components/dataclean/GovernanceTable.vue'
import { listNifiFlowTemplates, saveNifiFlowTemplate, triggerNifiFlow } from '../api/nifi-control-plane'
import { useAsyncTask } from '../composables/useAsyncTask'

const nifiTemplates = ref([])
const templateEditorVisible = ref(false)
const templateEditorTitle = ref('新增模板')
const templateRunVisible = ref(false)

const { loading: loadingNifiTemplates, run: runLoadNifiTemplates } = useAsyncTask()
const { loading: savingTemplate, run: runSaveTemplate } = useAsyncTask()
const { loading: runningTemplate, run: runTemplateTrigger } = useAsyncTask()

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

onMounted(loadNifiTemplates)
</script>
