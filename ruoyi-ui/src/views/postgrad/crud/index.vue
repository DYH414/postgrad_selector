<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="84px">
      <el-form-item v-for="field in queryFields" :key="field.prop" :label="field.label" :prop="field.prop">
        <component
          :is="queryComponent(field)"
          v-model="queryParams[field.prop]"
          v-bind="inputAttrs(field, true)"
          clearable
          filterable
          @keyup.enter.native="handleQuery"
          @change="handleQueryFieldChange(field)"
        >
          <el-option
            v-for="item in selectOptions(field, queryParams)"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </component>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="[permission('add')]">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate" v-hasPermi="[permission('edit')]">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="[permission('remove')]">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="[permission('export')]">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="rows" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column
        v-for="field in tableFields"
        :key="field.prop"
        :label="field.label"
        :prop="field.prop"
        :width="field.width"
        :min-width="field.minWidth"
        align="center"
        :show-overflow-tooltip="field.showOverflow"
      >
        <template slot-scope="scope">
          <el-tag v-if="field.type === 'switch' && Number(scope.row[field.prop]) === 1" size="mini" type="success">是</el-tag>
          <span v-else-if="field.type === 'switch'">否</span>
          <div v-else-if="field.subjectSummary" class="subject-summary">
            <el-tag
              v-for="subject in splitSubjects(scope.row[field.prop])"
              :key="subject"
              :type="subject.indexOf('408') !== -1 ? 'success' : ''"
              size="mini"
              effect="plain"
            >{{ subject }}</el-tag>
          </div>
          <el-button
            v-else-if="isLongText(field, scope.row[field.prop])"
            type="text"
            size="mini"
            @click="openTextPreview(field, scope.row[field.prop])"
          >查看</el-button>
          <span v-else-if="field.optionsKey || field.optionModule">{{ optionLabel(field, scope.row[field.prop]) }}</span>
          <span v-else>{{ scope.row[field.prop] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="[permission('edit')]">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="[permission('remove')]">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="760px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <div v-for="group in formGroups" :key="group.name" class="crud-form-group">
          <div class="crud-form-group__title">{{ group.name }}</div>
          <el-row :gutter="16">
            <el-col v-for="field in group.fields" :key="field.prop" :span="fieldSpan(field)">
              <el-form-item :label="field.label" :prop="field.prop">
                <el-input
                  v-if="field.type === 'text'"
                  v-model="form[field.prop]"
                  :placeholder="'请输入' + field.label"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.longText ? 5 : 3"
                  :placeholder="'请输入' + field.label"
                />
                <el-input-number
                  v-else-if="field.type === 'number'"
                  v-model="form[field.prop]"
                  controls-position="right"
                  :min="0"
                  style="width: 100%"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="yyyy-MM-dd"
                  placeholder="请选择日期"
                  style="width: 100%"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="1"
                  :inactive-value="0"
                />
                <el-select
                  v-else-if="field.type === 'select' || field.type === 'remoteSelect'"
                  v-model="form[field.prop]"
                  :placeholder="'请选择' + field.label"
                  clearable
                  filterable
                  style="width: 100%"
                  @change="handleFieldChange(field)"
                >
                  <el-option
                    v-for="item in selectOptions(field, form)"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  >
                    <div class="crud-option">
                      <span>{{ item.label }}</span>
                      <small v-if="item.subLabel">{{ item.subLabel }}</small>
                    </div>
                  </el-option>
                </el-select>
                <el-input v-else v-model="form[field.prop]" disabled />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="textPreview.title" :visible.sync="textPreview.open" width="720px" append-to-body>
      <el-input type="textarea" :rows="14" readonly :value="textPreview.content" />
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="textPreview.open = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listCrud, getCrud, delCrud, addCrud, updateCrud, optionselectCrud } from '@/api/postgrad/crud'
import { getModuleConfig, getOptions } from './moduleConfig'

export default {
  name: 'PostgradCrud',
  data() {
    return {
      module: '',
      config: null,
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      rows: [],
      title: '',
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10
      },
      form: {},
      rules: {},
      remoteOptions: {},
      textPreview: {
        open: false,
        title: '',
        content: ''
      }
    }
  },
  computed: {
    tableFields() {
      return this.config ? this.config.columns.filter(field => !field.virtual && !field.queryOnly && (field.type !== 'remoteSelect' || field.prop.endsWith('Id'))) : []
    },
    formFields() {
      return this.config ? this.config.columns.filter(field => !field.readonly && !field.queryOnly) : []
    },
    formGroups() {
      const groups = []
      this.formFields.forEach(field => {
        const name = field.group || this.inferGroup(field)
        let group = groups.find(item => item.name === name)
        if (!group) {
          group = { name, fields: [] }
          groups.push(group)
        }
        group.fields.push(field)
      })
      return groups
    },
    queryFields() {
      if (!this.config) return []
      return this.config.query.map(prop => this.config.columns.find(field => field.prop === prop)).filter(Boolean)
    }
  },
  watch: {
    '$route.path': {
      handler() {
        this.initModule()
      },
      immediate: true
    }
  },
  methods: {
    initModule() {
      const module = this.$route.path.split('/').filter(Boolean).pop()
      const config = getModuleConfig(module)
      if (!config) {
        this.$modal.msgError('未找到业务模块配置：' + module)
        return
      }
      this.module = module
      this.config = config
      this.resetQueryModel()
      this.buildRules()
      this.loadRemoteOptions().then(() => this.getList())
    },
    permission(action) {
      return `postgrad:${this.module}:${action}`
    },
    resetQueryModel() {
      this.queryParams = {
        pageNum: 1,
        pageSize: 10
      }
      this.config.query.forEach(prop => {
        this.$set(this.queryParams, prop, undefined)
      })
    },
    buildRules() {
      const rules = {}
      this.config.columns.forEach(field => {
        if (field.required) {
          rules[field.prop] = [{ required: true, message: field.label + '不能为空', trigger: field.type === 'select' || field.type === 'remoteSelect' ? 'change' : 'blur' }]
        }
      })
      this.rules = rules
    },
    loadRemoteOptions() {
      const modules = [...new Set(this.config.columns.filter(field => field.optionModule).map(field => field.optionModule))]
      return Promise.all(modules.map(module => optionselectCrud(module).then(response => {
        this.$set(this.remoteOptions, module, (response.data || []).map(this.normalizeRemoteOption))
      })))
    },
    normalizeRemoteOption(item) {
      return {
        label: item.label || item.name || item.title || item.programName || item.code || item.id,
        value: item.id,
        schoolId: item.schoolId || item.schoolid || item.school_id,
        collegeId: item.collegeId || item.collegeid || item.college_id,
        subLabel: item.schoolName || item.collegeName || item.sourceOwner
      }
    },
    getList() {
      this.loading = true
      listCrud(this.module, this.queryParams).then(response => {
        this.rows = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    queryComponent(field) {
      return field.type === 'select' || field.type === 'remoteSelect' ? 'el-select' : 'el-input'
    },
    inputAttrs(field, query) {
      if (field.type === 'select' || field.type === 'remoteSelect') {
        return { placeholder: '请选择' + field.label }
      }
      return { placeholder: '请输入' + field.label }
    },
    selectOptions(field, model = this.form) {
      if (field.optionModule) {
        const options = this.remoteOptions[field.optionModule] || []
        return options.filter(option => this.matchDependency(field, option, model))
      }
      return getOptions(field.optionsKey)
    },
    matchDependency(field, option, model = this.form) {
      if (!field.dependsOn || !model[field.dependsOn]) return true
      const dependValue = String(model[field.dependsOn])
      if (field.dependsOn === 'schoolId') return String(option.schoolId) === dependValue
      if (field.dependsOn === 'collegeId') return String(option.collegeId) === dependValue
      return true
    },
    optionLabel(field, value) {
      const item = this.selectOptions(field, {}).find(option => String(option.value) === String(value))
      return item ? item.label : value
    },
    handleFieldChange(field) {
      this.formFields.forEach(item => {
        if (item.dependsOn === field.prop) {
          this.$set(this.form, item.prop, undefined)
        }
      })
    },
    handleQueryFieldChange(field) {
      this.queryFields.forEach(item => {
        if (item.dependsOn === field.prop) {
          this.$set(this.queryParams, item.prop, undefined)
        }
      })
      this.handleQuery()
    },
    fieldSpan(field) {
      if (field.type === 'textarea' || field.longText) return 24
      return 12
    },
    inferGroup(field) {
      if (field.type === 'textarea' || field.longText) return '补充信息'
      if (field.type === 'switch' || field.prop.toLowerCase().includes('status') || field.prop.toLowerCase().includes('risk')) return '状态与标记'
      if (field.prop.toLowerCase().includes('score') || field.prop.toLowerCase().includes('count') || field.prop.toLowerCase().includes('plan') || field.prop.toLowerCase().includes('quota')) return '指标数据'
      return '基础信息'
    },
    isLongText(field, value) {
      return value && (field.longText || field.type === 'textarea' || String(value).length > 36)
    },
    openTextPreview(field, value) {
      this.textPreview = {
        open: true,
        title: field.label,
        content: value || ''
      }
    },
    splitSubjects(value) {
      return String(value || '').split('/').map(item => item.trim()).filter(Boolean)
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      const form = {}
      this.config.columns.forEach(field => {
        if (field.readonly) return
        if (field.default !== undefined) {
          form[field.prop] = field.default
        } else if (field.type === 'switch') {
          form[field.prop] = 0
        } else {
          form[field.prop] = undefined
        }
      })
      form.__key = undefined
      this.form = form
      this.resetForm('form')
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item[this.config.idField] || item.__key)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '添加' + this.config.title
    },
    handleUpdate(row) {
      this.reset()
      const id = row[this.config.idField] || this.ids[0]
      getCrud(this.module, id).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改' + this.config.title
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        const request = payload.__key ? updateCrud(this.module, payload) : addCrud(this.module, payload)
        request.then(() => {
          this.$modal.msgSuccess(this.form.__key ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
          this.loadRemoteOptions()
        })
      })
    },
    buildPayload() {
      const payload = {}
      this.config.columns.forEach(field => {
        if (!field.virtual && !field.readonly && Object.prototype.hasOwnProperty.call(this.form, field.prop)) {
          payload[field.prop] = this.form[field.prop]
        }
      })
      if (this.form.__key) payload.__key = this.form.__key
      return payload
    },
    handleDelete(row) {
      const ids = row[this.config.idField] || row.__key || this.ids
      this.$modal.confirm('是否确认删除"' + ids + '"的数据项？').then(() => {
        return delCrud(this.module, ids)
      }).then(() => {
        this.getList()
        this.loadRemoteOptions()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    handleExport() {
      this.download(`postgrad/${this.module}/export`, {
        ...this.queryParams
      }, `${this.module}_${new Date().getTime()}.csv`)
    }
  }
}
</script>

<style scoped>
.crud-form-group {
  padding-bottom: 6px;
}

.crud-form-group + .crud-form-group {
  border-top: 1px solid #ebeef5;
  padding-top: 14px;
  margin-top: 4px;
}

.crud-form-group__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.crud-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.crud-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crud-option small {
  flex: none;
  color: #909399;
}

.subject-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
  line-height: 1.8;
}
</style>
