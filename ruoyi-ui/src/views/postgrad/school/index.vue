<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="72px">
      <el-form-item label="学校名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入学校名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="省份" prop="province">
        <el-input
          v-model="queryParams.province"
          placeholder="请输入省份"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="城市" prop="city">
        <el-input
          v-model="queryParams.city"
          placeholder="请输入城市"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学校层次" prop="tier">
        <el-select v-model="queryParams.tier" placeholder="请选择学校层次" clearable>
          <el-option
            v-for="item in tierOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['postgrad:school:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['postgrad:school:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['postgrad:school:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['postgrad:school:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="schoolList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="学校名称" align="center" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="简称" align="center" prop="shortName" width="110" show-overflow-tooltip />
      <el-table-column label="省份" align="center" prop="province" width="100" />
      <el-table-column label="城市" align="center" prop="city" width="100" />
      <el-table-column label="层次" align="center" prop="tier" width="120">
        <template slot-scope="scope">
          <el-tag size="mini" effect="plain">{{ formatTier(scope.row.tier) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="985" align="center" prop="is985" width="70">
        <template slot-scope="scope">
          <el-tag v-if="Number(scope.row.is985) === 1" type="danger" size="mini">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="211" align="center" prop="is211" width="70">
        <template slot-scope="scope">
          <el-tag v-if="Number(scope.row.is211) === 1" type="warning" size="mini">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="双一流" align="center" prop="isDoubleFirst" width="80">
        <template slot-scope="scope">
          <el-tag v-if="Number(scope.row.isDoubleFirst) === 1" type="success" size="mini">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="公办" align="center" prop="isPublic" width="70">
        <template slot-scope="scope">
          <span>{{ Number(scope.row.isPublic) === 1 ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" size="mini">
            {{ formatStatus(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="官网" align="center" prop="website" min-width="180" show-overflow-tooltip />
      <el-table-column label="更新时间" align="center" prop="updatedAt" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updatedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['postgrad:school:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['postgrad:school:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="学校全称" prop="name">
              <el-input v-model="form.name" placeholder="请输入学校全称" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="学校简称" prop="shortName">
              <el-input v-model="form.shortName" placeholder="请输入简称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="省份" prop="province">
              <el-input v-model="form.province" placeholder="如 福建" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" placeholder="如 福州" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="学校层次" prop="tier">
          <el-select v-model="form.tier" placeholder="请选择学校层次" style="width: 100%">
            <el-option
              v-for="item in tierOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="985" prop="is985">
              <el-switch v-model="form.is985" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="211" prop="is211">
              <el-switch v-model="form.is211" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="双一流" prop="isDoubleFirst">
              <el-switch v-model="form.isDoubleFirst" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="公办" prop="isPublic">
              <el-switch v-model="form.isPublic" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="官网" prop="website">
          <el-input v-model="form.website" placeholder="请输入学校官网或研招官网" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.value"
            >{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSchool, getSchool, delSchool, addSchool, updateSchool } from '@/api/postgrad/school'

export default {
  name: 'School',
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      schoolList: [],
      title: '',
      open: false,
      tierOptions: [
        { label: '985', value: '985' },
        { label: '211', value: '211' },
        { label: '双一流', value: 'DOUBLE_FIRST' },
        { label: '普通公办', value: 'PUBLIC_REGULAR' },
        { label: '民办', value: 'PRIVATE' },
        { label: '独立学院', value: 'INDEPENDENT' },
        { label: '科研院所', value: 'RESEARCH_INSTITUTE' },
        { label: '其他', value: 'OTHER' }
      ],
      statusOptions: [
        { label: '正常', value: 'active' },
        { label: '停用', value: 'inactive' }
      ],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: undefined,
        province: undefined,
        city: undefined,
        tier: undefined,
        status: undefined
      },
      form: {},
      rules: {
        name: [
          { required: true, message: '学校全称不能为空', trigger: 'blur' }
        ],
        province: [
          { required: true, message: '省份不能为空', trigger: 'blur' }
        ],
        city: [
          { required: true, message: '城市不能为空', trigger: 'blur' }
        ],
        tier: [
          { required: true, message: '学校层次不能为空', trigger: 'change' }
        ],
        status: [
          { required: true, message: '状态不能为空', trigger: 'change' }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listSchool(this.queryParams).then(response => {
        this.schoolList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    formatTier(value) {
      const item = this.tierOptions.find(option => option.value === value)
      return item ? item.label : value
    },
    formatStatus(value) {
      const item = this.statusOptions.find(option => option.value === value)
      return item ? item.label : value
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: undefined,
        name: undefined,
        shortName: undefined,
        province: undefined,
        city: undefined,
        tier: 'OTHER',
        is985: 0,
        is211: 0,
        isDoubleFirst: 0,
        isPublic: 1,
        website: undefined,
        status: 'active'
      }
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
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '添加学校'
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getSchool(id).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改学校'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          if (this.form.id !== undefined) {
            updateSchool(this.form).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addSchool(this.form).then(() => {
              this.$modal.msgSuccess('新增成功')
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除学校编号为"' + ids + '"的数据项？').then(() => {
        return delSchool(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    handleExport() {
      this.download('postgrad/school/export', {
        ...this.queryParams
      }, `school_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
