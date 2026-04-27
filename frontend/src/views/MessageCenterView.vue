<template>
  <div class="page-wrap">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>消息通知中心</span>
          <div class="toolbar-right">
            <el-select v-model="filter" style="width: 140px">
              <el-option label="全部" value="ALL" />
              <el-option label="未读" value="UNREAD" />
              <el-option label="已读" value="READ" />
            </el-select>
            <el-button @click="refreshSnapshot">刷新</el-button>
          </div>
        </div>
      </template>

      <AppDataTable :data="rows" layout-storage-key="app:table-layout:message:center" :show-pagination="false" :with-card="false">
        <template #default>
        <el-table-column label="状态" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.isRead ? 'info' : 'danger'">{{ scope.row.isRead ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="fromUser" label="发送人" width="120" />
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="交互" width="280">
          <template #default="scope">
            <el-button link type="primary" @click="markRead(scope.row)">标记已读</el-button>
            <el-button link type="success" @click="ack(scope.row)">确认收到</el-button>
            <el-button link @click="openReply(scope.row)">回复</el-button>
            <el-button link @click="openHistory(scope.row)">互动记录</el-button>
          </template>
        </el-table-column>
        </template>
      </AppDataTable>
    </el-card>

    <el-dialog v-model="replyDialogVisible" title="回复通知" width="520px">
      <el-form label-width="80px">
        <el-form-item label="回复内容">
          <el-input v-model="replyText" type="textarea" :rows="4" placeholder="请输入回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyDrawerVisible" title="通知互动记录" size="40%">
      <el-empty v-if="!activeInteractions.length" description="暂无互动记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="item in activeInteractions"
          :key="item.id"
          :timestamp="item.createdAt"
          :type="item.action === 'ACK' ? 'success' : 'primary'"
        >
          <div class="history-title">{{ item.actor }} - {{ item.action === 'ACK' ? '确认收到' : '回复' }}</div>
          <div class="history-text">{{ item.message || '无附加说明' }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { interactNotification, markNotificationRead } from '../utils/rectificationStore'
import { useRectificationSnapshot } from '../composables/useRectificationSnapshot'
import { getCurrentUser } from '../utils/currentUser'
import AppDataTable from '../components/shared/AppDataTable.vue'

const { snapshot, refreshSnapshot } = useRectificationSnapshot()
const user = getCurrentUser()
const username = user.username || 'guest'

const filter = ref('UNREAD')
const replyDialogVisible = ref(false)
const historyDrawerVisible = ref(false)
const activeNotificationId = ref('')
const activeInteractions = ref([])
const replyText = ref('')

const rows = computed(() => {
  const notifications = (Array.isArray(snapshot.value.notifications) ? snapshot.value.notifications : []).map((item) => ({
    ...item,
    isRead: Array.isArray(item.readBy) ? item.readBy.includes(username) : false
  }))

  if (filter.value === 'UNREAD') {
    return notifications.filter((item) => !item.isRead)
  }
  if (filter.value === 'READ') {
    return notifications.filter((item) => item.isRead)
  }
  return notifications
})

async function markRead(row) {
  try {
    await markNotificationRead(row.id)
    await refreshSnapshot()
    ElMessage.success('已标记为已读')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '标记失败')
  }
}

async function ack(row) {
  try {
    await interactNotification(row.id, {
      action: 'ACK',
      actor: username,
      message: '已确认接收并开始处理'
    })
    await refreshSnapshot()
    ElMessage.success('已确认通知')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '确认失败')
  }
}

function openReply(row) {
  activeNotificationId.value = row.id
  replyText.value = ''
  replyDialogVisible.value = true
}

async function submitReply() {
  if (!activeNotificationId.value) return
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  try {
    await interactNotification(activeNotificationId.value, {
      action: 'REPLY',
      actor: username,
      message: replyText.value.trim()
    })
    replyDialogVisible.value = false
    await refreshSnapshot()
    ElMessage.success('回复已发送')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '回复失败')
  }
}

function openHistory(row) {
  activeInteractions.value = Array.isArray(row.interactions) ? row.interactions : []
  historyDrawerVisible.value = true
}
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

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.history-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.history-text {
  color: #606266;
}
</style>
