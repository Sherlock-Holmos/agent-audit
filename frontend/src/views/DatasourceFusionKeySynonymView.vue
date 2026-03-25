<template>
  <div>
    <GovernanceSubNav />

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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import GovernanceSubNav from '../components/dataclean/GovernanceSubNav.vue'
import GovernanceSectionHeader from '../components/dataclean/GovernanceSectionHeader.vue'
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
import { getErrorMessage } from '../utils/error'

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

const synonymEditorForm = reactive({
  canonicalKey: '',
  aliasesText: '',
  remark: ''
})

async function loadSynonyms() {
  loadingSynonyms.value = true
  try {
    const { data } = await listFusionKeySynonyms()
    synonyms.value = data.data || []
  } catch (error) {
    synonyms.value = []
    ElMessage.error(getErrorMessage(error, '加载主键映射失败'))
  } finally {
    loadingSynonyms.value = false
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
    ElMessage.error(getErrorMessage(error, '获取映射详情失败'))
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
    ElMessage.error(getErrorMessage(error, '保存主键映射失败'))
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
    ElMessage.error(getErrorMessage(error, '更新失败'))
  }
}

async function handleDeleteSynonym(row) {
  try {
    await deleteFusionKeySynonym(row.id)
    ElMessage.success('删除成功')
    await loadSynonyms()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除失败'))
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
    ElMessage.error(getErrorMessage(error, '加载映射历史失败'))
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
    ElMessage.error(getErrorMessage(error, '加载映射历史失败'))
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
