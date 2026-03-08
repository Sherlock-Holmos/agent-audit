<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    title="新增数据源"
    destroy-on-close
    @close="handleClose"
  >
    <el-radio-group v-model="sourceType" style="margin-bottom: 16px">
      <el-radio-button label="DATABASE" value="DATABASE">数据库</el-radio-button>
      <el-radio-button label="FILE" value="FILE">本地文件</el-radio-button>
    </el-radio-group>

    <el-form v-if="sourceType === 'DATABASE'" ref="dbFormRef" :model="dbForm" :rules="dbRules" label-width="110px">
      <el-form-item label="数据源名称" prop="name">
        <el-input v-model="dbForm.name" placeholder="例如：审计生产库" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="数据库类型" prop="dbType">
            <el-select v-model="dbForm.dbType" placeholder="请选择数据库类型">
              <el-option label="MySQL" value="MYSQL" />
              <el-option label="PostgreSQL" value="POSTGRESQL" />
              <el-option label="Oracle" value="ORACLE" />
              <el-option label="SQL Server" value="SQLSERVER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="dbForm.host" placeholder="127.0.0.1" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="dbForm.port" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="数据库名" prop="databaseName">
            <el-input v-model="dbForm.databaseName" placeholder="audit_db" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="dbForm.username" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="密码" prop="password">
            <el-input v-model="dbForm.password" type="password" show-password />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="dbForm.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
      </el-form-item>
    </el-form>

    <el-form v-else ref="fileFormRef" :model="fileForm" :rules="fileRules" label-width="110px">
      <el-form-item label="数据源名称" prop="name">
        <el-input v-model="fileForm.name" placeholder="例如：问题台账导入文件" />
      </el-form-item>
      <el-form-item label="选择文件" prop="file">
        <el-upload
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          :on-change="onFileChange"
          :on-remove="onFileRemove"
          accept=".csv,.xls,.xlsx,.json,.txt"
        >
          <el-button>选择文件</el-button>
          <template #tip>
            <div style="margin-top: 6px; color: #909399">支持 .csv/.xls/.xlsx/.json/.txt，单文件最大 20MB</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="fileForm.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
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
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const sourceType = ref('DATABASE')
const dbFormRef = ref()
const fileFormRef = ref()
const selectedFile = ref(null)

const dbForm = reactive({
  name: '',
  dbType: 'MYSQL',
  host: '127.0.0.1',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  remark: ''
})

const fileForm = reactive({
  name: '',
  remark: ''
})

const dbRules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'change' }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const fileRules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  file: [{ validator: validateFile, trigger: 'change' }]
}

function validateFile(_rule, _value, callback) {
  if (!selectedFile.value) {
    callback(new Error('请选择要导入的本地文件'))
    return
  }
  callback()
}

function onFileChange(uploadFile) {
  selectedFile.value = uploadFile.raw || null
}

function onFileRemove() {
  selectedFile.value = null
}

function resetForms() {
  sourceType.value = 'DATABASE'
  selectedFile.value = null
  Object.assign(dbForm, {
    name: '',
    dbType: 'MYSQL',
    host: '127.0.0.1',
    port: 3306,
    databaseName: '',
    username: '',
    password: '',
    remark: ''
  })
  Object.assign(fileForm, {
    name: '',
    remark: ''
  })
  dbFormRef.value?.clearValidate()
  fileFormRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      resetForms()
    }
  }
)

function handleClose() {
  emit('update:modelValue', false)
}

async function submit() {
  if (sourceType.value === 'DATABASE') {
    const valid = await dbFormRef.value?.validate().catch(() => false)
    if (!valid) return
    emit('submit', {
      type: 'DATABASE',
      payload: { ...dbForm }
    })
    return
  }

  const valid = await fileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  emit('submit', {
    type: 'FILE',
    payload: {
      ...fileForm,
      file: selectedFile.value
    }
  })
}
</script>
