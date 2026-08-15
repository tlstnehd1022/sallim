import React, { createContext, useContext, useMemo, useState } from 'react';
import { getTokens, ThemeName, ThemeTokens } from './tokens';

interface ThemeContextValue {
  theme: ThemeName;
  tokens: ThemeTokens;
  setTheme: (theme: ThemeName) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<ThemeName>('nocturne');
  const value = useMemo<ThemeContextValue>(
    () => ({ theme, tokens: getTokens(theme), setTheme }),
    [theme]
  );
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
