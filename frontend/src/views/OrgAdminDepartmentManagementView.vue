<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="page-title">部门与成员管理</div>
        <div class="page-subtitle">统一维护本单位部门结构、负责人和成员账号</div>
      </div>
    </div>

    <div class="stack-layout">
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-header">
            <span>部门管理</span>
            <el-button type="primary" link @click="openDepartmentDialog()">新增部门</el-button>
          </div>
        </template>

        <AppDataTable
          :data="departments"
          layout-storage-key="app:table-layout:org-admin:department"
          :show-pagination="false"
          :with-card="false"
          height="520"
        >
          <template #default>
          <el-table-column prop="name" label="部门名称" min-width="140" />
          <el-table-column prop="parentName" label="上级部门" min-width="120" />
          <el-table-column prop="leaderUsername" label="负责人账号" min-width="120" />
          <el-table-column label="操作" width="160">
            <template #default="scope">
              <el-button link type="primary" @click="openDepartmentDialog(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeDepartment(scope.row)">删除</el-button>
            </template>
          </el-table-column>
          </template>
        </AppDataTable>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-header">
            <span>成员管理</span>
            <div class="header-actions">
              <el-select v-model="memberDepartmentFilter" placeholder="按部门筛选" clearable style="width: 180px">
                <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.name" />
              </el-select>
              <el-button type="primary" @click="openMemberDialog()">新增成员</el-button>
            </div>
          </div>
        </template>

        <AppDataTable
          :data="members"
          layout-storage-key="app:table-layout:org-admin:member"
          :show-pagination="false"
          :with-card="false"
          height="520"
        >
          <template #default>
          <el-table-column prop="username" label="账号" min-width="120" />
          <el-table-column prop="nickname" label="姓名" min-width="120" />
          <el-table-column prop="department" label="部门" min-width="120" />
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="180">
            <template #default="scope">
              <el-button link type="primary" @click="openMemberDialog(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeMember(scope.row)">删除</el-button>
            </template>
          </el-table-column>
          </template>
        </AppDataTable>
      </el-card>
    </div>

    <el-dialog v-model="departmentDialogVisible" :title="departmentForm.id ? '编辑部门' : '新增部门'" width="520px">
      <el-form :model="departmentForm" label-width="96px">
        <el-form-item label="部门名称">
          <el-input v-model="departmentForm.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="departmentForm.parentId" placeholder="可选" clearable style="width: 100%">
            <el-option
              v-for="dept in parentDepartmentOptions"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人账号">
          <el-input v-model="departmentForm.leaderUsername" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="departmentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDepartment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" :title="memberForm.id ? '编辑成员' : '新增成员'" width="560px">
      <el-form :model="memberForm" label-width="96px">
        <el-form-item label="账号">
          <el-input v-model="memberForm.username" :disabled="Boolean(memberForm.id)" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="memberForm.nickname" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="memberForm.department" placeholder="请选择部门" style="width: 100%">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" style="width: 100%">
            <el-option label="单位管理员" value="ORG_ADMIN" />
            <el-option label="经办人" value="ORG_OPERATOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="memberForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMember">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createOrgDepartment,
  createOrgMember,
  deleteOrgDepartment,
  deleteOrgMember,
  listOrgDepartments,
  listOrgMembers,
  updateOrgDepartment,
  updateOrgMember
} from '../utils/rectificationStore'
import AppDataTable from '../components/shared/AppDataTable.vue'

const departments = ref([])
const members = ref([])
const memberDepartmentFilter = ref('')

const departmentDialogVisible = ref(false)
const memberDialogVisible = ref(false)

const departmentForm = reactive({
  id: null,
  name: '',
  parentId: null,
  leaderUsername: ''
})

const memberForm = reactive({
  id: null,
  username: '',
  nickname: '',
  department: '',
  role: 'ORG_OPERATOR',
  status: 'ENABLED'
})

const parentDepartmentOptions = computed(() =>
  departments.value.filter((dept) => dept.id !== departmentForm.id)
)

watch(memberDepartmentFilter, () => {
  loadMembers()
})

async function loadDepartments() {
  const rows = await listOrgDepartments()
  const nameMap = new Map(rows.map((item) => [item.id, item.name]))
  departments.value = rows.map((item) => ({
    ...item,
    parentName: item.parentId ? (nameMap.get(item.parentId) || '') : ''
  }))
}

async function loadMembers() {
  members.value = await listOrgMembers(memberDepartmentFilter.value || '')
}

function openDepartmentDialog(row = null) {
  if (!row) {
    Object.assign(departmentForm, {
      id: null,
      name: '',
      parentId: null,
      leaderUsername: ''
    })
  } else {
    Object.assign(departmentForm, {
      id: row.id,
      name: row.name,
      parentId: row.parentId || null,
      leaderUsername: row.leaderUsername || ''
    })
  }
  departmentDialogVisible.value = true
}

async function saveDepartment() {
  if (!departmentForm.name.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }

  const payload = {
    name: departmentForm.name.trim(),
    parentId: departmentForm.parentId,
    leaderUsername: departmentForm.leaderUsername.trim()
  }

  try {
    if (departmentForm.id) {
      await updateOrgDepartment(departmentForm.id, payload)
      ElMessage.success('部门更新成功')
    } else {
      await createOrgDepartment(payload)
      ElMessage.success('部门创建成功')
    }
    departmentDialogVisible.value = false
    await Promise.all([loadDepartments(), loadMembers()])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '部门保存失败')
  }
}

async function removeDepartment(row) {
  try {
    await ElMessageBox.confirm(`确认删除部门“${row.name}”吗？`, '提示', { type: 'warning' })
    await deleteOrgDepartment(row.id)
    ElMessage.success('部门删除成功')
    await Promise.all([loadDepartments(), loadMembers()])
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '部门删除失败')
    }
  }
}

function openMemberDialog(row = null) {
  if (!row) {
    Object.assign(memberForm, {
      id: null,
      username: '',
      nickname: '',
      department: memberDepartmentFilter.value || '',
      role: 'ORG_OPERATOR',
      status: 'ENABLED'
    })
  } else {
    Object.assign(memberForm, {
      id: row.id,
      username: row.username,
      nickname: row.nickname,
      department: row.department,
      role: row.role,
      status: row.status
    })
  }
  memberDialogVisible.value = true
}

async function saveMember() {
  if (!memberForm.username.trim()) {
    ElMessage.warning('请输入账号')
    return
  }
  if (!memberForm.department) {
    ElMessage.warning('请选择部门')
    return
  }

  const payload = {
    username: memberForm.username.trim(),
    nickname: memberForm.nickname.trim() || memberForm.username.trim(),
    department: memberForm.department,
    role: memberForm.role,
    status: memberForm.status
  }

  try {
    if (memberForm.id) {
      await updateOrgMember(memberForm.id, payload)
      ElMessage.success('成员更新成功')
    } else {
      await createOrgMember(payload)
      ElMessage.success('成员创建成功')
    }
    memberDialogVisible.value = false
    await loadMembers()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '成员保存失败')
  }
}

async function removeMember(row) {
  try {
    await ElMessageBox.confirm(`确认删除成员账号“${row.username}”吗？`, '提示', { type: 'warning' })
    await deleteOrgMember(row.id)
    ElMessage.success('成员删除成功')
    await loadMembers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '成员删除失败')
    }
  }
}

async function init() {
  try {
    await Promise.all([loadDepartments(), loadMembers()])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '加载数据失败')
  }
}

init()
</script>

<style scoped>
.page-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.section-card {
  min-height: 0;
}

.stack-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
