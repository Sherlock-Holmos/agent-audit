<template>
  <GovernancePageShell>

    <el-card shadow="never" style="margin-top: 0">
      <template #header>
        <GovernanceSectionHeader title="融合主键同义词管理">
          <template #actions>
          <el-space>
            <el-button @click="openSynonymHistorySearch">历史查询</el-button>
            <el-button type="primary" @click="openSynonymCreate">新增映射</el-button>
          </el-space>
          </template>
        </GovernanceSectionHeader>
      </template>
      <GovernanceTable :data="synonyms" :loading="loadingSynonyms" layout-storage-key="governance-synonym-table">
        <template #default="{ resolveWidth, resolveMinWidth }">
          <el-table-column
            column-key="canonicalKey"
            prop="canonicalKey"
            label="标准主键"
            :width="resolveWidth('canonicalKey')"
            :min-width="resolveMinWidth('canonicalKey', 180)"
          />
          <el-table-column column-key="aliases" label="同义字段" :width="resolveWidth('aliases')" :min-width="resolveMinWidth('aliases', 320)">
            <template #default="scope">
              <el-space wrap>
                <el-tag v-for="item in scope.row.aliases || []" :key="`${scope.row.id}-${item}`" type="info">{{ item }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column column-key="builtIn" label="类型" :width="resolveWidth('builtIn')" :min-width="resolveMinWidth('builtIn', 100)" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.builtIn ? 'info' : 'success'">
                {{ scope.row.builtIn ? '系统' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column column-key="enabled" label="启用" :width="resolveWidth('enabled')" :min-width="resolveMinWidth('enabled', 90)" align="center">
            <template #default="scope">
              <el-switch :model-value="scope.row.enabled" @change="(val) => handleToggleSynonym(scope.row.id, val)" />
            </template>
          </el-table-column>
          <el-table-column
            column-key="updatedAt"
            prop="updatedAt"
            label="更新时间"
            :width="resolveWidth('updatedAt')"
            :min-width="resolveMinWidth('updatedAt', 180)"
          />
          <el-table-column column-key="actions" label="操作" :width="resolveWidth('actions')" :min-width="resolveMinWidth('actions', 180)" align="center" fixed="right">
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
        </template>
      </GovernanceTable>
    </el-card>

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
  </GovernancePageShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernancePageShell from '../components/dataclean/GovernancePageShell.vue'
import GovernanceSectionHeader from '../components/dataclean/GovernanceSectionHeader.vue'
import GovernanceTable from '../components/dataclean/GovernanceTable.vue'
import { useAsyncTask } from '../composables/useAsyncTask'
import {
  createFusionKeySynonym,
  deleteFusionKeySynonym,
  getFusionKeySynonymDetail,
  listFusionKeySynonymHistory,
  listFusionKeySynonymHistoryByCanonicalKey,
  listFusionKeySynonyms,
  toggleFusionKeySynonym,
  updateFusionKeySynonym
} from '../api/fusion-key-synonym'

const { loading: loadingSynonyms, run: runLoadSynonyms } = useAsyncTask()
const { loading: savingSynonym, run: runSaveSynonym } = useAsyncTask()
const { loading: loadingSynonymHistory, run: runLoadSynonymHistory } = useAsyncTask()
const { run: runSynonymOperation } = useAsyncTask()

const synonyms = ref([])
const synonymEditorVisible = ref(false)
const synonymEditorTitle = ref('新增主键映射')
const synonymEditorBuiltIn = ref(false)
const editingSynonymId = ref('')
const synonymHistoryVisible = ref(false)
const synonymHistoryRows = ref([])
const historyCanonicalKey = ref('')

const synonymEditorForm = reactive({
  canonicalKey: '',
  aliasesText: '',
  remark: ''
})

async function loadSynonyms() {
  const result = await runLoadSynonyms(() => listFusionKeySynonyms(), {
    errorMessage: '加载主键映射失败',
    onError: () => {
      synonyms.value = []
    }
  })
  if (result) {
    synonyms.value = result.data?.data || []
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
  const result = await runSynonymOperation(() => getFusionKeySynonymDetail(row.id), {
    errorMessage: '获取映射详情失败'
  })
  if (result) {
    const detail = result.data?.data || {}
    editingSynonymId.value = String(detail.id || '')
    synonymEditorTitle.value = '编辑主键映射'
    synonymEditorBuiltIn.value = !!detail.builtIn
    synonymEditorForm.canonicalKey = detail.canonicalKey || ''
    synonymEditorForm.aliasesText = Array.isArray(detail.aliases) ? detail.aliases.join(', ') : ''
    synonymEditorForm.remark = detail.remark || ''
    synonymEditorVisible.value = true
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

  let result = null
  if (editingSynonymId.value) {
    result = await runSaveSynonym(() => updateFusionKeySynonym(editingSynonymId.value, {
      canonicalKey: synonymEditorForm.canonicalKey.trim(),
      aliases,
      remark: synonymEditorForm.remark.trim()
    }), {
      errorMessage: '保存主键映射失败'
    })
  } else {
    result = await runSaveSynonym(() => createFusionKeySynonym({
      canonicalKey: synonymEditorForm.canonicalKey.trim(),
      aliases,
      remark: synonymEditorForm.remark.trim()
    }), {
      errorMessage: '保存主键映射失败'
    })
  }

  if (result) {
    if (editingSynonymId.value) {
      ElMessage.success('主键映射已更新')
    } else {
      ElMessage.success('主键映射已新增')
    }
    synonymEditorVisible.value = false
    resetSynonymEditor()
    await loadSynonyms()
  }
}

async function handleToggleSynonym(id, enabled) {
  const result = await runSynonymOperation(() => toggleFusionKeySynonym(id, enabled), {
    errorMessage: '更新失败',
    successMessage: '映射状态已更新'
  })
  if (result) {
    await loadSynonyms()
  }
}

async function handleDeleteSynonym(row) {
  const result = await runSynonymOperation(() => deleteFusionKeySynonym(row.id), {
    errorMessage: '删除失败',
    successMessage: '删除成功'
  })
  if (result) {
    await loadSynonyms()
  }
}

async function openSynonymHistory(row) {
  synonymHistoryVisible.value = true
  historyCanonicalKey.value = row.canonicalKey || ''
  const result = await runLoadSynonymHistory(() => listFusionKeySynonymHistory(row.id, { limit: 100 }), {
    errorMessage: '加载映射历史失败',
    onError: () => {
      synonymHistoryRows.value = []
    }
  })
  if (result) {
    synonymHistoryRows.value = result.data?.data || []
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

  const result = await runLoadSynonymHistory(() => listFusionKeySynonymHistoryByCanonicalKey({
    canonicalKey: historyCanonicalKey.value.trim(),
    limit: 200
  }), {
    errorMessage: '加载映射历史失败',
    onError: () => {
      synonymHistoryRows.value = []
    }
  })
  if (result) {
    synonymHistoryRows.value = result.data?.data || []
  }
}

function toPrettyJson(value) {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return String(value ?? '')
  }
}

onMounted(loadSynonyms)
</script>

<style scoped>
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
