import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, ensureCsrf } from '../api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // undefined = still checking the session, null = logged out
  const [user, setUser] = useState(undefined)

  useEffect(() => {
    ensureCsrf()
    api.me().then(setUser).catch(() => setUser(null))
  }, [])

  const login = useCallback(async (email, password) => {
    const u = await api.login(email, password)
    setUser(u)
    return u
  }, [])

  const register = useCallback(async (fullName, email, password) => {
    await api.register(fullName, email, password)
    const u = await api.login(email, password)
    setUser(u)
    return u
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      setUser(null)
    }
  }, [])

  /** Re-reads role and permissions, e.g. when entering the admin area after an admin changed them. */
  const refresh = useCallback(() => api.me().then(setUser).catch(() => setUser(null)), [])

  const value = useMemo(() => {
    const permissions = user?.permissions ?? []
    return {
      user,
      loading: user === undefined,
      isAdmin: user?.role === 'ADMIN',
      // Staff and admins see the "Quản trị" entry and can open the admin area.
      isBackOffice: user?.role === 'ADMIN' || user?.role === 'STAFF',
      can: (section) => permissions.includes(section),
      login,
      register,
      logout,
      refresh,
    }
  }, [user, login, register, logout, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
