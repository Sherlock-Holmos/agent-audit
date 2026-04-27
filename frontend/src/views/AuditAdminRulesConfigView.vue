<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>系统规则配置中心</span>
          <div>
            <el-button @click="openAddRule">新增规则</el-button>
            <el-button type="primary" @click="refreshSnapshot">刷新</el-button>
          </div>
        </div>
      </template>
      <AppDataTable :data="rules" layout-storage-key="app:table-layout:audit-admin:rules" :show-pagination="false" :with-card="false">
        <template #default>
        <el-table-column prop="name" label="规则名称" min-width="260" />
        <el-table-column prop="updatedAt" label="最近更新时间" width="180" />
        <el-table-column label="是否启用" width="120">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => onRuleToggle(scope.row.id, val)" />
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>整改提醒策略配置</span>
          <div>
            <el-button @click="openReminderDialog()">新增提醒策略</el-button>
            <el-button type="primary" plain @click="triggerReminderScan">手动扫描提醒</el-button>
          </div>
        </div>
      </template>
      <AppDataTable :data="reminderRules" layout-storage-key="app:table-layout:audit-admin:reminder-rules" :show-pagination="false" :with-card="false">
        <template #default>
        <el-table-column prop="name" label="策略名称" min-width="220" />
        <el-table-column prop="triggerTypeLabel" label="触发类型" width="160" />
        <el-table-column prop="triggerValue" label="天数" width="100" />
        <el-table-column prop="updatedAt" label="最近更新时间" width="180" />
        <el-table-column label="启用" width="100">
          <template #default="scope">
            <el-switch :model-value="scope.row.enabled" @change="(val) => onReminderToggle(scope.row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button link type="primary" @click="openReminderDialog(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeReminderRule(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-card shadow="never">
      <template #header><span>规则设计建议</span></template>
      <el-alert title="可将规则按事件触发、状态约束、材料校验三个层次治理，避免流程断点。" type="info" :closable="false" show-icon />
      <el-collapse class="mt-12">
        <el-collapse-item title="事件触发类规则" name="event">
          例如：逾期 3 天未更新自动预警，关键问题状态变更自动抄送。
        </el-collapse-item>
        <el-collapse-item title="状态约束类规则" name="state">
          例如：主任务未签收前禁止派发子任务，退回修改后必须补充反馈说明。
        </el-collapse-item>
        <el-collapse-item title="材料校验类规则" name="material">
          例如：整改完成进度达到 100% 前必须上传至少一份证明材料。
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-dialog v-model="ruleDialogVisible" title="新增系统规则" width="480px">
      <el-form label-width="90px">
        <el-form-item label="规则名称">
          <el-input v-model="newRuleName" placeholder="请输入规则名称" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onRuleCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reminderDialogVisible" :title="editingReminderId ? '编辑提醒策略' : '新增提醒策略'" width="520px">
      <el-form :model="reminderForm" label-width="90px">
        <el-form-item label="策略名称">
          <el-input v-model="reminderForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="触发类型">
          <el-select v-model="reminderForm.triggerType" style="width: 100%">
            <el-option label="截止前提醒" value="BEFORE_DEADLINE" />
            <el-option label="逾期后第N天提醒" value="OVERDUE" />
            <el-option label="逾期按周期提醒" value="INTERVAL_DAYS" />
          </el-select>
        </el-form-item>
        <el-form-item label="天数">
          <el-input-number v-model="reminderForm.triggerValue" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="reminderForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reminderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReminderRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addRule,
  createReminderRule,
  deleteReminderRule,
  listReminderRules,
  runReminderScan,
  updateReminderRule,
  updateRule
} from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import AppDataTable from '../components/shared/AppDataTable.vue'

const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const rules = computed(() => snapshot.value.rules)

const ruleDialogVisible = ref(false)
const newRuleName = ref('')
const reminderDialogVisible = ref(false)
const editingReminderId = ref(null)
const reminderRulesRaw = ref([])

const reminderForm = reactive({
  name: '',
  triggerType: 'BEFORE_DEADLINE',
  triggerValue: 3,
  enabled: true
})

const TRIGGER_TYPE_LABELS = {
  BEFORE_DEADLINE: '截止前提醒',
  OVERDUE: '逾期后提醒',
  INTERVAL_DAYS: '逾期周期提醒'
}

const reminderRules = computed(() =>
  reminderRulesRaw.value.map((item) => ({
    ...item,
    triggerTypeLabel: TRIGGER_TYPE_LABELS[item.triggerType] || item.triggerType
  }))
)

async function loadReminderRules() {
  try {
    reminderRulesRaw.value = await listReminderRules()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '提醒策略加载失败')
  }
}

function resetReminderForm() {
  reminderForm.name = ''
  reminderForm.triggerType = 'BEFORE_DEADLINE'
  reminderForm.triggerValue = 3
  reminderForm.enabled = true
}

function openReminderDialog(row = null) {
  editingReminderId.value = row?.id || null
  if (row) {
    reminderForm.name = row.name
    reminderForm.triggerType = row.triggerType
    reminderForm.triggerValue = Number(row.triggerValue) || 1
    reminderForm.enabled = !!row.enabled
  } else {
    resetReminderForm()
  }
  reminderDialogVisible.value = true
}

async function submitReminderRule() {
  if (!reminderForm.name.trim()) {
    ElMessage.warning('策略名称不能为空')
    return
  }

  const payload = {
    name: reminderForm.name.trim(),
    triggerType: reminderForm.triggerType,
    triggerValue: Number(reminderForm.triggerValue) || 1,
    enabled: reminderForm.enabled
  }

  try {
    if (editingReminderId.value) {
      await updateReminderRule(editingReminderId.value, payload)
      ElMessage.success('提醒策略已更新')
    } else {
      await createReminderRule(payload)
      ElMessage.success('提醒策略已新增')
    }
    reminderDialogVisible.value = false
    await loadReminderRules()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '提醒策略保存失败')
  }
}

async function onReminderToggle(row, enabled) {
  try {
    await updateReminderRule(row.id, { enabled })
    await loadReminderRules()
    ElMessage.success('提醒策略状态已更新')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '提醒策略更新失败')
  }
}

async function removeReminderRule(row) {
  try {
    await ElMessageBox.confirm(`确认删除提醒策略“${row.name}”吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteReminderRule(row.id)
    await loadReminderRules()
    ElMessage.success('提醒策略已删除')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '提醒策略删除失败')
  }
}

async function triggerReminderScan() {
  try {
    const result = await runReminderScan()
    ElMessage.success(`提醒扫描完成，本次发送 ${Number(result?.count || 0)} 条通知`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '提醒扫描失败')
  }
}

function openAddRule() {
  newRuleName.value = ''
  ruleDialogVisible.value = true
}

async function onRuleCreate() {
  if (!newRuleName.value.trim()) {
    ElMessage.warning('规则名称不能为空')
    return
  }
  try {
    await addRule(newRuleName.value.trim())
    await refreshSnapshot()
    ruleDialogVisible.value = false
    ElMessage.success('规则已新增')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '规则新增失败')
  }
}

async function onRuleToggle(ruleId, enabled) {
  try {
    await updateRule(ruleId, enabled)
    await refreshSnapshot()
    ElMessage.success('规则状态已更新')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '规则更新失败')
  }
}

onMounted(() => {
  loadReminderRules()
})
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mt-12 {
  margin-top: 12px;
}
</style>
