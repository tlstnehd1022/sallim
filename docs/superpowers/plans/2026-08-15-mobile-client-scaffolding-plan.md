# 모바일 클라이언트 스캐폴딩 + 홈 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expo + TypeScript React Native 클라이언트를 스캐폴딩하고, Nocturne/Organic 두 테마를 지원하는 토큰 시스템 위에 3탭 셸과 홈(평면도 + 요약 스트립) 화면을 구현한다.

**Architecture:** `mobile/` 디렉터리에 독립된 Expo 프로젝트. Expo Router로 3탭(`홈`/`캘린더`/`가계부`, 캘린더·가계부는 이번 범위에서 플레이스홀더) 파일 기반 라우팅. 테마는 Context 기반 토큰 맵 1개(Nocturne/Organic 값만 다름). 평면도는 퍼센트 좌표계 절대 포지셔닝 뷰. 데이터는 전부 정적 목업(백엔드 없음).

**Tech Stack:** Expo SDK(TypeScript 템플릿) · expo-router · React Context · Jest + @testing-library/react-native

**Spec:** `docs/superpowers/specs/2026-08-15-mobile-client-scaffolding-design.md` (및 `docs/design-handoff/README.md`, `docs/design-handoff/spec/floorplan-handoff-v1.md`, `sallim-master-spec.md` 4장/10장)

## Global Constraints

- `mobile/` 은 Gradle 백엔드 모듈(`bootstrap`/`common`/`household`/`chore`/`calendar`/`ledger`)과 완전히 분리된 형제 디렉터리 — 그 이름들의 디렉터리/파일을 만들지 않는다
- 상태 관리는 React Context + useState만 사용 — Redux/Zustand 등 추가 라이브러리 금지 (YAGNI)
- 백엔드 연동 없음 — 전부 정적 목업 데이터, 네트워크 호출 금지
- Nocturne이 기본/1순위 테마, Organic은 토큰만 다른 동일 정보구조 — 하드코딩된 Nocturne 전용 스타일 금지, 반드시 테마 토큰을 통해 값을 가져온다
- 화면 카피는 한국어, 반말·구어체 (마스터 스펙 10장 "톤")
- 이번 플랜 범위 밖: 캘린더/가계부/설정 등 나머지 9개 화면, 백엔드 연동, 실시간 동기화, 인증 — "다음 단계" 절 참고

---

## File Structure

```
mobile/
  package.json
  tsconfig.json
  app.json
  app/
    _layout.tsx                    # 루트 레이아웃: ThemeProvider로 감싸기
    (tabs)/
      _layout.tsx                  # 3탭 네비게이터
      index.tsx                    # 홈 탭 (화면 1)
      calendar.tsx                 # 캘린더 탭 (플레이스홀더)
      ledger.tsx                   # 가계부 탭 (플레이스홀더)
  src/
    theme/
      tokens.ts                    # Nocturne/Organic 토큰 맵
      ThemeContext.tsx             # ThemeProvider, useTheme()
      tokens.test.ts
    domain/
      cleanliness.ts               # 청결도 계산 (마스터 스펙 4.2)
      cleanliness.test.ts
      floorplan.ts                 # 방 좌표 clamp 등 순수 함수
      floorplan.test.ts
      seedRooms.ts                 # DEFAULT_ROOMS 이식
    components/
      home/
        RoomTile.tsx                # 방 타일 (좌표/청결도 시각화)
        RoomTile.test.tsx
        SummaryStrip.tsx            # 요약 스트립
        SummaryStrip.test.tsx
        FloorPlanCanvas.tsx         # 방 타일들을 배치하는 캔버스
        FloorPlanCanvas.test.tsx
```

---

### Task 1: Expo 스캐폴딩 + 테마 시스템 + 3탭 셸

**Files:**
- Create: `mobile/package.json`
- Create: `mobile/tsconfig.json`
- Create: `mobile/app.json`
- Create: `mobile/app/_layout.tsx`
- Create: `mobile/app/(tabs)/_layout.tsx`
- Create: `mobile/app/(tabs)/index.tsx`
- Create: `mobile/app/(tabs)/calendar.tsx`
- Create: `mobile/app/(tabs)/ledger.tsx`
- Create: `mobile/src/theme/tokens.ts`
- Test: `mobile/src/theme/tokens.test.ts`
- Create: `mobile/src/theme/ThemeContext.tsx`

**Interfaces:**
- Consumes: 없음 (최초 태스크)
- Produces:
  - `type ThemeName = 'nocturne' | 'organic'`
  - `interface ThemeTokens { name: ThemeName; color: {...}; radius: {...}; spacing: {...} }`
  - `getTokens(theme: ThemeName): ThemeTokens`
  - `ThemeProvider`, `useTheme(): { theme: ThemeName; tokens: ThemeTokens; setTheme(t: ThemeName): void }` — Task 2가 `useTheme()`으로 색상/반경 값을 가져다 쓴다.

- [ ] **Step 1: Expo 프로젝트 생성**

Run (저장소 루트에서):
```bash
npx create-expo-app@latest mobile --template blank-typescript
cd mobile
npx expo install expo-router react-native-safe-area-context react-native-screens expo-linking expo-constants expo-status-bar
npm install --save-dev jest-expo @testing-library/react-native @types/jest
```

`mobile/package.json`의 `"main"`을 `"expo-router/entry"`로, `scripts.test`를 `"jest"`로 설정하고 다음 jest 설정을 추가한다:

```json
{
  "jest": {
    "preset": "jest-expo",
    "transformIgnorePatterns": [
      "node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg)"
    ]
  }
}
```

`mobile/app.json`의 `"expo"` 객체에 `"scheme": "sallim"`, `"plugins": ["expo-router"]` 를 추가한다.

- [ ] **Step 2: 테마 토큰 맵 실패하는 테스트 작성**

`mobile/src/theme/tokens.test.ts`:
```typescript
import { getTokens } from './tokens';

describe('getTokens', () => {
  it('Nocturne 토큰은 확정값과 일치한다', () => {
    const tokens = getTokens('nocturne');
    expect(tokens.color.bg).toBe('#161826');
    expect(tokens.color.surface).toBe('#232532');
    expect(tokens.color.text).toBe('#e9e9ed');
    expect(tokens.color.accent).toBe('#9184d9');
    expect(tokens.radius.sm).toBe(4);
    expect(tokens.radius.md).toBe(8);
    expect(tokens.radius.lg).toBe(14);
  });

  it('Organic 토큰은 확정값과 일치한다', () => {
    const tokens = getTokens('organic');
    expect(tokens.color.bg).toBe('#f5ead8');
    expect(tokens.color.surface).toBe('#ebddc5');
    expect(tokens.color.text).toBe('#201e1d');
    expect(tokens.color.accent).toBe('#c67139');
    expect(tokens.color.sage).toBe('#7a8a5e');
    expect(tokens.radius.sm).toBe(8);
    expect(tokens.radius.md).toBe(16);
    expect(tokens.radius.lg).toBe(28);
    expect(tokens.radius.pill).toBe(999);
  });
});
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `cd mobile && npx jest src/theme/tokens.test.ts`
Expected: FAIL — `./tokens` 모듈을 찾을 수 없음

- [ ] **Step 4: 토큰 맵 구현**

`mobile/src/theme/tokens.ts`:
```typescript
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
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/theme/tokens.test.ts`
Expected: PASS

- [ ] **Step 6: ThemeProvider 구현**

`mobile/src/theme/ThemeContext.tsx`:
```typescript
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
```

- [ ] **Step 7: 루트 레이아웃 + 3탭 셸 작성**

`mobile/app/_layout.tsx`:
```typescript
import { Stack } from 'expo-router';
import { ThemeProvider } from '../src/theme/ThemeContext';

export default function RootLayout() {
  return (
    <ThemeProvider>
      <Stack screenOptions={{ headerShown: false }} />
    </ThemeProvider>
  );
}
```

`mobile/app/(tabs)/_layout.tsx`:
```typescript
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../src/theme/ThemeContext';

export default function TabsLayout() {
  const { tokens } = useTheme();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: tokens.color.accent,
        tabBarInactiveTintColor: tokens.color.mutedDim,
        tabBarStyle: {
          height: 76,
          borderTopWidth: 1,
          borderTopColor: tokens.color.border,
          backgroundColor: tokens.color.surfaceDim,
          paddingHorizontal: 26,
          paddingBottom: 10,
        },
        tabBarLabelStyle: { fontSize: 10 },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '홈',
          tabBarIcon: ({ color, size }) => <Ionicons name="home" size={20} color={color} />,
        }}
      />
      <Tabs.Screen
        name="calendar"
        options={{
          title: '캘린더',
          tabBarIcon: ({ color }) => <Ionicons name="calendar-outline" size={20} color={color} />,
        }}
      />
      <Tabs.Screen
        name="ledger"
        options={{
          title: '가계부',
          tabBarIcon: ({ color }) => <Ionicons name="wallet-outline" size={20} color={color} />,
        }}
      />
    </Tabs>
  );
}
```

`mobile/app/(tabs)/calendar.tsx`:
```typescript
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';

export default function CalendarScreen() {
  const { tokens } = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <Text style={{ color: tokens.color.text }}>캘린더 — 다음 서브프로젝트에서 구현</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
```

`mobile/app/(tabs)/ledger.tsx`:
```typescript
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';

export default function LedgerScreen() {
  const { tokens } = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <Text style={{ color: tokens.color.text }}>가계부 — 다음 서브프로젝트에서 구현</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
```

`mobile/app/(tabs)/index.tsx` (Task 2에서 실제 홈 화면으로 교체 — 지금은 플레이스홀더):
```typescript
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';

export default function HomeScreen() {
  const { tokens } = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <Text style={{ color: tokens.color.text }}>홈 — Task 2에서 구현 예정</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
```

- [ ] **Step 8: 번들 검증**

Run: `cd mobile && npx expo export --platform web 2>&1 | tail -30`
Expected: 에러 없이 번들 생성 완료 (시뮬레이터/디바이스 없이도 번들러 통과 여부로 컴파일 정합성을 검증한다)

- [ ] **Step 9: 전체 테스트 실행**

Run: `cd mobile && npx jest`
Expected: PASS (tokens.test.ts 2개)

- [ ] **Step 10: Commit**

```bash
cd mobile
git add -A
git commit -m "feat: Expo 클라이언트 스캐폴딩 + Nocturne/Organic 테마 시스템 + 3탭 셸"
```

---

### Task 2: 홈 화면 — 평면도 + 요약 스트립

**Files:**
- Create: `mobile/src/domain/seedRooms.ts`
- Create: `mobile/src/domain/cleanliness.ts`
- Test: `mobile/src/domain/cleanliness.test.ts`
- Create: `mobile/src/domain/floorplan.ts`
- Test: `mobile/src/domain/floorplan.test.ts`
- Create: `mobile/src/components/home/RoomTile.tsx`
- Test: `mobile/src/components/home/RoomTile.test.tsx`
- Create: `mobile/src/components/home/SummaryStrip.tsx`
- Test: `mobile/src/components/home/SummaryStrip.test.tsx`
- Create: `mobile/src/components/home/FloorPlanCanvas.tsx`
- Test: `mobile/src/components/home/FloorPlanCanvas.test.tsx`
- Modify: `mobile/app/(tabs)/index.tsx` (Task 1의 플레이스홀더를 실제 화면으로 교체)

**Interfaces:**
- Consumes: `useTheme()` / `ThemeTokens` (Task 1)
- Produces: 이번 플랜의 마지막 화면 — 이후 서브프로젝트(캘린더/가계부 등)가 참고할 `Room`/`Chore` 타입과 `computeCleanliness()`를 정의한다.

- [ ] **Step 1: 시드 데이터 이식**

`docs/design-handoff/screens/floorplan-v2.dc.html`의 `DEFAULT_ROOMS`(방 10개, `T(label, who, cycle, how, yt)` 헬퍼로 정의된 할 일 18개)를 그대로 TypeScript로 옮긴다.

`mobile/src/domain/seedRooms.ts`:
```typescript
export interface Chore {
  label: string;
  who: '나' | '짝꿍';
  cycle: string;
  how: string[];
  yt: string;
}

export interface Room {
  id: string;
  name: string;
  x: number;
  y: number;
  w: number;
  h: number;
  z: number;
  chores: Chore[];
}

function chore(label: string, who: '나' | '짝꿍', cycle: string, how: string[], yt: string): Chore {
  return { label, who, cycle, how, yt };
}

export const DEFAULT_ROOMS: Room[] = [
  { id: 'kveranda', name: '주방 베란다', x: 26, y: 0, w: 36, h: 8, z: 2, chores: [
    chore('분리수거', '짝꿍', '주 2회', ['플라스틱은 라벨 떼고 한 번 헹구기', '종이 상자는 테이프·송장 제거', '금·일 저녁에 한 번에 배출'], '분리수거 제대로 하는 법'),
  ] },
  { id: 'kid', name: '아이방', x: 0, y: 8, w: 26, h: 30, z: 2, chores: [
    chore('장난감 정리', '짝꿍', '매일', ['바구니 3개로 종류별 분류', '아이와 5분 타이머 걸고 같이', '안 쓰는 건 상자에 격리 보관'], '아이방 장난감 수납'),
    chore('침구 정리', '짝꿍', '매일', ['이불은 발끝부터 반듯하게', '베개 털어 각 세우기', '주 1회 커버 세탁'], '침구 정리 습관'),
  ] },
  { id: 'kitchen', name: '주방', x: 26, y: 8, w: 36, h: 30, z: 2, chores: [
    chore('설거지', '나', '매일', ['기름기 없는 그릇부터 먼저', '수세미는 주 1회 교체', '마지막에 싱크볼까지 닦기'], '설거지 순서 팁'),
    chore('음식물 쓰레기', '짝꿍', '매일', ['물기를 최대한 짜기', '신문지로 한 번 감싸기', '저녁 산책 나갈 때 같이'], '음식물 쓰레기 냄새 잡기'),
    chore('가스레인지 닦기', '나', '주 2회', ['식은 뒤 베이킹소다 뿌리기', '5분 두고 마른 천으로 밀기', '틈새는 면봉으로 마무리'], '가스레인지 기름때 제거'),
  ] },
  { id: 'myroom', name: '컴퓨터방', x: 62, y: 8, w: 38, h: 30, z: 2, chores: [
    chore('책상 정리', '나', '매일', ['책상 위 물건을 전부 내리기', '자주 쓰는 것만 다시 올리기', '서류는 트레이 한 곳에'], '책상 정리 루틴'),
    chore('케이블 정리', '나', '매달', ['전원 뽑고 전부 분리', '케이블 타이로 묶기', '라벨 붙여 구분'], '책상 밑 케이블 정리'),
  ] },
  { id: 'bath', name: '공용욕실', x: 0, y: 38, w: 26, h: 15, z: 2, chores: [
    chore('변기 청소', '짝꿍', '주 1회', ['세정제 뿌리고 5분 방치', '솔로 테두리 안쪽부터', '물내림 버튼·손잡이 소독'], '변기 청소하는 법'),
    chore('세면대 닦기', '나', '주 2회', ['배수구 머리카락 먼저 제거', '구연산수로 물때 녹이기', '수전은 마른 천으로 광내기'], '세면대 물때 제거'),
  ] },
  { id: 'living', name: '거실', x: 26, y: 38, w: 74, h: 50, z: 1, chores: [
    chore('바닥 청소기', '짝꿍', '매일', ['바닥 물건 먼저 치우기', '창가에서 문 쪽으로 밀기', '소파 밑은 3초 더'], '빠른 바닥 청소'),
    chore('소파 위 옷 치우기', '나', '매일', ['입을 옷 / 빨래 두 더미로', '빨래는 바로 세탁기로', '5분 넘기지 않기'], '옷 쌓임 방지 정리'),
    chore('테이블 닦기', '나', '매일', ['컵·리모컨 제자리로', '물티슈 후 마른 천으로', '컵받침 깔아두기'], '거실 테이블 정리'),
  ] },
  { id: 'entry', name: '현관', x: 78, y: 38, w: 22, h: 14, z: 3, chores: [
    chore('신발 정리', '짝꿍', '주 1회', ['오늘 신은 것만 밖에 두기', '나머지는 신발장 안으로', '현관 바닥 물걸레'], '현관 신발 수납'),
  ] },
  { id: 'master', name: '안방', x: 0, y: 53, w: 26, h: 35, z: 1, chores: [
    chore('이불 정리', '나', '매일', ['일어나자마자 걷어 환기', '10분 뒤 반듯하게 펴기', '주 1회 이불 털기'], '침대 정리 30초'),
    chore('옷 정리', '짝꿍', '주 1회', ['의자 위 옷부터 처리', '계절 아닌 옷은 상단칸', '안 입는 옷 3벌 비우기'], '옷장 정리 방법'),
  ] },
  { id: 'mbath', name: '안방욕실', x: 0, y: 53, w: 15, h: 13, z: 3, chores: [
    chore('거울 닦기', '나', '주 1회', ['물 스프레이 후 스퀴지', '마른 극세사로 마무리', '세면대 튄 자국까지'], '거울 얼룩 없이 닦기'),
  ] },
  { id: 'lveranda', name: '거실 베란다', x: 0, y: 88, w: 100, h: 12, z: 2, chores: [
    chore('빨래 개기', '나', '매일', ['드라마 한 편 = 바구니 하나', '옷장 칸별로 쌓기', '갠 즉시 넣기'], '빨래 빨리 개는 법'),
  ] },
];
```

- [ ] **Step 2: 청결도 계산 실패하는 테스트 작성**

마스터 스펙 4.2장 공식: `먼지 = f( Σ 미완료 할 일마다 (1 + 지연일수 × 계수) )`. 계수는 0.15로 둔다(단순 선형, 값이 클수록 밀린 티가 빨리 남).

`mobile/src/domain/cleanliness.test.ts`:
```typescript
import { computeCleanliness } from './cleanliness';

describe('computeCleanliness', () => {
  it('미완료 할 일이 없으면 0을 반환한다 (완전히 깨끗함)', () => {
    expect(computeCleanliness([])).toBe(0);
  });

  it('미완료 할 일 1개, 지연 0일이면 1을 반환한다', () => {
    expect(computeCleanliness([{ overdueDays: 0 }])).toBe(1);
  });

  it('지연일수가 클수록 값이 커진다', () => {
    const score3days = computeCleanliness([{ overdueDays: 3 }]);
    const score10days = computeCleanliness([{ overdueDays: 10 }]);
    expect(score10days).toBeGreaterThan(score3days);
  });

  it('미완료 할 일이 여러 개면 합산된다', () => {
    const one = computeCleanliness([{ overdueDays: 2 }]);
    const two = computeCleanliness([{ overdueDays: 2 }, { overdueDays: 2 }]);
    expect(two).toBeCloseTo(one * 2, 5);
  });
});
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `cd mobile && npx jest src/domain/cleanliness.test.ts`
Expected: FAIL — `./cleanliness` 모듈을 찾을 수 없음

- [ ] **Step 4: 청결도 계산 구현**

`mobile/src/domain/cleanliness.ts`:
```typescript
export interface OverdueChore {
  overdueDays: number;
}

const DELAY_COEFFICIENT = 0.15;

/**
 * 마스터 스펙 4.2: 먼지 = f( Σ 미완료 할 일마다 (1 + 지연일수 × 계수) )
 * 반환값은 정규화되지 않은 raw dust score — UI 레이어에서 시각 강도로 매핑한다.
 */
export function computeCleanliness(overdueChores: OverdueChore[]): number {
  return overdueChores.reduce(
    (sum, chore) => sum + (1 + chore.overdueDays * DELAY_COEFFICIENT),
    0
  );
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/domain/cleanliness.test.ts`
Expected: PASS

- [ ] **Step 6: 방 좌표 clamp 함수 실패하는 테스트 작성**

마스터 스펙 4.1장: 편집 스텝 2%, `w ∈ [8, 100-x]`, `h ∈ [6, 100-y]`.

`mobile/src/domain/floorplan.test.ts`:
```typescript
import { clampRoomSize } from './floorplan';

describe('clampRoomSize', () => {
  it('너비는 최소 8, 최대 100-x로 clamp된다', () => {
    expect(clampRoomSize({ x: 90, y: 0, w: 50, h: 10 })).toEqual({ w: 10, h: 10 });
    expect(clampRoomSize({ x: 0, y: 0, w: 2, h: 10 })).toEqual({ w: 8, h: 10 });
  });

  it('높이는 최소 6, 최대 100-y로 clamp된다', () => {
    expect(clampRoomSize({ x: 0, y: 95, w: 10, h: 50 })).toEqual({ w: 10, h: 5 });
    expect(clampRoomSize({ x: 0, y: 0, w: 10, h: 2 })).toEqual({ w: 10, h: 6 });
  });
});
```

Note: `y: 95, h: 50` 케이스는 `h`의 상한 `100-y=5`가 하한 `6`보다 작은 경계 사례다 — 상한이 하한보다 작을 때는 상한을 우선한다(방이 캔버스를 벗어나지 않는 쪽이 항상 이긴다). 그래서 기대값은 `5`.

- [ ] **Step 7: 테스트 실행 → 실패 확인**

Run: `cd mobile && npx jest src/domain/floorplan.test.ts`
Expected: FAIL — `./floorplan` 모듈을 찾을 수 없음

- [ ] **Step 8: clamp 함수 구현**

`mobile/src/domain/floorplan.ts`:
```typescript
export interface RoomBounds {
  x: number;
  y: number;
  w: number;
  h: number;
}

function clamp(value: number, lo: number, hi: number): number {
  if (hi < lo) return hi;
  return Math.max(lo, Math.min(hi, value));
}

export function clampRoomSize({ x, y, w, h }: RoomBounds): { w: number; h: number } {
  return {
    w: clamp(w, 8, 100 - x),
    h: clamp(h, 6, 100 - y),
  };
}
```

- [ ] **Step 9: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/domain/floorplan.test.ts`
Expected: PASS

- [ ] **Step 10: RoomTile 컴포넌트 + 스모크 테스트**

퍼센트 좌표를 절대 위치 스타일로 변환해 방 이름과 미완료 개수를 표시한다. 청결도가 높을수록(먼지가 많을수록) 배경이 흐려지는 시각 강도를 `computeCleanliness` 결과로 결정한다.

`mobile/src/components/home/RoomTile.tsx`:
```typescript
import { View, Text, StyleSheet } from 'react-native';
import { Room } from '../../domain/seedRooms';
import { computeCleanliness } from '../../domain/cleanliness';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  room: Room;
  overdueDaysByChoreIndex: Record<number, number>;
}

export function RoomTile({ room, overdueDaysByChoreIndex }: Props) {
  const { tokens } = useTheme();
  const overdue = Object.values(overdueDaysByChoreIndex).map((overdueDays) => ({ overdueDays }));
  const dust = computeCleanliness(overdue);
  const isDirty = dust > 0;

  return (
    <View
      testID={`room-tile-${room.id}`}
      style={[
        styles.tile,
        {
          left: `${room.x}%`,
          top: `${room.y}%`,
          width: `${room.w}%`,
          height: `${room.h}%`,
          zIndex: room.z,
          backgroundColor: isDirty ? tokens.color.surfaceDim : tokens.color.surface,
          borderColor: tokens.color.border,
          borderRadius: tokens.radius.sm,
        },
      ]}
    >
      <Text style={{ color: tokens.color.text, fontSize: 12 }}>{room.name}</Text>
      {isDirty && (
        <Text style={{ color: tokens.color.mutedDim, fontSize: 10 }}>
          미완료 {overdue.length}건
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  tile: {
    position: 'absolute',
    borderWidth: 1,
    padding: 6,
  },
});
```

`mobile/src/components/home/RoomTile.test.tsx`:
```typescript
import { render, screen } from '@testing-library/react-native';
import { RoomTile } from './RoomTile';
import { ThemeProvider } from '../../theme/ThemeContext';
import { DEFAULT_ROOMS } from '../../domain/seedRooms';

describe('RoomTile', () => {
  it('방 이름을 렌더링한다', () => {
    render(
      <ThemeProvider>
        <RoomTile room={DEFAULT_ROOMS[0]} overdueDaysByChoreIndex={{}} />
      </ThemeProvider>
    );
    expect(screen.getByText(DEFAULT_ROOMS[0].name)).toBeTruthy();
  });

  it('미완료 할 일이 있으면 건수를 표시한다', () => {
    render(
      <ThemeProvider>
        <RoomTile room={DEFAULT_ROOMS[0]} overdueDaysByChoreIndex={{ 0: 2 }} />
      </ThemeProvider>
    );
    expect(screen.getByText('미완료 1건')).toBeTruthy();
  });
});
```

- [ ] **Step 11: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/components/home/RoomTile.test.tsx`
Expected: PASS

- [ ] **Step 12: SummaryStrip 컴포넌트 + 스모크 테스트**

README 화면 1 스펙: `📅 오늘 일정 n건 · 💰 이번 달 nn만원` (1행) + 위트 문구(2행). 문구는 프롭으로 주입받는다(문구 로직/랜덤 선택은 캘린더·가계부 데이터가 필요해 다음 서브프로젝트로 미룬다 — 지금은 고정 문구 하나로 시각적 뼈대만 만든다).

`mobile/src/components/home/SummaryStrip.tsx`:
```typescript
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  todayEventCount: number;
  monthlySpendManwon: number;
  message: string;
}

export function SummaryStrip({ todayEventCount, monthlySpendManwon, message }: Props) {
  const { tokens } = useTheme();
  return (
    <View
      style={[
        styles.strip,
        { backgroundColor: tokens.color.surfaceDim, borderColor: tokens.color.border, borderRadius: tokens.radius.lg },
      ]}
    >
      <Text style={{ color: tokens.color.muted, fontSize: 13 }}>
        📅 오늘 일정 {todayEventCount}건 · 💰 이번 달 {monthlySpendManwon}만원
      </Text>
      <Text style={{ color: tokens.color.mutedDim, fontSize: 13, lineHeight: 19, marginTop: 4 }}>
        {message}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: {
    marginHorizontal: 22,
    marginTop: 16,
    padding: 14,
    borderWidth: 1,
  },
});
```

`mobile/src/components/home/SummaryStrip.test.tsx`:
```typescript
import { render, screen } from '@testing-library/react-native';
import { SummaryStrip } from './SummaryStrip';
import { ThemeProvider } from '../../theme/ThemeContext';

describe('SummaryStrip', () => {
  it('일정 건수와 지출 금액, 문구를 렌더링한다', () => {
    render(
      <ThemeProvider>
        <SummaryStrip todayEventCount={3} monthlySpendManwon={82} message="테스트 문구" />
      </ThemeProvider>
    );
    expect(screen.getByText(/오늘 일정 3건/)).toBeTruthy();
    expect(screen.getByText(/이번 달 82만원/)).toBeTruthy();
    expect(screen.getByText('테스트 문구')).toBeTruthy();
  });
});
```

- [ ] **Step 13: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/components/home/SummaryStrip.test.tsx`
Expected: PASS

- [ ] **Step 14: FloorPlanCanvas 컴포넌트 + 스모크 테스트**

마스터 스펙 4.1장: 캔버스 높이 404px(Nocturne)/398px(Organic), 100×100 퍼센트 좌표계. `DEFAULT_ROOMS`를 순회하며 `RoomTile`을 배치한다.

`mobile/src/components/home/FloorPlanCanvas.tsx`:
```typescript
import { View, StyleSheet } from 'react-native';
import { Room } from '../../domain/seedRooms';
import { RoomTile } from './RoomTile';
import { useTheme } from '../../theme/ThemeContext';

interface Props {
  rooms: Room[];
}

export function FloorPlanCanvas({ rooms }: Props) {
  const { tokens, theme } = useTheme();
  const canvasHeight = theme === 'nocturne' ? 404 : 398;

  return (
    <View
      testID="floorplan-canvas"
      style={[
        styles.canvas,
        { height: canvasHeight, backgroundColor: tokens.color.bg, marginHorizontal: 22, marginTop: 16 },
      ]}
    >
      {rooms.map((room) => (
        <RoomTile key={room.id} room={room} overdueDaysByChoreIndex={{}} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'relative',
    width: '100%',
  },
});
```

`mobile/src/components/home/FloorPlanCanvas.test.tsx`:
```typescript
import { render, screen } from '@testing-library/react-native';
import { FloorPlanCanvas } from './FloorPlanCanvas';
import { ThemeProvider } from '../../theme/ThemeContext';
import { DEFAULT_ROOMS } from '../../domain/seedRooms';

describe('FloorPlanCanvas', () => {
  it('DEFAULT_ROOMS의 방 10개를 모두 렌더링한다', () => {
    render(
      <ThemeProvider>
        <FloorPlanCanvas rooms={DEFAULT_ROOMS} />
      </ThemeProvider>
    );
    DEFAULT_ROOMS.forEach((room) => {
      expect(screen.getByText(room.name)).toBeTruthy();
    });
  });
});
```

- [ ] **Step 15: 테스트 실행 → 통과 확인**

Run: `cd mobile && npx jest src/components/home/FloorPlanCanvas.test.tsx`
Expected: PASS

- [ ] **Step 16: 홈 화면 조립**

Task 1의 플레이스홀더를 실제 화면으로 교체한다.

`mobile/app/(tabs)/index.tsx`:
```typescript
import { ScrollView, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme/ThemeContext';
import { SummaryStrip } from '../../src/components/home/SummaryStrip';
import { FloorPlanCanvas } from '../../src/components/home/FloorPlanCanvas';
import { DEFAULT_ROOMS } from '../../src/domain/seedRooms';

export default function HomeScreen() {
  const { tokens } = useTheme();
  return (
    <ScrollView style={[styles.container, { backgroundColor: tokens.color.bg }]}>
      <SummaryStrip
        todayEventCount={3}
        monthlySpendManwon={82}
        message="공용욕실이 3일째 조용히 삐져 있어. 오늘 5분만 쓰면 풀려."
      />
      <FloorPlanCanvas rooms={DEFAULT_ROOMS} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
});
```

- [ ] **Step 17: 번들 검증**

Run: `cd mobile && npx expo export --platform web 2>&1 | tail -30`
Expected: 에러 없이 번들 생성 완료

- [ ] **Step 18: 전체 테스트 실행**

Run: `cd mobile && npx jest`
Expected: PASS (Task 1의 2개 + Task 2의 9개 = 11개 전체 통과)

- [ ] **Step 19: Commit**

```bash
cd mobile
git add -A
git commit -m "feat: 홈 화면 — 평면도 캔버스 + 요약 스트립 + 청결도 계산"
```

## 다음 단계

- 캘린더 화면(2a: 월 그리드 + 일 상세 + 일정 생성, 이벤트 바 레인 알고리즘 포함) — `docs/design-handoff/README.md` 화면 2 절
- 가계부 메인/지출입력/정산 — README 화면 3~5
- 반복 규칙 편집기, 할 일 편집, 가전 유지보수, 프로필·설정(테마 선택 UI), 알림 — README 화면 6~10
- 완료 애니메이션(링 반짝임 900ms + 닦임 스윕 800ms) — `docs/design-handoff/spec/floorplan-handoff-v1.md`
- 평면도 편집 모드(방 추가/삭제/리사이즈)
- 백엔드 API 연동 시점에 목업 데이터를 실제 데이터로 교체

## Self-Review

**Spec coverage:** 설계 문서의 이번 범위(스캐폴딩/테마/홈 화면)를 Task 1(스캐폴딩+테마+3탭)과 Task 2(홈 화면 전체 구성요소)가 커버한다. 캘린더 이하 화면은 설계 문서 자체가 "다음 서브프로젝트"로 명시했으므로 "다음 단계" 절로 이관, 플랜 범위 아님이 Global Constraints에도 명시됨.

**Placeholder scan:** 전 스텝 실제 코드/커맨드 포함. `calendar.tsx`/`ledger.tsx`의 "다음 서브프로젝트에서 구현" 텍스트는 TODO 주석이 아니라 사용자에게 보이는 실제 UI 카피이므로 플레이스홀더 규칙 위반이 아니다.

**Type consistency:** `Room`/`Chore`(Task 2 Step 1)가 `RoomTile`/`FloorPlanCanvas`(Step 10, 14)에서 동일하게 사용됨. `ThemeTokens`/`useTheme()`(Task 1)이 Task 2 전 컴포넌트에서 동일 시그니처로 사용됨. `clampRoomSize`/`computeCleanliness`는 정의된 곳 외에는 아직 소비자가 없음(향후 편집 모드/방 타일 시각 강도 고도화에서 사용 예정 — 이번 태스크 범위에서는 순수 함수 자체의 정합성만 테스트로 보장).
