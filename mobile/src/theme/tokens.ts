export type ThemeName = 'nocturne' | 'organic';

export interface ThemeTokens {
  name: ThemeName;
  color: {
    bg: string;
    surface: string;
    surfaceDim: string;
    text: string;
    muted: string;
    mutedDim: string;
    accent: string;
    accentLight: string;
    border: string;
    sage?: string;
  };
  radius: { sm: number; md: number; lg: number; pill: number };
  spacing: { xs: number; sm: number; md: number; lg: number; xl: number };
  headingWeight: '500';
}

const nocturne: ThemeTokens = {
  name: 'nocturne',
  color: {
    bg: '#161826',
    surface: '#232532',
    surfaceDim: '#1c1e2c',
    text: '#e9e9ed',
    muted: '#9397ab',
    mutedDim: '#6d7183',
    accent: '#9184d9',
    accentLight: '#ded9ff',
    border: '#2e3040',
  },
  radius: { sm: 4, md: 8, lg: 14, pill: 999 },
  spacing: { xs: 4, sm: 8, md: 14, lg: 22, xl: 32 },
  headingWeight: '500',
};

const organic: ThemeTokens = {
  name: 'organic',
  color: {
    bg: '#f5ead8',
    surface: '#ebddc5',
    surfaceDim: '#e3d3b5',
    text: '#201e1d',
    muted: '#5c5650',
    mutedDim: '#867e73',
    accent: '#c67139',
    accentLight: '#e0aa7d',
    border: '#ddc9a3',
    sage: '#7a8a5e',
  },
  radius: { sm: 8, md: 16, lg: 28, pill: 999 },
  spacing: { xs: 4, sm: 8, md: 14, lg: 22, xl: 32 },
  headingWeight: '500',
};

export function getTokens(theme: ThemeName): ThemeTokens {
  return theme === 'nocturne' ? nocturne : organic;
}
