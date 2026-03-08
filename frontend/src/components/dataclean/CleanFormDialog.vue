<template>
  <el-dialog
    :model-value="modelValue"
    width="680px"
    title="新建数据清洗任务"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="form.taskName" placeholder="例如：问题台账去重清洗" />
      </el-form-item>
      <el-form-item label="清洗对象" prop="objectKeys">
        <el-select v-model="form.objectKeys" multiple placeholder="请选择数据库表或文件" style="width: 100%">
          <el-option
            v-for="obj in objectOptions"
            :key="obj.key"
            :label="obj.label"
            :value="obj.key"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="清洗策略" prop="strategy">
        <el-select v-model="form.strategy" placeholder="请选择清洗策略" style="width: 100%">
          <el-option
            v-for="strategy in strategyOptions"
            :key="strategy.code"
            :label="strategy.name"
            :value="strategy.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="应用规则">
        <el-select v-model="form.ruleIds" multiple clearable placeholder="可选：选择启用中的清洗规则" style="width: 100%">
          <el-option
            v-for="rule in ruleOptions"
            :key="rule.id"
            :label="rule.name"
            :value="rule.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标准化表名">
        <el-input v-model="form.standardTable" placeholder="例如：clean_std_issue_union（留空自动生成）" />
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
  objectOptions: {
    type: Array,
    default: () => []
  },
  ruleOptions: {
    type: Array,
    default: () => []
  },
  strategyOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])
const formRef = ref()

const form = reactive({
  taskName: '',
  objectKeys: [],
  strategy: '',
  ruleIds: [],
  standardTable: '',
  remark: ''
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  objectKeys: [{ required: true, message: '请选择清洗对象', trigger: 'change' }],
  strategy: [{ required: true, message: '请选择清洗策略', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, {
    taskName: '',
    objectKeys: [],
    strategy: props.strategyOptions[0]?.code || '',
    ruleIds: [],
    standardTable: '',
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

watch(
  () => props.strategyOptions,
  (list) => {
    if (!form.strategy && Array.isArray(list) && list.length) {
      form.strategy = list[0].code
    }
  },
  { immediate: true }
)

function handleClose() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const cleanObjects = form.objectKeys
    .map((key) => props.objectOptions.find((item) => item.key === key))
    .filter(Boolean)
    .map((item) => ({
      sourceId: item.sourceId,
      sourceName: item.sourceName,
      sourceType: item.sourceType,
      objectType: item.objectType,
      objectName: item.objectName
    }))

  emit('submit', {
    taskName: form.taskName,
    strategy: form.strategy,
    cleanRuleIds: [...form.ruleIds],
    cleanRuleNames: props.ruleOptions.filter((item) => form.ruleIds.includes(item.id)).map((item) => item.name),
    standardTable: form.standardTable,
    remark: form.remark,
    cleanObjects
  })
}
</script>
