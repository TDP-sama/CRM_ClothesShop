import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const STORAGE_KEY = 'moda-theme'

/** Each theme is a set of CSS variables in App.css under [data-theme="<id>"]. */
export const THEMES = [
  {
    id: 'co-ban',
    name: 'Cơ bản',
    description: 'Sạch sẽ, rõ ràng, dễ dùng',
    preview: { bg: '#ffffff', surface: '#f4f4f5', text: '#18181b', accent: '#18181b' },
  },
  {
    id: 'toi-gian',
    name: 'Tối giản',
    description: 'Nhiều khoảng trắng, chữ có chân thanh lịch',
    preview: { bg: '#faf7f2', surface: '#efe9df', text: '#2b2621', accent: '#2b2621' },
  },
  {
    id: 'nang-dong',
    name: 'Năng động',
    description: 'Màu sắc tươi, bo góc mềm, nổi bật khuyến mãi',
    preview: { bg: '#ffffff', surface: '#fff1ec', text: '#1d1d1f', accent: '#ff5a36' },
  },
  {
    id: 'cao-cap',
    name: 'Cao cấp',
    description: 'Nền tối, điểm nhấn vàng đồng sang trọng',
    preview: { bg: '#0e0e10', surface: '#1a1a1d', text: '#f2efe9', accent: '#c9a96e' },
  },
]

const ThemeContext = createContext(null)

function initialTheme() {
  // ?theme=<id> lets a link open the store in a specific style (it is then remembered).
  const fromUrl = new URLSearchParams(window.location.search).get('theme')
  if (THEMES.some((t) => t.id === fromUrl)) return fromUrl
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (THEMES.some((t) => t.id === saved)) return saved
  } catch {
    /* private mode: fall back to the default */
  }
  return THEMES[0].id
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(initialTheme)
  const [settingsOpen, setSettingsOpen] = useState(false)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      /* not persisted, still applied */
    }
  }, [theme])

  const setTheme = useCallback((id) => setThemeState(id), [])
  const value = useMemo(
    () => ({
      theme,
      setTheme,
      settingsOpen,
      openSettings: () => setSettingsOpen(true),
      closeSettings: () => setSettingsOpen(false),
    }),
    [theme, setTheme, settingsOpen],
  )
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export const useTheme = () => useContext(ThemeContext)
