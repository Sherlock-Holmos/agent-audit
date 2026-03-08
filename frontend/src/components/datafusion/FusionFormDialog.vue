<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    title="新建数据融合任务"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="form.taskName" placeholder="例如：台账与整改系统融合" />
      </el-form-item>
      <el-form-item label="目标整合表" prop="targetTable">
        <el-input v-model="form.targetTable" placeholder="例如：fusion_result_table" />
      </el-form-item>
      <el-form-item label="清洗任务" prop="cleanTaskIds">
        <el-select v-model="form.cleanTaskIds" multiple placeholder="请选择已完成清洗任务" style="width: 100%">
          <el-option
            v-for="task in cleanTaskOptions"
            :key="task.id"
            :label="`${task.taskName}（${task.standardTable || '-'}）`"
            :value="String(task.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="融合策略" prop="strategy">
        <el-select v-model="form.strategy" placeholder="请选择融合策略" style="width: 100%">
          <el-option label="主键对齐融合" value="KEY_ALIGN" />
          <el-option label="时间窗口融合" value="TIME_WINDOW" />
          <el-option label="规则匹配融合" value="RULE_MATCH" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  submitting: {
    type: Boolean,
    default: false
  },
  cleanTaskOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])
const formRef = ref()

const form = reactive({
  taskName: '',
  targetTable: '',
  cleanTaskIds: [],
  strategy: 'KEY_ALIGN',
  remark: ''
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  targetTable: [{ required: true, message: '请输入目标整合表', trigger: 'blur' }],
  cleanTaskIds: [{ required: true, message: '请选择清洗任务', trigger: 'change' }],
  strategy: [{ required: true, message: '请选择融合策略', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, {
    taskName: '',
    targetTable: '',
    cleanTaskIds: [],
    strategy: 'KEY_ALIGN',
    remark: ''
  })
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      resetForm()
    }
  }
)

function handleClose() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    ...form,
    cleanTaskIds: [...form.cleanTaskIds]
  })
}
</script>
