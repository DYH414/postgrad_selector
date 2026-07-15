<template>
  <div class="app-container home">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Postgrad Selector Console</p>
        <h2>考研择校管理后台</h2>
        <p class="hero-copy">
          聚合院校、专业、招生、知识库和 AI 调用状态，支撑用户端择校推荐、报告生成与数据运维。
        </p>
      </div>
      <el-tag type="success">v{{ version }}</el-tag>
    </section>

    <el-row :gutter="16" class="quick-grid">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in quickLinks" :key="item.path">
        <el-card shadow="hover" class="quick-card" @click.native="goRoute(item.path)">
          <i :class="item.icon"></i>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card class="status-card">
          <div slot="header">上线检查重点</div>
          <ul class="check-list">
            <li>确认 MySQL、Redis、Qdrant 与模型服务环境变量已配置。</li>
            <li>确认知识库活动版本、最近索引批次和检索质量正常。</li>
            <li>确认 AI 服务状态、Tool Trace 和异常摘要没有持续错误。</li>
            <li>确认用户端推荐、对话、报告生成链路通过验收。</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="status-card">
          <div slot="header">运维入口</div>
          <div class="ops-actions">
            <el-button type="primary" plain @click="goRoute('/postgrad/rag')">知识库状态</el-button>
            <el-button type="primary" plain @click="goRoute('/postgrad/observe')">AI 服务状态</el-button>
            <el-button plain @click="goRoute('/postgrad/workspace')">学校数据工作台</el-button>
            <el-button plain @click="goRoute('/postgrad/review')">数据审核</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: 'Index',
  data() {
    return {
      version: '1.0.0',
      quickLinks: [
        { title: '学校数据', desc: '维护院校、专业、招生和录取事实。', icon: 'el-icon-school', path: '/postgrad/workspace' },
        { title: '知识库状态', desc: '查看索引版本、检索记录和质量检查。', icon: 'el-icon-collection', path: '/postgrad/rag' },
        { title: 'AI 服务状态', desc: '观察模型调用、工具执行和异常摘要。', icon: 'el-icon-monitor', path: '/postgrad/observe' },
        { title: '数据审核', desc: '处理采集数据的审核与入库流程。', icon: 'el-icon-document-checked', path: '/postgrad/review' }
      ]
    }
  },
  methods: {
    goRoute(path) {
      this.$router.push(path)
    }
  }
}
</script>

<style scoped lang="scss">
.home {
  background: #f5f7fb;
  min-height: calc(100vh - 84px);
}

.hero-card,
.status-card,
.quick-card {
  border-radius: 8px;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #e5eaf3;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-weight: 700;
  font-size: 13px;
}

.hero-card h2 {
  margin: 0 0 10px;
  font-size: 26px;
  color: #172033;
}

.hero-copy {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.quick-grid {
  margin-bottom: 16px;
}

.quick-card {
  min-height: 150px;
  cursor: pointer;
}

.quick-card i {
  font-size: 28px;
  color: #2563eb;
}

.quick-card h3 {
  margin: 14px 0 8px;
  color: #172033;
}

.quick-card p,
.check-list {
  color: #64748b;
  line-height: 1.7;
}

.check-list {
  margin: 0;
  padding-left: 18px;
}

.ops-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
