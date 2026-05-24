<template>
  <div class="app-page">
    <AppHeader current-page="favorites" />
    <div class="app-body">
      <h3>我的收藏</h3>
      <el-table :data="favorites" v-loading="loading" style="width:100%">
        <el-table-column prop="school_name" label="学校" />
        <el-table-column prop="tier" label="层次" width="80" />
        <el-table-column prop="college_name" label="学院" />
        <el-table-column prop="program_name" label="专业" />
        <el-table-column prop="program_code" label="专业代码" width="100" />
        <el-table-column prop="study_mode" label="学习方式" width="90">
          <template slot-scope="scope">
            {{ scope.row.study_mode === 'full_time' ? '全日制' : '非全日制' }}
          </template>
        </el-table-column>
        <el-table-column prop="degree_type" label="学位" width="70">
          <template slot-scope="scope">
            {{ scope.row.degree_type === 'professional' ? '专硕' : '学硕' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="handleRemove(scope.row)">取消收藏</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && favorites.length === 0" style="text-align:center;padding:60px;color:#909399">
        暂无收藏，去推荐结果页收藏意向专业吧
        <br /><br />
        <el-button type="primary" @click="$router.push('/app/recommend')">去生成推荐</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { listFavorites, removeFavorite } from '@/api/postgrad/appFavorites'
import AppHeader from './components/AppHeader'
import { mapActions } from 'vuex'

export default {
  name: 'AppFavorites',
  components: { AppHeader },
  data() {
    return { favorites: [], loading: false }
  },
  created() { this.fetchList() },
  methods: {
    ...mapActions('appUser', ['Logout']),
    fetchList() {
      this.loading = true
      listFavorites().then(res => {
        this.favorites = res.data || []
      }).finally(() => { this.loading = false })
    },
    handleRemove(row) {
      removeFavorite(row.program_id).then(() => {
        this.$message.success('已取消收藏')
        this.favorites = this.favorites.filter(f => f.program_id !== row.program_id)
      })
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
