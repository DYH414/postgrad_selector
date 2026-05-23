import { login, logout, me } from '@/api/postgrad/appAuth'
import { getAppToken, setAppToken, removeAppToken } from '@/utils/appAuth'

const appUser = {
  namespaced: true,

  state: {
    token: getAppToken(),
    userId: null,
    role: null,
    profile: null
  },

  mutations: {
    SET_TOKEN(state, token) {
      state.token = token
    },
    SET_USER_ID(state, id) {
      state.userId = id
    },
    SET_ROLE(state, role) {
      state.role = role
    },
    SET_PROFILE(state, profile) {
      state.profile = profile
    }
  },

  actions: {
    Login({ commit }, data) {
      return new Promise((resolve, reject) => {
        login(data).then(res => {
          setAppToken(res.token)
          commit('SET_TOKEN', res.token)
          commit('SET_USER_ID', res.userId)
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    FetchMe({ commit }) {
      return new Promise((resolve, reject) => {
        me().then(res => {
          commit('SET_USER_ID', res.userId)
          commit('SET_ROLE', res.role)
          commit('SET_PROFILE', res.profile)
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    Logout({ commit }) {
      return new Promise((resolve) => {
        logout().finally(() => {
          commit('SET_TOKEN', '')
          commit('SET_USER_ID', null)
          commit('SET_ROLE', null)
          commit('SET_PROFILE', null)
          removeAppToken()
          resolve()
        })
      })
    },

    SetProfile({ commit }, profile) {
      commit('SET_PROFILE', profile)
    }
  }
}

export default appUser
