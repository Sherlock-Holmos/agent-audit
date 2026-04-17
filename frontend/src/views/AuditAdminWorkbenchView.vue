<template>
  <div class="page-wrap">
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="item in statCards" :key="item.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header><span>重点问题督办</span></template>
          <el-table :data="focusIssues" border>
            <el-table-column prop="code" label="问题编号" width="130" />
            <el-table-column prop="title" label="问题标题" min-width="220" />
            <el-table-column prop="unit" label="被审单位" width="140" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="level" label="等级" width="80" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="mb-16">
          <template #header>
            <div class="header-line">
              <span>系统规则配置</span>
              <el-button size="small" @click="openAddRule">新增规则</el-button>
            </div>
          </template>
          <el-table :data="rules" border>
            <el-table-column prop="name" label="规则名称" min-width="170" />
            <el-table-column label="启用" width="90">
              <template #default="scope">
                <el-switch :model-value="scope.row.enabled" @change="(val) => onRuleToggle(scope.row.id, val)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header><span>用户权限管理</span></template>
          <el-table :data="users" border>
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="department" label="部门" width="120" />
            <el-table-column label="角色" min-width="170">
              <template #default="scope">
                <el-select :model-value="scope.row.role" @change="(val) => onRoleChange(scope.row.id, val)">
                  <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="ruleDialogVisible" title="新增系统规则" width="420px">
      <el-input v-model="newRuleName" placeholder="请输入规则名称" maxlength="100" show-word-limit />
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onRuleCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ROLE_OPTIONS } from '../constants/rbac'
import {
  addRule,
  getGlobalOverview,
  getRectificationSnapshot,
  updateRule,
  updateUserRole
} from '../utils/rectificationStore'

const roleOptions = ROLE_OPTIONS
const ruleDialogVisible = ref(false)
const newRuleName = ref('')
const stamp = ref(0)

const snapshot = computed(() => {
  stamp.value
  return getRectificationSnapshot()
})

const overview = computed(() => {
  stamp.value
  return getGlobalOverview()
})

const statCards = computed(() => [
  { label: '问题总数', value: overview.value.totalIssues },
  { label: '整改中', value: overview.value.inProgressIssues },
  { label: '已完成', value: overview.value.completedIssues },
  { label: '逾期任务', value: overview.value.overdueTasks }
])

const focusIssues = computed(() => overview.value.focusIssues)
const rules = computed(() => snapshot.value.rules)
const users = computed(() => snapshot.value.users)

function refresh() {
  stamp.value += 1
}

function openAddRule() {
  newRuleName.value = ''
  ruleDialogVisible.value = true
}

function onRuleCreate() {
  if (!newRuleName.value.trim()) {
    ElMessage.warning('规则名称不能为空')
    return
  }
  addRule(newRuleName.value.trim())
  ruleDialogVisible.value = false
  refresh()
  ElMessage.success('规则已新增')
}

function onRuleToggle(ruleId, enabled) {
  updateRule(ruleId, enabled)
  refresh()
  ElMessage.success('规则状态已更新')
}

function onRoleChange(userId, role) {
  updateUserRole(userId, role)
  refresh()
  ElMessage.success('用户角色已更新')
}
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  margin-bottom: 2px;
}

.stat-card {
  margin-bottom: 12px;
}

.stat-label {
  color: #606266;
  font-size: 13px;
}

.stat-value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 700;
  color: #1d4f91;
}

.mb-16 {
  margin-bottom: 16px;
}

.header-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
