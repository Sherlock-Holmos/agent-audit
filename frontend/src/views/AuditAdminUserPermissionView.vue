<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <div class="toolbar-title">
            <el-button v-if="viewMode === 'deleted'" link type="primary" class="back-button" @click="showActiveUsers">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>{{ viewMode === 'deleted' ? '已删除用户' : '用户与权限管理' }}</span>
          </div>
          <div class="toolbar-right">
            <template v-if="viewMode === 'active'">
              <el-input v-model="keyword" placeholder="按用户名、单位或部门筛选" clearable style="width: 260px" />
              <el-button type="primary" @click="openCreateDialog">创建用户</el-button>
              <el-button @click="openDepartmentListDialog">单位列表</el-button>
              <el-button link type="warning" class="bin-button" @click="showDeletedUsers">
                <el-icon><DeleteFilled /></el-icon>
              </el-button>
            </template>
            <template v-else>
              <el-button @click="loadDeletedUsers">刷新回收站</el-button>
            </template>
          </div>
        </div>
      </template>
      <AppDataTable
        v-if="viewMode === 'active'"
        :data="filteredUsers"
        layout-storage-key="app:table-layout:audit-admin:user-permission-active"
        :show-pagination="false"
        :with-card="false"
      >
        <template #default>
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickname" label="姓名" width="140" />
        <el-table-column prop="unit" label="单位" min-width="150" />
        <el-table-column prop="department" label="部门" min-width="150" />
        <el-table-column label="角色" width="220">
          <template #default="scope">
            <el-select :model-value="scope.row.role" @change="(val) => changeRole(scope.row.id, val)">
              <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'danger'">
              {{ scope.row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="openBindDepartmentDialog(scope.row)">绑定单位</el-button>
            <el-button
              link
              :type="scope.row.status === 'ENABLED' ? 'danger' : 'success'"
              @click="toggleStatus(scope.row)"
            >
              {{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="removeUser(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>

      <AppDataTable
        v-else
        :data="deletedUsers"
        layout-storage-key="app:table-layout:audit-admin:user-permission-deleted"
        :show-pagination="false"
        :with-card="false"
      >
        <template #default>
        <el-table-column prop="username" label="用户名" width="180" />
        <el-table-column prop="nickname" label="姓名" width="160" />
        <el-table-column prop="unit" label="单位" min-width="160" />
        <el-table-column prop="department" label="部门" min-width="160" />
        <el-table-column prop="updatedAt" label="删除时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="success" @click="recoverUser(scope.row)">恢复</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-dialog v-model="userDialogVisible" :title="editingUserId ? '编辑用户' : '创建用户'" width="560px">
      <el-form :model="userForm" label-width="95px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="Boolean(editingUserId)" placeholder="唯一账号名" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="userForm.unit" filterable placeholder="请选择单位" style="width: 100%">
            <el-option v-for="item in bindableDepartmentOptions" :key="item.id" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.department" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="userForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="departmentDialogVisible" title="绑定单位" width="460px">
      <el-form :model="departmentForm" label-width="95px">
        <el-form-item label="用户名">
          <el-input v-model="departmentForm.username" disabled />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="departmentForm.department" filterable placeholder="请选择单位" style="width: 100%">
            <el-option v-for="item in bindableDepartmentOptions" :key="item.id" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="departmentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDepartmentBinding">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="departmentListDialogVisible" title="单位列表维护" width="640px">
      <div class="department-toolbar">
        <el-input v-model="departmentNameInput" placeholder="请输入单位名称" clearable style="flex: 1" />
        <el-button type="primary" @click="saveDepartmentRecord">
          {{ editingDepartmentId ? '保存修改' : '新增单位' }}
        </el-button>
        <el-button v-if="editingDepartmentId" @click="resetDepartmentEditor">取消编辑</el-button>
      </div>

      <AppDataTable
        :data="departmentOptions"
        layout-storage-key="app:table-layout:audit-admin:user-permission-department"
        :show-pagination="false"
        :with-card="false"
        table-class="mt-12"
      >
        <template #default>
        <el-table-column prop="name" label="单位名称" min-width="220" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button link type="primary" @click="editDepartmentRecord(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeDepartmentRecord(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, DeleteFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ROLE_OPTIONS } from '../constants/rbac'
import {
  bindUserDepartment,
  createDepartment,
  createUser,
  deleteDepartment,
  deleteUser,
  listDepartments,
  listDeletedUsers,
  restoreUser,
  updateDepartment,
  updateUserProfile,
  updateUserRole,
  updateUserStatus
} from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import AppDataTable from '../components/shared/AppDataTable.vue'

const roleOptions = ROLE_OPTIONS
const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const viewMode = ref('active')
const keyword = ref('')
const userDialogVisible = ref(false)
const editingUserId = ref('')
const departmentDialogVisible = ref(false)
const departmentBindingUserId = ref('')
const deletedUsers = ref([])
const departmentListDialogVisible = ref(false)
const editingDepartmentId = ref('')
const departmentNameInput = ref('')
const departmentOptions = ref([])
const bindableDepartmentOptions = computed(() => departmentOptions.value.filter((item) => item.name !== '未分配部门'))

const userForm = reactive({
  username: '',
  nickname: '',
  unit: '',
  department: '',
  role: roleOptions[0].value,
  status: 'ENABLED'
})

const departmentForm = reactive({
  username: '',
  department: ''
})

const filteredUsers = computed(() => {
  const text = keyword.value.trim()
  const users = snapshot.value.users
  if (!text) return users
  return users.filter((item) =>
    String(item.username).includes(text) ||
    String(item.unit || '').includes(text) ||
    String(item.department || '').includes(text)
  )
})

function showDeletedUsers() {
  viewMode.value = 'deleted'
  loadDeletedUsers()
}

function showActiveUsers() {
  viewMode.value = 'active'
}

async function changeRole(userId, role) {
  try {
    await updateUserRole(userId, role)
    await refreshSnapshot()
    ElMessage.success('用户角色已更新')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '角色更新失败')
  }
}

async function toggleStatus(user) {
  const status = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await updateUserStatus(user.id, status)
    await refreshSnapshot()
    ElMessage.success(`用户已${status === 'ENABLED' ? '启用' : '停用'}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '状态更新失败')
  }
}

function openCreateDialog() {
  if (!departmentOptions.value.length) {
    ElMessage.warning('请先维护单位列表')
    openDepartmentListDialog()
    return
  }
  editingUserId.value = ''
  userForm.username = ''
  userForm.nickname = ''
  userForm.unit = bindableDepartmentOptions.value[0]?.name || ''
  userForm.department = ''
  userForm.role = roleOptions[0].value
  userForm.status = 'ENABLED'
  userDialogVisible.value = true
}

function openEditDialog(user) {
  editingUserId.value = user.id
  userForm.username = user.username
  userForm.nickname = user.nickname || ''
  userForm.unit = user.unit || ''
  userForm.department = user.department || ''
  userForm.role = user.role
  userForm.status = user.status
  userDialogVisible.value = true
}

async function saveUser() {
  if (!userForm.username.trim() || !userForm.nickname.trim() || !userForm.unit.trim()) {
    ElMessage.warning('用户名、姓名、单位不能为空')
    return
  }

  try {
    if (editingUserId.value) {
      await updateUserProfile(editingUserId.value, { ...userForm })
      ElMessage.success('用户信息已更新')
    } else {
      await createUser({ ...userForm })
      ElMessage.success('用户创建成功')
    }
    await refreshSnapshot()
    userDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '保存失败，请重试')
  }
}

function openBindDepartmentDialog(user) {
  if (!departmentOptions.value.length) {
    ElMessage.warning('请先维护单位列表')
    openDepartmentListDialog()
    return
  }
  departmentBindingUserId.value = user.id
  departmentForm.username = user.username
  departmentForm.department = bindableDepartmentOptions.value[0]?.name || ''
  departmentDialogVisible.value = true
}

async function saveDepartmentBinding() {
  const department = departmentForm.department.trim()
  if (!department) {
    ElMessage.warning('单位不能为空')
    return
  }

  try {
    await bindUserDepartment(departmentBindingUserId.value, department)
    await refreshSnapshot()
    departmentDialogVisible.value = false
    ElMessage.success('单位绑定成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '单位绑定失败')
  }
}

async function removeUser(user) {
  try {
    await ElMessageBox.confirm(`确认删除用户 ${user.username} 吗？删除后将从管理列表移除。`, '删除用户', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteUser(user.id)
    await refreshSnapshot()
    await loadDeletedUsers()
    viewMode.value = 'deleted'
    ElMessage.success('用户已删除')
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message || '删除失败')
  }
}

async function loadDeletedUsers() {
  try {
    deletedUsers.value = await listDeletedUsers()
  } catch {
    deletedUsers.value = []
  }
}

async function loadDepartmentOptions() {
  try {
    departmentOptions.value = await listDepartments()
  } catch {
    departmentOptions.value = []
  }
}

function openDepartmentListDialog() {
  resetDepartmentEditor()
  departmentListDialogVisible.value = true
  loadDepartmentOptions()
}

function editDepartmentRecord(row) {
  editingDepartmentId.value = row.id
  departmentNameInput.value = row.name || ''
}

function resetDepartmentEditor() {
  editingDepartmentId.value = ''
  departmentNameInput.value = ''
}

async function saveDepartmentRecord() {
  const name = departmentNameInput.value.trim()
  if (!name) {
    ElMessage.warning('单位名称不能为空')
    return
  }

  try {
    if (editingDepartmentId.value) {
      await updateDepartment(editingDepartmentId.value, name)
      ElMessage.success('单位已更新')
    } else {
      await createDepartment(name)
      ElMessage.success('单位已创建')
    }
    resetDepartmentEditor()
    await Promise.all([loadDepartmentOptions(), refreshSnapshot()])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '单位保存失败')
  }
}

async function removeDepartmentRecord(row) {
  try {
    await ElMessageBox.confirm(`确认删除单位 ${row.name} 吗？`, '删除单位', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteDepartment(row.id)
    ElMessage.success('单位已删除')
    await Promise.all([loadDepartmentOptions(), refreshSnapshot()])
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message || '单位删除失败')
  }
}

async function recoverUser(user) {
  try {
    await ElMessageBox.confirm(`确认恢复用户 ${user.username} 吗？恢复后状态为停用。`, '恢复用户', {
      type: 'info',
      confirmButtonText: '恢复',
      cancelButtonText: '取消'
    })
    await restoreUser(user.id)
    await refreshSnapshot()
    await loadDeletedUsers()
    if (viewMode.value === 'deleted') {
      viewMode.value = 'active'
    }
    ElMessage.success('用户已恢复（当前为停用状态）')
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message || '恢复失败')
  }
}

onMounted(() => {
  loadDeletedUsers()
  loadDepartmentOptions()
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

.toolbar-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bin-button {
  font-size: 18px;
  padding: 0 6px;
}

.back-button {
  padding: 0;
}

.department-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mt-12 {
  margin-top: 12px;
}
</style>
