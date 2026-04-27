<template>
  <div v-if="task" class="progress-detail-panel">
    <el-descriptions v-if="showDescriptions" :column="2" border class="panel-descriptions">
      <el-descriptions-item label="任务标题">{{ task.title || '-' }}</el-descriptions-item>
      <el-descriptions-item label="任务层级">{{ task.parentId ? '子任务' : '主任务' }}</el-descriptions-item>
      <el-descriptions-item label="责任单位">{{ task.unit || '无' }}</el-descriptions-item>
      <el-descriptions-item label="责任人">{{ task.assignee || '未分配' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ task.status || '无' }}</el-descriptions-item>
      <el-descriptions-item label="审核状态">{{ task.reviewStatus || '无' }}</el-descriptions-item>
      <el-descriptions-item label="进度">{{ Number(task.progress || 0) }}%</el-descriptions-item>
      <el-descriptions-item label="截止日期">{{ task.deadline || '无' }}</el-descriptions-item>
      <el-descriptions-item label="整改措施" :span="2">{{ task.measure || '无' }}</el-descriptions-item>
      <el-descriptions-item label="执行反馈" :span="2">{{ task.feedback || '无' }}</el-descriptions-item>
      <el-descriptions-item label="证明材料" :span="2">
        <div v-if="attachmentItems.length" class="tag-wrap">
          <el-link
            v-for="item in attachmentItems"
            :key="`${item.label}-${item.index}`"
            class="attachment-link"
            type="primary"
            :underline="false"
            @click.prevent="handleAttachmentClick(item)"
          >
            {{ item.label }}
          </el-link>
        </div>
        <span v-else>无</span>
      </el-descriptions-item>
    </el-descriptions>

    <template v-if="showTimeline">
      <el-divider content-position="left">流转轨迹</el-divider>
      <el-timeline v-if="timeline.length">
        <el-timeline-item
          v-for="item in timeline"
          :key="`${item.label}-${item.time}-${item.text}`"
          :timestamp="item.time"
          :type="item.type || 'primary'"
        >
          <div class="timeline-title">{{ item.label }}</div>
          <div class="timeline-text">{{ item.text }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无流转记录" />
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadTaskAttachment } from '../../utils/rectificationStore'

const props = defineProps({
  task: {
    type: Object,
    default: null
  },
  timeline: {
    type: Array,
    default: () => []
  },
  showTimeline: {
    type: Boolean,
    default: true
  },
  showDescriptions: {
    type: Boolean,
    default: true
  }
})

const attachmentItems = computed(() => {
  const list = Array.isArray(props.task?.attachments) ? props.task.attachments : []
  return list
    .map((item, index) => {
      if (typeof item === 'string') {
        const label = String(item || '').trim()
        return label ? { index: index + 1, label } : null
      }
      if (!item || typeof item !== 'object') return null
      const label = String(item.fileName || item.originalName || item.name || item.storedName || '').trim()
      return label ? { index: index + 1, label } : null
    })
    .filter(Boolean)
})

async function handleAttachmentClick(item) {
  if (!props.task?.id) {
    ElMessage.warning('附件信息不完整')
    return
  }
  try {
    const blob = await downloadTaskAttachment(props.task.id, item.index)
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = item.label || `附件${item.index}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => window.URL.revokeObjectURL(url), 0)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '附件下载失败')
  }
}
</script>

<style scoped>
.panel-descriptions {
  margin-bottom: 16px;
}

.timeline-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.timeline-text {
  color: #606266;
  white-space: pre-wrap;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-link {
  margin-right: 8px;
  margin-bottom: 4px;
}
</style>
