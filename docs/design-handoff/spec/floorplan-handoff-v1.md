# Handoff: 집안일 평면도 앱 (Home Chores — Floorplan App)

## Overview
우리집 평면도를 첫 화면으로 쓰는 부부용 집안일 관리 모바일 앱. 방마다 할 일이 남아 있으면 그 방이 "더러워 보이고"(먼지 텍스처 + 미완료 개수 배지), 할 일을 완료하면 먼지가 걷히며 짧은 반짝임/닦임 애니메이션이 재생된다. 사용자는 평면도 레이아웃(방 이름·위치·크기·추가/삭제)을 앱 안에서 직접 편집할 수 있고, 각 할 일에는 "하는 방법" 단계와 관련 유튜브 검색 링크가 붙는다.

Target user: 부부/파트너 2인 가구 (담당자는 `나` / `짝꿍`). Tone: 살짝 유머러스한 한국어 반말·구어체 카피.

## About the Design Files
이 폴더의 HTML 파일들은 **디자인 레퍼런스**다 — 의도한 외형과 동작을 보여주는 프로토타입이지, 그대로 가져다 쓰는 프로덕션 코드가 아니다. 작업은 이 HTML 디자인을 **대상 코드베이스의 기존 환경(React Native / React / Flutter / SwiftUI 등)에서 그 프로젝트의 관례와 라이브러리로 재구현**하는 것이다. 아직 환경이 없다면 프로젝트에 가장 적합한 프레임워크를 고르고 거기서 구현한다 (모바일 앱이 목표이므로 React Native 또는 Flutter 권장).

HTML 파일은 사내 프로토타이핑 런타임(`support.js`)을 쓴다. 그 런타임 자체를 이식할 필요는 없다 — 화면, 상태, 상호작용만 참고하면 된다.

## Fidelity
**High-fidelity (hifi).** 색상·타이포·간격·반경·애니메이션 타이밍이 전부 확정값이다. 단, 두 가지 **시각 방향(theme)** 이 병렬로 존재한다:

- **2a — Nocturne**: 어두운 밤 모드. 깨끗한 방이 보라빛으로 빛난다.
- **2b — Organic**: 따뜻한 크림/테라코타. 더러운 방은 흙빛, 깨끗한 방은 세이지 그린.

두 방향은 **레이아웃·정보구조·인터랙션이 완전히 동일**하고 토큰만 다르다. 구현 시 하나를 고르거나, 테마 토큰 레이어로 두 개를 모두 지원할 수 있게 만들면 된다 (권장: 토큰 맵 1개 + 테마 2개).

---

## Screens / Views

탭 바 4개: **평면도 · 오늘·일정 · 할일 · 기록**

### 1. 평면도 (Home / Floorplan) — 기본 화면
**Purpose**: 집 전체의 청결 상태를 한눈에 보고, 방을 눌러 그 방의 할 일을 처리한다.

**Layout** (390×844 기준):
- 상태바 46px
- 헤더: 좌측 kicker(11px, letter-spacing .14em, uppercase) + 타이틀(Nocturne 26px / Organic 27px), 우측 `평면도 편집` pill 버튼
- 진행 바: 높이 4px(Nocturne) / 10px(Organic), 우측에 `5/18` 카운터(tabular-nums), 아래 한 줄 quip 카피
- 평면도 캔버스: `margin: 14px 18px 0`(N) / `16px 20px 0`(O), height **404px**(N) / **398px**(O), `position: relative`
- 탭 바: 하단 고정

**평면도 캔버스 좌표계**: 캔버스는 100×100 퍼센트 좌표계. 각 방은 `position:absolute; left:x%; top:y%; width:w%; height:h%`. 기본 방 정의(사용자 집 기준):

| id | 이름 | x | y | w | h | z |
|---|---|---|---|---|---|---|
| kveranda | 주방 배란다 | 26 | 0 | 36 | 8 | 2 |
| kid | 아이방 | 0 | 8 | 26 | 30 | 2 |
| kitchen | 주방 | 26 | 8 | 36 | 30 | 2 |
| myroom | 컴퓨터방 | 62 | 8 | 38 | 30 | 2 |
| bath | 공용욕실 | 0 | 38 | 26 | 15 | 2 |
| living | 거실 | 26 | 38 | 74 | 50 | 1 |
| entry | 현관 | 78 | 38 | 22 | 14 | 3 |
| master | 안방 | 0 | 53 | 26 | 35 | 1 |
| mbath | 안방욕실 | 0 | 53 | 15 | 13 | 3 |
| lveranda | 거실 배란다 | 0 | 88 | 100 | 12 | 2 |

`현관`은 `거실` 위에, `안방욕실`은 `안방` 위에 겹쳐 올려서 L자 공간을 사각형 두 개로 표현한다 (z-index로 처리).

**방 타일 구성** (레이어 순서, 아래→위):
1. 베이스: 더러운 상태 배경 + 테두리
2. 깨끗 오버레이: `opacity: isClean ? 1 : 0` — 깨끗한 색 배경 + 테두리 (+ Nocturne은 accent glow)
3. 먼지 도트: `background-image: radial-gradient(<dustColor> 1.1px, transparent 1.5px); background-size: 9px 9px`(N) / `1.3px/1.7px, 10px 10px`(O), `opacity: min(0.85, 0.3 + 미완료수 × 0.2)`
4. 얼룩 그라디언트: `radial-gradient(60% 70% at 30% 80%, rgba(89,93,108,.55), transparent 70%)`(N) / `radial-gradient(65% 75% at 32% 78%, rgba(130,121,106,.45), transparent 72%)`(O), 같은 opacity
5. 완료 애니메이션 레이어 (아래 Interactions 참고)
6. 라벨 행: 좌하단 `left:8px; bottom:7px`(N) / `10px/8px`(O), `display:flex; flex-wrap:wrap; gap:5px; align-items:center` — 방 이름(`white-space:nowrap`, 방마다 폰트 크기 9–13px) + 미완료 개수 배지 + 깨끗 표식

**방 이름 폰트 크기**: 거실 13px, 아이방/주방/컴퓨터방/안방 12px, 공용욕실 11px, 주방배란다/현관/거실배란다 10px, 안방욕실 9px. (새로 추가한 방은 11px)

**미완료 배지**: 원형 pill, min-width 16px/height 16px(N) · 18/18(O), font-size 10px, weight 600(N)/700(O).
**깨끗 표식**: Nocturne = Phosphor `ph-fill ph-sparkle` 12px accent-400 / Organic = 8px 원, sage(#7a8a5e).

### 2. 방 팝오버 (Room popover)
방을 탭하면 그 방 옆에 말풍선 카드가 뜬다.
- width 222px(N) / 232px(O), radius 14px(N) / 28px(O)
- 위치: `left: calc(clamp(방중심x, 26, 74)% - 116px)`; 방 하단이 캔버스 56% 위면 방 아래(`top: calc((y+h)% + 10px)`), 아니면 방 위(`top: calc(y% - (할일수×40 + 92)px)`)
- 등장: `@keyframes pop { from { opacity:0; transform: translateY(6px) scale(.96) } to { opacity:1; transform:none } }`, 160ms ease-out
- 헤더: 방 이름 + 닫기(×)
- 할 일 행: 체크 원/사각 + 라벨(완료 시 `line-through` + dim 색) + ⓘ 버튼(→ 할일 상세로 이동)
- 하단 힌트 문구: 남은 개수에 따라 `"n개 남았어요. ⓘ 를 누르면 방법이 나와요."` / `"완벽합니다. 잠시 감상하세요."` / `"아직 등록된 할 일이 없어요."`

### 3. 평면도 편집 모드 (Floorplan edit)
헤더의 `평면도 편집` 버튼으로 진입. 다른 사용자가 자기 집 구조로 바꿀 수 있게 하는 것이 목적.

편집 모드에서:
- kicker → `평면도 편집`, 타이틀 → `우리집 평면도 고치기`, 버튼 → `완료`(채워진 상태)
- 진행 바/quip 숨김, 대신 안내문 `"방을 눌러 고르고, 아래에서 이름·위치·크기를 바꾸세요."`
- 방 배지/깨끗 표식 숨김, 모든 방 테두리를 편집용으로 (N: `inset 0 0 0 1px #595d6c`, O: `inset 0 0 0 2px #a19786`)
- 방 탭 = 선택. 선택된 방은 `inset 0 0 0 2px #b5abfc, 0 0 18px rgba(145,132,217,.45)`(N) / `inset 0 0 0 3px #c67139`(O), z-index 9
- 하단 편집 패널 (`position:absolute; left/right 14–16px; bottom 92px; z-index 60`, `@keyframes slideup` 180ms):
  - 방 이름 텍스트 입력 + 삭제 버튼
  - `위치` ← ↑ ↓ → : 각 **2% 스텝**, `x`는 `[0, 100-w]`, `y`는 `[0, 100-h]` 로 클램프
  - `크기` 가로 −/＋, 세로 −/＋ : 각 2% 스텝, `w ∈ [8, 100-x]`, `h ∈ [6, 100-y]`
  - `방 추가` : 새 방 `{name:'새 방', x:34, y:42, w:24, h:16, z:5, tasks:[]}` 생성 후 자동 선택
  - `되돌리기` : 기본 평면도로 복원
- 선택된 방이 없으면 패널에 `"수정할 방을 위에서 골라주세요."`

> 프로덕션 권장: 화살표 버튼 대신/추가로 **드래그 + 리사이즈 핸들**을 지원할 것. 퍼센트 좌표계 + 2% 스냅 그리드는 그대로 유지하면 데이터 모델이 호환된다.

### 4. 오늘·일정 (Plan)
하나의 탭 안에서 세그먼트 버튼 `오늘` / `일정` 으로 전환. 타이틀도 같이 바뀐다 (`오늘 할 일` / `이번 주`).

- **오늘**: `n개 끝, m개 남음` 서브텍스트 + 전체 할 일 리스트(미완료가 위로 정렬). 행 = 체크 + 라벨 + `방 이름 · 담당` + `›`. 행 본문 탭 → 할일 상세.
- **일정**: 7일 주간 스트립 (일~토, 오늘=수·5일 강조, 하단 점 = 그날 할 일 밀도: 없음/보통/많음) + 그 아래 **반복 집안일** 리스트 (주기가 `매일`이 아닌 모든 할 일을 자동 수집: 라벨 / `주기 · 방 이름` / 담당 칩). 항목 탭 → 할일 상세.

### 5. 할일 (Task management)
**리스트 모드**: 방별로 그룹핑된 전체 할 일. 그룹 헤더 = 방 이름(11px uppercase accent), 행 = 라벨 + `주기 · 담당` + `›`.

**상세 모드**: 
- 뒤로가기 `‹ 방 이름`
- 할 일 제목 (25px N / 28px O)
- 담당 칩 + 주기 칩
- `하는 방법` — 번호 원(1,2,3) + 단계 텍스트 (14px N / 15px O, line-height 1.5, `text-wrap: pretty`)
- 유튜브 카드 — `https://www.youtube.com/results?search_query=<encodeURIComponent(검색어)>` 로 새 탭 열기. 프로덕션에서는 검색어 대신 **사용자가 저장한 실제 영상 URL** 필드를 갖게 하고, 인앱 웹뷰 또는 유튜브 앱 딥링크로 열 것.
- `완료로 표시` / `완료 취소` 버튼 (평면도 상태와 동일한 소스에 반영 → 누르면 그 방이 깨끗해진다)

> 프로덕션 권장: 이 화면은 읽기 전용 목업이지만 실제로는 **편집 가능**해야 한다 — 할 일 추가/삭제, 방 배정, 담당자, 주기(매일/주 n회/매주 요일/매달), 방법 단계 편집, 영상 URL 입력.

### 6. 기록 (Log / Stats)
- 상단 2열 카드: `나` 58건 / `짝꿍` 61건 (`근소한 승` 카피)
- `방별 청결 유지율` — 방 이름 + 퍼센트 + 진행 바 (현재 목업은 상위 6개 방에 58/48/64/35/73/82 고정값)

---

## Interactions & Behavior

- **방 탭** (일반 모드): 팝오버 열기/닫기 토글. 이미 열린 방을 다시 누르면 닫힘.
- **방 탭** (편집 모드): 선택.
- **체크 토글**: 해당 할 일 완료 상태 반전 → 방의 미완료 수 재계산 → 먼지 opacity 변경 (CSS transition 없이 즉시; 프로덕션에서는 `opacity` 250ms ease 권장).
- **방이 0개 미완료가 되는 순간** — 완료 애니메이션:
  - 링 반짝임: `@keyframes sprk { 0% {opacity:0; transform:scale(.55)} 35% {opacity:1} 100% {opacity:0; transform:scale(1.45)} }`, **900ms ease-out forwards**, `box-shadow: inset 0 0 22px rgba(181,171,252,.85)`(N) / `inset 0 0 26px rgba(198,113,57,.75)`(O)
  - 닦임 스윕: `@keyframes wipe { from { transform: translateX(-130%) skewX(-12deg) } to { transform: translateX(130%) skewX(-12deg) } }`, **800ms ease-in-out**, 폭 40–42%의 선형 그라디언트 밴드
  - 상태의 `spark` 값은 **950ms 후 자동 해제** (타이머는 방 전환 시 clearTimeout)
- **탭 전환**: 즉시, 트랜지션 없음.
- **할일 상세 진입**: 팝오버/오늘 리스트/반복 리스트 어디서든 진입 가능하며, 진입 시 탭이 `할일`로 바뀌고 팝오버는 닫힌다.
- **링크**: 유튜브 카드만 외부 링크 (`target="_blank"`).
- **호버/포커스**: 목업에는 없음. 프로덕션에서는 각 디자인 시스템 규칙을 따를 것 — 두 시스템 모두 `:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }`, hover/pressed는 accent ramp 한 단계.

## State Management

프레임(테마)마다 독립된 상태 세트를 갖는다. 프로덕션에서는 하나만 있으면 된다.

```
rooms: Room[]            // 평면도 정의 — 사용자가 편집 가능, 영속 저장 필요
done:  { [taskKey]: bool } // taskKey = `${roomId}:${taskIndex}`; 하루 단위로 리셋되는 완료 상태
tab:   'home' | 'plan' | 'tasks' | 'log'
plan:  'today' | 'week'          // 오늘·일정 세그먼트
open:  roomId | null             // 열린 팝오버
det:   `${roomId}:${i}` | null   // 할일 상세 대상
edit:  boolean                   // 평면도 편집 모드
sel:   roomId | null             // 편집 모드에서 선택된 방
spark: roomId | null             // 완료 애니메이션 재생 중인 방 (950ms 후 null)
```

```
Room  = { id, name, x, y, w, h, z, fs, tasks: Task[] }
Task  = { label, who: '나'|'짝꿍', cycle: '매일'|'주 n회'|'주 1회'|'매달', how: string[], yt: string }
```

파생값 (매 렌더 계산): 방별 미완료 수, 전체 done/total/퍼센트, 미완료 우선 정렬된 오늘 리스트, 방별 그룹, `cycle !== '매일'`인 반복 리스트.

**데이터 요구사항 (프로덕션)**: 평면도와 할 일 정의는 가구 단위로 서버/로컬에 영속 저장하고 두 사용자 간 실시간 동기화가 필요하다. `done`은 날짜 키를 갖는 별도 테이블(완료자·완료시각 기록 → 기록 탭 통계의 소스). 반복 주기에 따라 매일 자정에 해당 날짜의 할 일 인스턴스를 생성하는 스케줄러가 필요하다.

**시드 데이터**: 기본 10개 방과 18개 할 일(방법 단계 3개 + 유튜브 검색어 포함)이 `집안일 평면도 앱 v2.dc.html` 로직 상단 `DEFAULT_ROOMS` 에 그대로 들어 있다. 그대로 옮겨 쓰면 된다.

---

## Design Tokens

두 시스템의 원본 토큰 시트를 `design-systems/` 에 포함했다. 아래는 실제로 쓰인 값만 추린 것.

### Nocturne (2a)
```
bg #161826 · surface #232532 · text #e9e9ed · accent #9184d9
neutral 100 #f3f5fe · 200 #e4e7f5 · 300 #cfd3e5 · 400 #b2b6ca · 500 #9397ab
        600 #75798c · 700 #595d6c · 800 #3f424d · 900 #292b31
accent  200 #e7e5fe · 300 #d2cefd · 400 #b5abfc · 500 #968ae0
        600 #796cbf · 700 #5d5294 · 800 #423a6a · 900 #2b2741
font: Inter 400/500/600 (headings weight 500 — 더 굵게 쓰지 말 것)
radius: sm 4 · md 8 · lg 14
shadow: sm `0 0 0 1px #3f424d` · md `0 0 0 1px #595d6c, 0 6px 18px rgba(0,0,0,.55)`
        lg `0 0 0 1px #9397ab, 0 16px 40px rgba(0,0,0,.65)`
spacing (density .70×): 2.8 / 5.6 / 8.4 / 11.2 / 16.8 / 22.4
icons: Phosphor (ph-house, ph-calendar-check, ph-list-checks, ph-chart-bar, ph-sparkle,
       ph-info, ph-caret-left/right, ph-arrow-*, ph-trash, ph-youtube-logo, ph-arrows-clockwise)
규칙: primary 액션은 accent 아웃라인(채우지 않음), accent는 선과 글로우로만, 순수 흑백 금지.
```

### Organic (2b)
```
bg #f5ead8 · surface #ebddc5 · text #201e1d · accent #c67139 · accent-2 (sage) #7a8a5e
neutral 100 #f9f4ed · 200 #eee7db · 300 #dcd3c4 · 400 #c0b6a5 · 500 #a19786
        600 #82796a · 700 #645c50 · 800 #474238 · 900 #2e2b25
accent  100 #fff2eb · 200 #ffe1d0 · 600 #b2622d · 700 #8c491a
accent-2 200 #e1eecc · 400 #aebf92 · 700 #56633f · 800 #3d472b
popover surface: #fff9ef
font: Caprasimo 400 (headings) / Figtree 400·600·700 (body)
radius: sm 8 · md 16 · lg 28 · 버튼·칩·행은 999px pill
shadow: sm `0 1px 2px rgba(46,43,37,.14)` · md `0 3px 10px rgba(46,43,37,.16)`
        lg `0 12px 32px rgba(46,43,37,.22)`
spacing (density 1.10×): 4.4 / 8.8 / 13.2 / 17.6 / 26.4 / 35.2
icons: Lucide, stroke-width 2.75 (목업은 아이콘 대신 텍스트/도형 사용 — 구현 시 Lucide로 대체)
규칙: 날카로운 모서리 금지, 회색으로 탈색시키지 말 것, sage는 보조 색이 아니라 두 번째 목소리로.
```

### 공통 레이아웃 값
```
기기 프레임 390 × 844, radius 44
상태바 46px
탭 바: Nocturne = 하단 고정 82px, 상단 1px #292b31 라인 / Organic = 플로팅 pill, left/right 14, bottom 14, height 64
평면도 캔버스: 404px(N) / 398px(O), 100×100% 좌표계, 편집 스텝 2%
```

## Assets
- `uploads/floorplan-1785716803991.jpg` — 사용자가 제공한 실제 아파트 평면도 원본 (네이버 부동산 캡처, 워터마크 포함). **디자인에는 쓰이지 않았다** — 평면도는 좌표 데이터로 재구성했다. 방 위치 검증용 참고 자료로만 포함.
- 아이콘은 전부 오픈소스 아이콘 세트(Phosphor / Lucide)에서 가져온다. 별도 커스텀 아이콘 없음.
- 폰트: Google Fonts (Inter, Caprasimo, Figtree).

## Files
- `집안일 평면도 앱 v2.dc.html` — **최신 디자인**. 2a Nocturne / 2b Organic 두 프레임, 편집 모드 + 통합 오늘·일정 + 할일 관리 포함. 브라우저에서 바로 열면 동작한다.
- `집안일 평면도 앱.dc.html` — 첫 초안 (탭 4개: 평면도/오늘/일정/기록, 편집 모드 없음). 히스토리 참고용.
- `support.js` — 프로토타입 런타임. 두 HTML 파일이 이걸 로드한다. **이식 대상 아님.**
- `design-systems/nocturne/{styles.css, readme.md}` · `design-systems/organic/{styles.css, readme.md}` — 원본 디자인 시스템 토큰과 가이드.
- `uploads/floorplan-1785716803991.jpg` — 원본 평면도 사진.
