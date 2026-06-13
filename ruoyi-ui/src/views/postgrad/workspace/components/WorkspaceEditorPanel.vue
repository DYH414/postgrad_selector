<template>
  <section class="workspace-panel detail-panel workspace-editor-panel">
    <div class="panel-heading">
      <div>
        <h3>专业方向数据维护</h3>
        <span>{{ selectedProgram ? selectedProgram.programCode : '等待选择' }}</span>
      </div>
      <el-button size="mini" type="text" icon="el-icon-refresh" :disabled="!selectedProgram" @click="reloadAll">刷新</el-button>
    </div>

    <div v-if="selectedProgram" class="editor-body">
      <div class="editor-title">
        <div>
          <strong>{{ selectedProgram.programName }}</strong>
          <span>{{ selectedSchool ? selectedSchool.name : '-' }} / {{ selectedProgram.collegeName || '-' }}</span>
        </div>
        <div class="editor-tags">
          <el-tag size="mini">{{ selectedProgram.programCode }}</el-tag>
          <el-tag v-if="Number(selectedProgram.is408) === 1" size="mini" type="success">408</el-tag>
          <el-tag size="mini" :type="qualityType(selectedProgram.completenessLevel)">
            {{ selectedProgram.completenessLevel || '待体检' }}
          </el-tag>
        </div>
      </div>

      <div class="editor-yearbar">
        <div>
          <strong>维护年份</strong>
          <span>切换后同步刷新当前专业的年度数据</span>
        </div>
        <el-select :value="Number(year)" size="mini" class="editor-year-select" @change="handleYearChange">
          <el-option v-for="item in yearOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </div>

      <el-tabs v-model="activeTab" class="editor-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="overview-grid">
            <div>
              <span>当前年份</span>
              <strong>{{ year }}</strong>
            </div>
            <div>
              <span>复试线</span>
              <strong>{{ selectedProgram.scoreLine || '-' }}</strong>
            </div>
            <div>
              <span>招生计划</span>
              <strong>{{ selectedProgram.totalPlan || selectedProgram.unifiedExamQuota || '-' }}</strong>
            </div>
            <div>
              <span>拟录取最低</span>
              <strong>{{ selectedProgram.minAdmittedScore || '-' }}</strong>
            </div>
          </div>

          <div class="editor-section">
            <h4>近年复试线趋势与数据</h4>
            <el-table :data="programYears" size="mini" border>
              <el-table-column prop="year" label="年份" width="70" align="center" />
              <el-table-column prop="scoreLine" label="复试线" width="80" align="center" />
              <el-table-column prop="totalPlan" label="总计划" width="80" align="center" />
              <el-table-column prop="unifiedExamQuota" label="统考" width="80" align="center" />
              <el-table-column prop="minAdmittedScore" label="最低分" width="80" align="center" />
              <el-table-column prop="completenessLevel" label="完整度" width="80" align="center">
                <template slot-scope="scope">
                  <el-tag size="mini" :type="qualityType(scope.row.completenessLevel)">
                    {{ scope.row.completenessLevel || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="missingFields" label="缺失字段" min-width="140" show-overflow-tooltip />
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-for="item in editableModules"
          :key="item.module"
          :label="item.label"
          :name="item.module"
        >
          <div class="module-toolbar">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ recordStatus(item.module) }}</span>
            </div>
            <div class="module-actions">
              <el-button size="mini" :disabled="loadingMap[item.module]" @click="resetModule(item.module)">重置</el-button>
              <el-button
                size="mini"
                type="primary"
                :loading="savingMap[item.module]"
                @click="saveModule(item.module)"
              >
                {{ forms[item.module] && forms[item.module].id ? '保存修改' : '新增当前年份数据' }}
              </el-button>
            </div>
          </div>

          <el-form
            :ref="'form_' + item.module"
            v-loading="loadingMap[item.module]"
            :model="forms[item.module]"
            :rules="rulesMap[item.module]"
            label-width="108px"
            size="small"
            class="inline-editor-form"
          >
            <div v-for="group in formGroups(item.module)" :key="group.name" class="editor-form-group">
              <div class="editor-form-group__title">{{ group.name }}</div>
              <el-row :gutter="12">
                <el-col v-for="field in group.fields" :key="field.prop" :span="fieldSpan(field)">
                  <el-form-item :label="field.label" :prop="field.prop">
                    <el-input
                      v-if="field.type === 'text'"
                      v-model="forms[item.module][field.prop]"
                      :placeholder="'请输入' + field.label"
                    />
                    <el-input
                      v-else-if="field.type === 'textarea'"
                      v-model="forms[item.module][field.prop]"
                      type="textarea"
                      :rows="field.longText ? 4 : 3"
                      :placeholder="'请输入' + field.label"
                    />
                    <el-input-number
                      v-else-if="field.type === 'number'"
                      v-model="forms[item.module][field.prop]"
                      controls-position="right"
                      :min="0"
                      style="width: 100%"
                    />
                    <el-switch
                      v-else-if="field.type === 'switch'"
                      v-model="forms[item.module][field.prop]"
                      :active-value="1"
                      :inactive-value="0"
                    />
                    <el-date-picker
                      v-else-if="field.type === 'date'"
                      v-model="forms[item.module][field.prop]"
                      type="date"
                      value-format="yyyy-MM-dd"
                      placeholder="请选择日期"
                      style="width: 100%"
                    />
                    <el-select
                      v-else-if="field.type === 'select' || field.type === 'remoteSelect'"
                      v-model="forms[item.module][field.prop]"
                      :placeholder="'请选择' + field.label"
                      clearable
                      filterable
                      :remote="field.type === 'remoteSelect'"
                      :remote-method="keyword => remoteSearch(field, keyword)"
                      style="width: 100%"
                      @visible-change="visible => handleRemoteVisible(field, visible)"
                    >
                      <el-option
                        v-for="option in selectOptions(field)"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                    <el-input v-else v-model="forms[item.module][field.prop]" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div v-else class="empty-block empty-block--large">
      <i class="el-icon-document-checked"></i>
      <p>请选择一个专业方向</p>
      <span>右侧将展示完整字段，并支持在当前页面直接维护。</span>
    </div>
  </section>
</template>

<script>
import { listCrud, getCrud, addCrud, updateCrud, optionselectCrud } from '@/api/postgrad/crud'
import { getModuleConfig, getOptions } from '../../crud/moduleConfig'

const MODULES = [
  { module: 'program', label: '专业基础信息' },
  { module: 'admissionScore', label: '复试线' },
  { module: 'admissionPlan', label: '招生计划' },
  { module: 'admissionResult', label: '拟录取' },
  { module: 'programYearDataQuality', label: '数据质量' }
]

export default {
  name: 'WorkspaceEditorPanel',
  props: {
    selectedSchool: {
      type: Object,
      default: null
    },
    selectedProgram: {
      type: Object,
      default: null
    },
    year: {
      type: [Number, String],
      required: true
    },
    programYears: {
      type: Array,
      default: () => []
    },
    yearOptions: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      activeTab: 'overview',
      editableModules: MODULES,
      forms: {},
      originalForms: {},
      loadingMap: {},
      savingMap: {},
      remoteOptions: {},
      remoteSearchOptions: {},
      rulesMap: {}
    }
  },
  watch: {
    selectedProgram: {
      immediate: true,
      handler() {
        this.activeTab = 'overview'
        this.reloadAll()
      }
    },
    year() {
      this.reloadAll()
    }
  },
  methods: {
    reloadAll() {
      if (!this.selectedProgram) return
      this.editableModules.forEach(item => {
        this.loadModule(item.module)
      })
    },
    handleYearChange(year) {
      this.$emit('year-change', year)
    },
    loadModule(module) {
      this.$set(this.loadingMap, module, true)
      this.ensureRules(module)
      const query = this.recordQuery()
      const request = module === 'program'
        ? getCrud(module, this.selectedProgram.id)
        : listCrud(module, query)
      request.then(response => {
        const row = module === 'program'
          ? response.data || {}
          : ((response.rows || [])[0] || this.defaultForm(module))
        this.setForm(module, row)
        this.loadRemoteOptions(module)
      }).catch(() => {
        this.setForm(module, this.defaultForm(module))
      }).finally(() => {
        this.$set(this.loadingMap, module, false)
      })
    },
    setForm(module, row) {
      const form = Object.assign({}, this.defaultForm(module), row || {})
      this.$set(this.forms, module, form)
      this.$set(this.originalForms, module, Object.assign({}, form))
    },
    recordQuery() {
      return {
        pageNum: 1,
        pageSize: 1,
        programId: this.selectedProgram ? this.selectedProgram.id : undefined,
        year: this.year
      }
    },
    defaultForm(module) {
      const form = {}
      this.formFields(module).forEach(field => {
        if (field.default !== undefined) {
          form[field.prop] = field.default
        } else if (field.type === 'switch') {
          form[field.prop] = 0
        } else {
          form[field.prop] = undefined
        }
      })
      if (module === 'program') {
        form.id = this.selectedProgram ? this.selectedProgram.id : undefined
      } else {
        form.programId = this.selectedProgram ? this.selectedProgram.id : undefined
        form.year = Number(this.year)
      }
      return form
    },
    moduleConfig(module) {
      return getModuleConfig(module) || { columns: [] }
    },
    formFields(module) {
      return this.moduleConfig(module).columns.filter(field => {
        if (field.virtual || field.readonly || field.queryOnly) return false
        if (module === 'program' && field.prop === 'collegeId') return false
        if (module !== 'program' && field.prop === 'programId') return false
        if (module !== 'program' && field.prop === 'year') return false
        return true
      })
    },
    formGroups(module) {
      const groups = []
      this.formFields(module).forEach(field => {
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
    ensureRules(module) {
      const rules = {}
      this.formFields(module).forEach(field => {
        if (field.required) {
          rules[field.prop] = [{ required: true, message: field.label + '不能为空', trigger: field.type === 'select' || field.type === 'remoteSelect' ? 'change' : 'blur' }]
        }
      })
      this.$set(this.rulesMap, module, rules)
    },
    saveModule(module) {
      const refs = this.$refs['form_' + module]
      const formRef = Array.isArray(refs) ? refs[0] : refs
      if (!formRef) return
      formRef.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload(module)
        this.$set(this.savingMap, module, true)
        const request = payload.id ? updateCrud(module, payload) : addCrud(module, payload)
        request.then(() => {
          this.$modal.msgSuccess(payload.id ? '保存成功' : '新增成功')
          this.loadModule(module)
          this.$emit('saved', { module })
        }).finally(() => {
          this.$set(this.savingMap, module, false)
        })
      })
    },
    buildPayload(module) {
      const form = this.forms[module] || {}
      const payload = {}
      if (form.id) payload.id = form.id
      this.formFields(module).forEach(field => {
        if (Object.prototype.hasOwnProperty.call(form, field.prop)) {
          payload[field.prop] = form[field.prop]
        }
      })
      if (module !== 'program') {
        payload.programId = this.selectedProgram.id
        payload.year = Number(this.year)
      }
      return payload
    },
    resetModule(module) {
      this.setForm(module, this.originalForms[module] || this.defaultForm(module))
    },
    recordStatus(module) {
      const form = this.forms[module]
      if (!form) return '正在加载'
      if (module === 'program') return '专业基础记录'
      return form.id ? '已存在记录，可直接修改' : '当前年份暂无记录，保存后新增'
    },
    fieldSpan(field) {
      return 24
    },
    inferGroup(field) {
      const prop = field.prop.toLowerCase()
      if (field.type === 'textarea' || field.longText) return '补充信息'
      if (field.type === 'switch' || prop.includes('status') || prop.includes('risk')) return '状态与标记'
      if (prop.includes('score') || prop.includes('count') || prop.includes('plan') || prop.includes('quota')) return '指标数据'
      return '基础信息'
    },
    selectOptions(field) {
      if (field.optionModule) {
        return this.remoteSearchOptions[field.optionModule] || this.remoteOptions[field.optionModule] || []
      }
      return getOptions(field.optionsKey)
    },
    loadRemoteOptions(module) {
      const modules = [...new Set(this.formFields(module).filter(field => field.optionModule).map(field => field.optionModule))]
      modules.forEach(optionModule => {
        optionselectCrud(optionModule).then(response => {
          this.$set(this.remoteOptions, optionModule, (response.data || []).map(this.normalizeRemoteOption))
        })
      })
    },
    remoteSearch(field, keyword) {
      if (!field.optionModule) return
      optionselectCrud(field.optionModule, keyword ? { keyword } : undefined).then(response => {
        this.$set(this.remoteSearchOptions, field.optionModule, (response.data || []).map(this.normalizeRemoteOption))
      })
    },
    handleRemoteVisible(field, visible) {
      if (visible && field.type === 'remoteSelect') {
        this.remoteSearch(field, '')
      }
    },
    normalizeRemoteOption(item) {
      return {
        label: item.label || item.name || item.title || item.programName || item.code || item.id,
        value: item.id
      }
    },
    qualityType(value) {
      if (value === 'A') return 'success'
      if (value === 'B') return ''
      if (value === 'C') return 'warning'
      if (value === 'D') return 'danger'
      return 'info'
    }
  }
}
</script>

<style scoped>
.workspace-editor-panel {
  min-height: 620px;
}

.editor-body {
  height: 560px;
  overflow: auto;
  padding: 14px;
}

.editor-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.editor-title strong {
  display: block;
  color: #0f172a;
  font-size: 17px;
  line-height: 1.35;
}

.editor-title span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
}

.editor-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.overview-grid div {
  min-width: 0;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.overview-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.overview-grid strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

.editor-tabs {
  margin-top: 10px;
}

.editor-yearbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.editor-yearbar strong,
.editor-yearbar span {
  display: block;
}

.editor-yearbar strong {
  color: #0f172a;
  font-size: 13px;
}

.editor-yearbar span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.editor-year-select {
  flex: none;
  width: 108px;
}

.editor-section {
  margin-top: 16px;
}

.editor-section h4 {
  margin: 0 0 10px;
  font-size: 14px;
}

.module-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.module-toolbar strong,
.module-toolbar span {
  display: block;
}

.module-toolbar strong {
  color: #0f172a;
  font-size: 14px;
}

.module-toolbar span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.module-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.editor-form-group {
  padding-bottom: 4px;
}

.editor-form-group + .editor-form-group {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.editor-form-group__title {
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.empty-block {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #64748b;
  text-align: center;
}

.empty-block--large {
  min-height: 520px;
}

.empty-block i {
  margin-bottom: 10px;
  color: #94a3b8;
  font-size: 34px;
}

.empty-block p {
  margin: 0 0 6px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.empty-block span {
  max-width: 320px;
  color: #94a3b8;
  font-size: 12px;
}
</style>
