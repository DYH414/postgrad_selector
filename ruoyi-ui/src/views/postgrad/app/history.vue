<template>
  <div class="app-page">
    <AppHeader current-page="history" />
    <div class="app-body">
      <h3>推荐历史</h3>
      <el-table :data="logs" v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="rule_version" label="规则版本" width="100" />
        <el-table-column prop="created_at" label="生成时间" width="180">
          <template slot-scope="scope">{{ scope.row.created_at }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="$router.push('/app/history/' + scope.row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && logs.length === 0" style="text-align:center;padding:60px;color:#909399">
        暂无推荐记录
        <br /><br />
        <el-button type="primary" @click="$router.push('/app/recommend')">去生成推荐</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { history } from '@/api/postgrad/appRecommendation'
import AppHeader from './components/AppHeader'
import { mapActions } from 'vuex'

export default {
  name: 'AppHistory',
  components: { AppHeader },
  data() {
    return { logs: [], loading: false }
  },
  created() { this.fetchList() },
  methods: {
    ...mapActions('appUser', ['Logout']),
    fetchList() {
      this.loading = true
      history().then(res => {
        this.logs = res.data || []
      }).finally(() => { this.loading = false })
    },
    handleLogout() {
      this.Logout().then(() => { this.$router.push('/app/login') })
    }
  }
}
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

.app-body { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
</style>
