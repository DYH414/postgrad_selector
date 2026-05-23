<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :inline="true" :model="queryParams" size="small">
      <el-form-item label="学校">
        <el-input v-model="queryParams.schoolName" placeholder="学校名" clearable />
      </el-form-item>
      <el-form-item label="专业代码">
        <el-input v-model="queryParams.programCode" placeholder="如085404" clearable />
      </el-form-item>
      <el-form-item label="年份">
        <el-select v-model="queryParams.year" placeholder="年份" clearable>
          <el-option v-for="y in years" :key="y" :label="y" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="状态" clearable>
          <el-option label="待审核" value="pending" />
          <el-option label="草稿" value="seed" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已跳过" value="skipped" />
        </el-select>
      </el-form-item>
      <el-form-item label="置信度">
        <el-select v-model="queryParams.confidence" placeholder="置信度" clearable>
          <el-option label="高" value="high" />
          <el-option label="中" value="medium" />
          <el-option label="低" value="low" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="queryParams.sourceType" placeholder="来源" clearable>
          <el-option label="研招网" value="YZ_CHSI" />
          <el-option label="HTML表格" value="OFFICIAL_HTML" />
          <el-option label="N诺" value="THIRD_PARTY" />
          <el-option label="人工录入" value="MANUAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="匹配">
        <el-select v-model="queryParams.matchStatus" placeholder="匹配状态" clearable>
          <el-option label="已匹配" value="matched" />
          <el-option label="未匹配" value="unmatched" />
        </el-select>
      </el-form-item>
      <el-form-item label="408">
        <el-select v-model="queryParams.is408" placeholder="408筛选" clearable>
          <el-option label="408专业" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="el-icon-download" @click="quickFilterNoob">N诺数据速查</el-button>
      </el-form-item>
    </el-form>

    <!-- 统计 -->
    <el-row :gutter="10" style="margin-bottom:15px">
      <el-col :span="4" v-for="s in stats" :key="s.key">
        <el-card shadow="hover" :body-style="{padding:'10px',textAlign:'center'}">
          <div style="font-size:24px;font-weight:bold">{{ s.count }}</div>
          <div style="color:#909399;font-size:12px">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div style="margin-bottom:12px">
      <el-button type="success" size="small" icon="el-icon-check" :loading="autoApproving"
        @click="handleAutoApprove">一键通过学校/专业目录数据</el-button>
      <span style="color:#909399;font-size:12px;margin-left:8px">
        自动通过无复试线、无计划、无录取数据的学校/学院/专业记录（来自研招网目录，无需人工审核）
      </span>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe style="width:100%"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="school_name" label="学校" width="120" />
      <el-table-column prop="program_code" label="专业代码" width="100" />
      <el-table-column prop="program_name" label="专业名" width="120" show-overflow-tooltip />
      <el-table-column prop="year" label="年份" width="60" />
      <el-table-column prop="score_line" label="复试线" width="70">
        <template slot-scope="scope">
          <span v-if="scope.row.scoreLine">{{ scope.row.scoreLine }}</span>
          <span v-else style="color:#c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="confidence" label="置信度" width="70">
        <template slot-scope="scope">
          <el-tag :type="confTag(scope.row.confidence)" size="mini">{{ scope.row.confidence }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="90" />
      <el-table-column prop="status" label="状态" width="80">
        <template slot-scope="scope">
          <el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="匹配" width="70">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.matched_program_id" type="success" size="mini">已匹配</el-tag>
          <el-tag v-else type="info" size="mini">未匹配</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="408" width="55">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.exam_subjects && scope.row.exam_subjects.includes('408')" type="success" size="mini">408</el-tag>
          <span v-else style="color:#c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="matchedProgramLabel" label="匹配专业" width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="showDetail(scope.row)">详情</el-button>
          <el-button v-if="scope.row.status==='pending'" size="mini" type="text" icon="el-icon-check"
            style="color:#67c23a" @click="approve(scope.row)">通过</el-button>
          <el-button v-if="scope.row.status==='pending'" size="mini" type="text" icon="el-icon-close"
            style="color:#f56c6c" @click="reject(scope.row)">驳回</el-button>
          <el-button v-if="scope.row.status==='pending'" size="mini" type="text" icon="el-icon-remove"
            @click="skip(scope.row)">跳过</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />

    <!-- 详情弹窗 -->
    <el-dialog :title="'数据详情 #' + detail.id" :visible.sync="detailVisible" width="800px">
      <el-descriptions :column="3" border size="small" v-if="detail.id">
        <el-descriptions-item label="学校">{{ detail.school_name }}</el-descriptions-item>
        <el-descriptions-item label="专业代码">{{ detail.program_code }}</el-descriptions-item>
        <el-descriptions-item label="年份">{{ detail.year }}</el-descriptions-item>
        <el-descriptions-item label="复试总分线">{{ detail.score_line }}</el-descriptions-item>
        <el-descriptions-item label="政治">{{ detail.single_politics }}</el-descriptions-item>
        <el-descriptions-item label="英语">{{ detail.single_english }}</el-descriptions-item>
        <el-descriptions-item label="数学">{{ detail.single_math }}</el-descriptions-item>
        <el-descriptions-item label="专业课">{{ detail.single_professional }}</el-descriptions-item>
        <el-descriptions-item label="置信度">
          <el-tag :type="confTag(detail.confidence)" size="mini">{{ detail.confidence }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source_type }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="mini">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="匹配专业" :span="2">{{ detail.matched_program_label }}</el-descriptions-item>
        <el-descriptions-item label="审核备注" :span="2">{{ detail.review_note || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源URL" :span="3">
          <a v-if="detail.source_url" :href="detail.source_url" target="_blank" style="word-break:break-all">
            {{ detail.source_url }}
          </a>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="原始文本" :span="3">
          <div style="max-height:200px;overflow:auto;white-space:pre-wrap;background:#f5f7fa;padding:8px;font-size:12px">
            {{ detail.raw_text || '-' }}
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="抽取JSON" :span="3">
          <div style="max-height:200px;overflow:auto;white-space:pre-wrap;background:#f5f7fa;padding:8px;font-size:12px">
            {{ detail.extract_json || '-' }}
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <span slot="footer">
        <el-button @click="detailVisible=false">关闭</el-button>
        <el-button v-if="detail.status==='pending'" type="danger" @click="rejectFromDetail">驳回</el-button>
        <el-button v-if="detail.status==='pending'" type="success" @click="approveFromDetail">通过并入库</el-button>
      </span>
    </el-dialog>

    <!-- 批量操作 -->
    <div v-if="selection.length>0" style="margin-top:10px">
      <el-button type="success" size="small" @click="batchApprove">批量通过({{ selection.length }})</el-button>
    </div>
  </div>
</template>

<script>
import { listReview, getReview, approveReview, rejectReview, skipReview,
  batchApproveReview, reviewStats, autoApproveDirectory } from '@/api/postgrad/review'

export default {
  data() {
    return {
      loading: false,
      queryParams: { pageNum: 1, pageSize: 10 },
      tableData: [],
      total: 0,
      selection: [],
      detail: {},
      detailVisible: false,
      stats: [],
      years: [2023,2024,2025,2026],
      autoApproving: false
    }
  },
  created() { this.getList(); this.getStats() },
  methods: {
    getList() {
      this.loading = true
      listReview(this.queryParams).then(r => {
        this.tableData = r.rows
        this.total = r.total
        this.loading = false
      })
    },
    getStats() {
      reviewStats().then(r => {
        const d = r.data || r
        this.stats = [
          { key: 'pending', label: '待审核', count: d.pending },
          { key: 'approved', label: '已通过', count: d.approved },
          { key: 'rejected', label: '已驳回', count: d.rejected },
          { key: 'skipped', label: '已跳过', count: d.skipped },
          { key: 'total', label: '总计', count: d.total }
        ]
      })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10 }
      this.getList()
    },
    handleSelectionChange(v) { this.selection = v },
    showDetail(row) {
      getReview(row.id).then(r => { this.detail = r.data || r; this.detailVisible = true })
    },
    approve(row) {
      this.$prompt('审核备注(可选):', '确认通过', { inputType: 'textarea' }).then(({ value }) => {
        approveReview(row.id, { reviewNote: value }).then(() => { this.$message.success('已通过并入库'); this.getList(); this.getStats() })
      }).catch(() => {})
    },
    reject(row) {
      this.$prompt('驳回原因:', '确认驳回', { inputType: 'textarea' }).then(({ value }) => {
        rejectReview(row.id, { reviewNote: value }).then(() => { this.$message.success('已驳回'); this.getList(); this.getStats() })
      }).catch(() => {})
    },
    skip(row) {
      skipReview(row.id).then(() => { this.$message.success('已跳过'); this.getList(); this.getStats() })
    },
    approveFromDetail() {
      approveReview(this.detail.id, {}).then(() => {
        this.$message.success('已通过并入库'); this.detailVisible = false; this.getList(); this.getStats()
      })
    },
    rejectFromDetail() {
      this.$prompt('驳回原因:', '确认驳回', { inputType: 'textarea' }).then(({ value }) => {
        rejectReview(this.detail.id, { reviewNote: value }).then(() => {
          this.$message.success('已驳回'); this.detailVisible = false; this.getList(); this.getStats()
        })
      }).catch(() => {})
    },
    batchApprove() {
      const ids = this.selection.map(r => r.id)
      this.$confirm(`确认通过 ${ids.length} 条记录?`, '批量审核').then(() => {
        batchApproveReview({ ids }).then(() => { this.$message.success('已批量通过'); this.getList(); this.getStats() })
      }).catch(() => {})
    },
    handleAutoApprove() {
      this.$confirm('将自动通过所有无复试线、无招生计划、无录取数据的学校/学院/专业记录。这些目录数据来自研招网，无需人工审核。确认继续？', '一键通过目录数据').then(() => {
        this.autoApproving = true
        autoApproveDirectory().then(res => {
          this.$message.success(res.msg || '操作成功')
          this.getList()
          this.getStats()
        }).finally(() => { this.autoApproving = false })
      }).catch(() => {})
    },
    quickFilterNoob() {
      this.queryParams = { sourceType: 'THIRD_PARTY', is408: '1', pageNum: 1, pageSize: 10 }
      this.getList()
    },
    confTag(v) { return v==='high'?'success':v==='medium'?'warning':v==='low'?'danger':'info' },
    statusTag(v) { return v==='approved'?'success':v==='rejected'?'danger':v==='pending'?'warning':'info' },
    statusLabel(v) { return {pending:'待审核',seed:'草稿',approved:'已通过',rejected:'已驳回',skipped:'已跳过'}[v]||v }
  }
}
</script>
