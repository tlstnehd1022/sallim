# Handoff: sallim (살림)

집안일 관리 앱을 **살림 = 집안일 + 일정 + 가계**로 확장한 디자인 핸드오프.
repo/project name: `sallim`.

## 이 폴더 구성

```
README.md                              ← 이 문서 (구현 지시서)
screens/sallim-screens.dc.html         ← 최신 디자인 (살림 전체 화면). 브라우저에서 바로 열림
screens/floorplan-v2.dc.html           ← 평면도 홈 화면 프로토타입 (Nocturne/Organic 2프레임)
support.js                             ← 프로토타입 런타임. 이식 대상 아님
spec/sallim-master-spec.md             ← 사용자 원본 마스터 스펙 (진실의 원천)
spec/foundation-scaffolding-design.md  ← Household/Member 도메인 설계
spec/floorplan-handoff-v1.md           ← 평면도 홈 화면 상세 핸드오프 (이전 라운드, 여전히 유효)
reference/timetree-*.jpg               ← 캘린더 참고 스크린샷 (사용자 제공)
design-systems/{nocturne,organic}/     ← 원본 토큰 시트 + 가이드
```

**평면도 홈 화면(방 좌표, 먼지 레이어, 완료 애니메이션, 편집 모드, 할 일 상세)의 픽셀 단위 명세는
`spec/floorplan-handoff-v1.md` 에, 동작하는 프로토타입은 `screens/floorplan-v2.dc.html` 에 있다.**
이 문서는 그 위에 추가된 캘린더 · 가계부 · 공통 셸을 다룬다.

## 디자인 파일의 성격

`screens/*.dc.html` 는 **디자인 레퍼런스**다. 사내 프로토타이핑 런타임(`support.js`) 위에서 돌아가는
프로토타입이며 프로덕션 코드가 아니다. 할 일은 이 화면·상태·인터랙션을 **대상 스택에서 재구현**하는 것.
모바일 앱이 목표이므로 React Native 또는 Flutter 권장. 런타임 자체는 이식하지 않는다.

파일은 캔버스 형태로, 턴별 섹션이 위에서부터 최신순으로 쌓여 있다:

- **2a — 캘린더 v2** (최신, 타임트리 참고): 월 그리드 / 일 상세 / FAB 확장 / 일정 만들기
- **1a — Nocturne 전체 세트 10화면**: 홈, 캘린더(구안), 가계부 메인, 지출 입력, 정산·분담, 반복 규칙 편집기, 할 일 편집, 가전 유지보수, 프로필·설정, 알림
- **1b — Organic** 같은 정보구조 5화면 (웜 테마 확인용)
- **1c — 2안 비교**: 가계부 "하루 페이스" / 캘린더 "주 스트립 + 아젠다" (미채택, 참고용)

**충돌 시 우선순위: 2a > 1a > 1b/1c.**

## Fidelity

**High-fidelity.** 색·간격·타이포·반경 전부 확정값. 단 **테마 두 개가 병렬로 존재**한다:

- **Nocturne** — 다크. 기본이자 1순위 구현 대상. 모든 화면이 이 테마로 존재한다.
- **Organic** — 웜 크림/테라코타. 정보구조 동일, 토큰만 다름. 5화면만 그려져 있다.

권장: **토큰 맵 1개 + 테마 2개**. 프로필·설정 화면에 테마 선택 UI가 이미 있다.

---

## 공통 셸

### 하단 탭 — 3개
`홈 · 캘린더 · 가계부` (이 순서). 알림은 탭이 아니라 각 화면 헤더의 종 아이콘/프로필 진입으로 들어간다.

- height 76px, `border-top: 1px solid #2e3040`, `background: #191b28`, `padding: 0 26px 10px`
- 아이콘 20px + 라벨 10px, 활성 = `ph-fill` + accent `#9184d9`, 비활성 = `ph` + `#6d7183`
- 아이콘: `ph-house` / `ph-calendar-blank` / `ph-wallet`

### 기기 프레임
390 × 844, radius 44. 상태바 46px. 헤더 패턴: kicker(11px, letter-spacing .14em, uppercase, accent) + 타이틀(25px weight 500).

### FAB
`right: 20px; bottom: 96px; 54×54; border-radius: 999px`
`background: rgba(145,132,217,.16); border: 1px solid #9184d9; box-shadow: 0 0 24px rgba(145,132,217,.3)`
탭하면 확장: 화면 전체에 `rgba(11,12,20,.72)` 오버레이 + FAB 위로 pill 액션 2개가 위로 쌓인다.

**액션 2개: `집안일 추가` / `일정 추가`.** (AI 문자·영수증 스캔은 제외 — 나중 단계)
pill: `padding: 13px 18px; radius 999px; background #1c1e2c`, 첫 액션만 `border: 1px solid rgba(145,132,217,.45)`,
나머지는 `#2e3040`. 라벨 13px + 아이콘 17px accent. 확장 상태에서 FAB는 `×`(`#232532`/`#3a3d55`)로 변한다.

---

## 화면 1: 홈 (평면도 + 요약 스트립)

평면도 본체는 `spec/floorplan-handoff-v1.md` 그대로. 살림 확장으로 **위에 요약 스트립이 추가**됐다.

- 위치: 헤더 아래, 진행 바 위. `margin: 16px 22px 0; padding: 12px 14px; radius 14px`
- `background: #1c1e2c; border: 1px solid #2e3040`
- 1행 (13px, `#c9c9d4`): `📅 오늘 일정 n건 · 💰 이번 달 nn만원` — 아이콘 `ph-calendar-dot` / `ph-wallet`, accent 색, 구분점 `#3a3d4e`
- 2행 (13px, `#9397ab`, line-height 1.45): **위트 있는 한 줄**. 상태 기반으로 서버/클라이언트가 문구를 고른다.

위트 문구 규칙 — 반말 구어체, 비난하지 않고, 20~40자, 행동 하나를 암시:
```
공용욕실이 3일째 조용히 삐져 있어. 오늘 5분만 쓰면 풀려.
이번 주는 둘 다 잘했어. 거실이 2주 연속 깨끗해.
지출은 예산선 아래야. 이대로 가면 이번 달은 편해.
```
문구 세트는 상태 버킷(밀린 방 있음 / 전부 깨끗 / 예산 초과 / 예산 여유 / 기념일 임박)별로 3~5개씩 두고 랜덤 선택.
디자인 파일에서는 이 문구가 tweakable prop(`stripMessage`)으로 노출되어 있다.

---

## 화면 2: 캘린더 (2a — 이게 최종안)

참고: `reference/timetree-*.jpg`. 타임트리에서 **가져온 것**과 **일부러 안 가져온 것**이 있다.

가져온 것: 제목이 보이는 이벤트 바(여러 날 걸침), 날짜 밑 음력, 주말/공휴일 색 구분, 공유 캘린더 필터 칩,
날짜 탭 → 일 상세, FAB 확장, 옵션 칩으로 필드를 늘리는 일정 생성 폼.
안 가져온 것: 광고 슬롯, 이모지 캘린더 이름, AI 이미지 스캔.

### 2-1. 월 그리드

헤더: `2026년 10월 ⌄` (21px weight 500) + 우측 검색/필터 원형 버튼 32px (`border 1px #2e3040`).

**캘린더 필터 칩 행** (`margin: 14px 20px 0; gap 7px`):
- 선택된 칩: `padding: 6px 12px 6px 7px; radius 999px; background rgba(145,132,217,.14); border 1px solid rgba(145,132,217,.45)`, 20px 원형 아바타 + 체크, 라벨 12px `#ded9ff`
- 미선택 칩: `border 1px #2e3040`, 아이콘 13px + 라벨 12px `#8b8fa3`
- 행 끝에 접기 `ph-caret-up` (`#5c5f75`)

**요일 헤더**: 10px. 일 `#c98b8b`, 토 `#8fa8d9`, 나머지 `#6d7183`.

**주 행** (`margin: 6px 12px 0`, 6주 고정):
- 각 주: `border-top: 1px solid #22242f; padding: 5px 0`, `min-height = 66 + 레인수 × 13 px`
- 날짜 셀: 7열 그리드, 숫자 14px weight 500, 그 아래 음력 8px `#4d5164`
- 색: 이번 달 밖 `#3a3d4a`, 일요일·공휴일 `#c98b8b`, 토요일 `#8fa8d9`, 평일 `#e9e9ed`
- 선택된 날: 셀 배경 `rgba(145,132,217,.14)`, radius 7px
- 날짜 셀 탭 → 그 날짜 선택 (일 상세 시트가 그 날짜로 갱신)

**이벤트 바 (핵심)**:
- 날짜 행 아래 레인(row)들이 쌓인다. 레인 = `display:grid; grid-template-columns: repeat(7,1fr); gap 1px`
- 바 = `grid-column: <시작열> / span <일수>`, `radius 3px; padding 2px 4px; font-size 9px; weight 500`,
  `white-space: nowrap; overflow: hidden; text-overflow: ellipsis`
- **레인 배치는 그리디 알고리즘**: 주 안에서 시작열 오름차순(같으면 긴 것 먼저)으로 정렬하고,
  마지막 바의 끝 열이 새 바의 시작열보다 작은 첫 레인에 넣는다. 없으면 새 레인 생성.
  여러 날 걸치는 이벤트는 주 경계에서 잘라 각 주에 별도 세그먼트로 배치한다.
- 바 색은 **kind별**: `집안일 = #9184d9` / `가족·개인 일정 = #7f92c9` / `공휴일 = #b57f9e` / `정기결제 = rgba(86,90,123,.34)`
- 바 글자색: 정기결제만 `#b0b3c6`, 나머지는 어두운 `#151622` (accent 위에 얹히므로)

**음력**: 프로토타입은 2026-10-01 = 음력 8/21 기준의 선형 근사를 쓴다.
**프로덕션은 실제 음력 변환 라이브러리를 써야 한다** (KASI 한국천문연구원 API 또는 `lunar-javascript` 등).
음력 생일 반복은 한국 사용자에게 필수 기능이다 — 이 부분은 근사로 넘기지 말 것.

### 2-2. 일 상세

바텀시트 형태(상단에 38×4 드래그 핸들 `#2e3040`). 월 그리드에서 선택한 날짜를 따른다.

- 타이틀 24px weight 500: `10월 3일 토요일`
- 서브 12px `#6d7183`: `개천절 · 음력 8/23` (공휴일 없으면 음력만)
- 우측 상단: 이모지/스티커 버튼(`ph-smiley`, outline)과 추가 버튼(`ph-plus`, accent fill 34px)
- 이벤트 행 (`padding: 13px 4px`): `시각(38px, 11px #6d7183)` + `3px 세로 바(kind 색, height 30px)` +
  `제목 14px + 부가 아이콘(알람/장소/영수증)` / `메타 11px #6d7183` + 우측 **담당 아바타 스택**
  (30px 원, `border: 2px solid #161826`, 겹침 `margin-left:-8px`; 나 = `#332f4d`/`#ded9ff`, 짝꿍 = `#2b3348`/`#c2cbe4`)
- 이벤트 없는 날: 중앙 정렬 2줄 empty state (`이 날은 비어 있어. / 집안일도 일정도 없는 날, 드물어.`)
- 하단 고정 카드 (`bottom: 118px`): **이 날 집안일** — 평면도 데이터와 같은 소스.
  체크 아이콘(`ph-fill ph-check-circle` accent / `ph ph-circle` `#4d5164`) + 라벨(완료 시 `line-through` + `#6d7183`) + 담당

### 2-3. 일정 만들기

상단: `←` + `저장`(13px accent). 제목 입력은 21px placeholder `#4d5164`.
아래는 `border-bottom: 1px solid #22242f` 로 구분된 행 리스트, 각 행 `padding: 15px 22px`, 좌측 아이콘 18px accent + 라벨 14px.

고정 행 순서:
1. **캘린더** — `우리 집 캘린더` + 26px 썸네일 + `›`
2. **종일** 토글 — 켜면 아래 시작/종료 행의 시각이 `종일`로 바뀌고 `#6d7183`으로 dim
3. **시작** — `2026년 10월 3일 (토)` / 우측 `오전 11:00` (들여쓰기 `padding-left: 53px`)
4. **종료** — 같은 형식 / `오후 2:00`
5. **음력으로 반복** 토글 (off 기본)
6. **색** — `라벤더` + 우측 16px 스와치 4개 (`#9184d9` 선택 시 `border 1px #c9c4ec`, `#7f92c9`, `#b57f9e`, `#7fa48c`)
7. **참여자** — 아바타 스택 + `›`
8. **알림** — `10분 전 · 하루 전에 알려줄게` + 제거 `×`

**옵션 필드 칩 (핵심 인터랙션)**: 폼 하단에 `+` 아이콘 + 칩 목록.
칩을 누르면 그 필드가 **폼 리스트 끝에 실제로 추가**되고(배경 `rgba(145,132,217,.06)`, 우측에 `×`로 제거),
칩 목록에서는 사라진다. 칩 = `padding: 9px 14px; radius 999px; background #1c1e2c; border 1px #2e3040`.

칩 목록: `반복` `D-Day` `장소` `링크` `메모` `할 일` `가계 연결` `첨부`

- **`반복`** 은 별도 화면(아래 반복 규칙 편집기)으로 이어진다.
- **`가계 연결`** 은 타임트리에 없는 **살림 고유 필드**다. 일정에 지출을 붙인다 (예: 결혼식 일정 ↔ 축의금 100,000원).
  저장 시 가계부에 거래가 생기고, 캘린더에서 그 일정은 정기결제 바와 같은 계열로 표시된다.
- **`할 일`** 은 이 일정에 딸린 체크리스트 (장보기 목록 등).

---

## 화면 3: 가계부 메인

우선순위: **이번 달 남은 예산**과 **카테고리별 비중**. (사용자가 명시적으로 고른 두 지표)

- 헤더 우측: `17일 남음 / 하루 22,300원` (12px `#6d7183`, 우측 정렬 2줄)
- **남은 예산 카드** (`padding 20px; radius 16px; #1c1e2c` + `border 1px #2e3040`):
  - 숫자 40px weight 500 `letter-spacing -.02em` + `원` 15px `#9397ab`
  - 8px 스택 바: 카테고리별 폭을 순서대로 이어 붙인 하나의 바 (`#9184d9 #7f92c9 #6a6f9c #565a7b #3e415a`)
  - 하단 `쓴 돈 / 예산` 12px `#6d7183` 양끝 정렬
- **카테고리별 비중** 리스트: 8px 점(색) + 이름(13px, 폭 74px) + 5px 진행 바(트랙 `#232532`) + 금액(12px, 우측 66px)
- **분담 요약 행** → 정산 화면 진입: `ph-scales` accent + `이번 달 분담 나 54% · 짝꿍 46%` + `›`

## 화면 4: 지출 입력

사용자가 고른 방식: **카테고리 먼저 → 숫자 키패드**. 두 단계가 한 화면에 다 보인다.

- 상단: `×` / `지출 기록` / `저장`(accent)
- **카테고리 그리드** 4열, 셀 64px, radius 12px: 아이콘 19px + 이름 11px.
  선택 = `rgba(145,132,217,.16)` + `border #9184d9` + `#ded9ff`, 미선택 = `#1c1e2c` / `#2a2c3c` / `#8b8fa3`
  카테고리 8개: 식비 · 생활 · 외식 · 교통 · 의료 · 육아 · 문화 · 기타
- **금액 표시 카드**: `<카테고리> · 오늘 · 내가 냄` (11px `#6d7183`) + 금액 38px weight 500
  (미입력 시 `0`을 `#4d5164`로, 입력되면 `#e9e9ed`), 우측 `원`
- **빠른 가산 pill 3개**: `+1,000` `+5,000` `+10,000` — 현재 금액에 더한다
- **키패드**: 화면 하단 고정, 3열 × 4행, 셀 58px radius 12px `#1c1e2c`/`#2a2c3c`, 숫자 21px weight 500.
  배열 `1 2 3 / 4 5 6 / 7 8 9 / 00 0 ←`. `00`은 뒤에 두 자리 추가, `←`는 한 자리 삭제(색 `#9397ab`).
  최대 9자리. 천단위 콤마는 `toLocaleString('ko-KR')`.

## 화면 5: 정산 · 분담

- 세그먼트 pill: `이번 달` / `3개월` / `전체` (선택 = `rgba(145,132,217,.16)`, `#ded9ff`)
- **집안일 카드** + **가계 카드**: 각각 나/짝꿍 22px 수평 막대(radius 6px, 트랙 `#232532`, 나 `#9184d9`, 짝꿍 `#6a6f9c`) + 수치
- 집안일 카드에는 해석 한 줄: `지난달보다 격차가 4%p 줄었어. 욕실 담당을 한 번 바꿔볼까?`
- **정산 카드** (`background #1a1c2e; border 1px rgba(145,132,217,.4)`):
  kicker `정산` + `짝꿍이 나에게 32,800원` (18px weight 500, 금액만 `#ded9ff`) + 기준 설명 12px
  + `정산 완료로 표시` 아웃라인 버튼 + 공유 아이콘 버튼(48px)

> **미확정**: 정산 기준. 현재 목업은 **공동 지출 반반**. 소득 비율 분담을 지원할지 결정 필요 (설정에 `정산 기준` 행이 이미 있다).

## 화면 6: 반복 규칙 편집기

집안일과 가계부 정기 지출이 **같은 규칙 엔진**을 쓴다. 이게 이 화면의 존재 이유다.

- 모드 4개 (4열 그리드): `매일` `매주` `매달` `직접`
- 모드별 본문:
  - 매일 → 간격 (`매일` / `2일마다` / `3일마다`)
  - 매주 → 간격(`1주마다`…) + **요일 다중 선택**
  - 매달 → 몇째 주(`첫째`~`마지막`) + **요일 단일 선택**
  - 직접 → 기준 (`마지막 완료일부터` / `날짜 고정` / `계절마다`)
- **미리보기 카드** (accent border): `이렇게 반복돼` + 자연어 한 문장(17px weight 500) + 다음 발생 3개 목록
  - 예: `매달 둘째 주 토요일` → `8월 8일 (토) / 9월 12일 (토) / 10월 10일 (토)`
  - `직접 · 마지막 완료일부터` → `완료할 때마다 다시 계산 / 밀린 날은 먼지가 더 짙어져`
- **`밀리면 다음 날로 넘기기` 토글** (기본 on): 끄면 그날 지나면 사라진다. 평면도 먼지 누적 로직과 직결.

데이터 모델 권장 (RRULE 호환):
```
Recurrence = {
  mode: 'daily' | 'weekly' | 'monthly' | 'custom',
  interval: number,               // n일/n주마다
  weekdays: number[],             // 0=일 … 6=토
  monthlyOrdinal: 1|2|3|4|-1,     // 첫째~넷째, -1=마지막
  anchor: 'fixedDate' | 'lastCompleted' | 'season',
  rollover: boolean               // 밀리면 넘기기
}
```
음력 반복(`음력으로 반복` 토글)은 RRULE로 표현되지 않으므로 별도 플래그 + 음력 변환 레이어가 필요하다.

## 화면 7: 할 일 편집

- 제목 입력 (15px, `border 1px #343750`)
- 2열: `방` 드롭다운 / `주기` → 반복 규칙 편집기로 이동
- `담당` 3분할: `나` / `짝꿍` / `번갈아`
- **`방법` 단계 리스트**: 드래그 핸들(`ph-dots-six-vertical`) + 번호 원 19px + 텍스트 13px + 제거 `×`,
  섹션 헤더 우측에 `단계 추가`
- **참고 영상**: `ph-youtube-logo` accent + 제목 + 연필 아이콘.
  프로덕션은 검색어가 아니라 **사용자가 저장한 실제 URL**을 갖고, 인앱 웹뷰나 유튜브 딥링크로 열 것
- 하단 중앙 `이 할 일 삭제` (13px, `#8b6f8f`)

## 화면 8: 가전 유지보수

집 청소와 **주기 단위가 다르고(월·년), 평면도 먼지에는 영향을 주지 않는다.** 별 화면으로 분리한 이유.

- 헤더 카피: `필터랑 청소, 방 청소랑 / 주기가 다르니까 따로 봐` (22px weight 500, line-height 1.35)
- 항목 카드: 아이콘 20px + 이름 14px + `n개월 주기 · 방` 11px + 우측 `D-n` + 하단 4px 진행 바(주기 경과율)
- **임박 강조**: D-3 같은 임박 항목은 `#b57f9e` 계열 + border 강조, 여유 항목은 뉴트럴(`#6a6f9c`/`#565a7b`)
- 하단 `+ 가전 추가` 점선 카드
- 시드: 정수기 필터(6개월) / 에어컨 필터(2개월) / 세탁조 클리닝(3개월) / 보일러 점검(1년) / 후드 필터(4개월)

## 화면 9: 프로필 · 설정

- **가구 카드**: kicker `가구` + 이름 20px + `초대 코드 SLLM-4K2P · 2026년 3월부터`
- **가구원 리스트**: 34px 아바타 + 이름 + `역할 · 집안일 n건 / 가계 n원`
  - 나(소유자), 짝꿍(가구원), **`아이 자리` 점선 슬롯** — `태어나면 여기에 추가해. 담당은 자동으로 안 배정돼`
  - **가구원 = 2인 + 아이 슬롯** 구조. 스키마는 N인 확장 가능하게 만들되 UI는 이 형태를 유지.
- **테마 선택**: Nocturne / Organic 2분할 미리보기 카드 (실제 배경색으로 칠한다)
- 설정 행: `알림`(푸시·카톡) / `동기화` / `평면도 편집` / `정산 기준`

## 화면 10: 알림

- 상단: `←` `알림` + `모두 읽음`(accent)
- `오늘` / `이번 주` 섹션. 오늘 항목은 kind별 색 아이콘 + 본문 13px(line-height 1.45) + 시각 11px + 미읽음 6px accent 점
  - 미읽음 카드는 `border`에 kind 색 (`rgba(145,132,217,.4)` / `rgba(181,127,158,.4)`), 읽음은 `#1a1c28`/`#242634`
- 알림 문구도 홈 스트립과 같은 톤: `짝꿍이 부엌 후드 닦기를 끝냈어. 부엌이 조금 밝아졌어.`
- 하단 **알림 채널** 카드: 앱 푸시 / 카카오톡 / 이메일(주간 요약) 토글 3개

---

## 상태 모델 (프로토타입 → 프로덕션)

프로토타입의 로컬 상태:
```
// 캘린더
selDay: number                     // 선택된 날짜
allDay: boolean                    // 일정 생성 폼의 종일 토글
fields: string[]                   // 폼에 추가된 옵션 필드 id 목록
// 반복 규칙
mode, interval, ord, wd, weekly[]
// 지출 입력
cat: string, amount: string        // 문자열로 누적, 표시할 때만 포맷
```

프로덕션 도메인은 `spec/foundation-scaffolding-design.md` 의 Household/Member 설계를 따르고, 최소 다음 엔티티가 필요하다:

```
Household { id, name, inviteCode, createdAt }
Member    { id, householdId, name, role: 'owner'|'member'|'child', avatarColor }
Room      { id, householdId, name, x, y, w, h, z }        // 퍼센트 좌표, 2% 스냅
Chore     { id, roomId, label, assignee, recurrence, steps[], videoUrl }
ChoreLog  { id, choreId, date, completedBy, completedAt } // 먼지/통계의 소스
Event     { id, householdId, calendarId, title, start, end, allDay, lunar,
            color, attendees[], recurrence, alarms[], place, memo,
            linkedTransactionId }                          // ← 가계 연결
Calendar  { id, householdId, name, color, shared }
Category  { id, name, icon, color, monthlyBudget }
Transaction { id, householdId, categoryId, amount, date, payer, shared,
              memo, receiptUrl, recurrence, linkedEventId }
Appliance { id, householdId, name, roomId, cycleMonths, lastServicedAt }
Notification { id, householdId, kind, body, createdAt, readBy[] }
```

핵심 동기화 규칙:
- `ChoreLog` 가 평면도 먼지 · 정산 통계 · 알림의 단일 소스
- 반복 규칙에 따라 **매일 자정 인스턴스 생성** 스케줄러 필요 (`rollover=false`면 지난 인스턴스 폐기)
- `Event.linkedTransactionId` ↔ `Transaction.linkedEventId` 양방향
- 정기 지출은 `Transaction.recurrence` 로 캘린더에 자동 표시 (사용자가 만든 일정이 아니다 — 읽기 전용 바)
- 두 사용자 실시간 동기화 필요

## 인터랙션 / 상태 스타일

디자인 시스템 규칙을 그대로 따른다: hover/pressed는 accent ramp 한 단계,
`:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }`.
프로토타입에는 hover가 없다 — 구현 시 채워야 한다.

애니메이션은 평면도 완료 효과만 확정값이 있다 (`spec/floorplan-handoff-v1.md`).
캘린더/가계부 화면은 정적 트랜지션 없음 — 플랫폼 기본 네비게이션 전환을 쓰면 된다.
단 **월 그리드 ↔ 일 상세**는 바텀시트 형태이므로 시트 슬라이드업을 쓸 것.

## Design Tokens (Nocturne, 실제로 쓰인 값)

```
bg #161826 · card #1c1e2c · card-dim #1a1c28 · surface #232532 · text #e9e9ed
accent #9184d9 · accent-light #c9c4ec · accent-lighter #ded9ff · accent-tint rgba(145,132,217,.16)
border #2e3040 · border-dim #22242f · border-strong #3a3d55
muted #9397ab · muted-2 #6d7183 · muted-3 #5c5f75 · placeholder #4d5164
kind: 집안일 #9184d9 · 일정 #7f92c9 · 공휴일/경고 #b57f9e · 정기결제 #565a7b · 긍정 #7fa48c
차트 램프: #9184d9 #7f92c9 #6a6f9c #565a7b #3e415a
주말: 일 #c98b8b · 토 #8fa8d9
아바타: 나 bg #332f4d fg #ded9ff · 짝꿍 bg #2b3348 fg #c2cbe4
font: Inter 400/500 (헤딩 weight 500 고정 — 더 굵게 쓰지 말 것)
radius: 카드 14–16 · 작은 요소 10–12 · 이벤트 바 3 · pill 999
아이콘: Phosphor (regular + fill), 인터페이스 17–20px
```

Organic 토큰과 규칙은 `design-systems/organic/readme.md`. 아이콘은 Lucide, stroke-width 2.75.
프로토타입에서는 Lucide를 인라인 SVG로 박아 넣었다 — 구현 시 정식 Lucide 패키지로 교체할 것.

---

## 미확정 / 결정 필요

1. **예산 방식** — 월 총액 하나인가, 카테고리별 예산인가. 목업은 월 총액 + 카테고리는 비중만 표시.
2. **정산 기준** — 공동 지출 반반(현재) vs 소득 비율. 설정 행은 이미 있다.
3. **음력 변환** — 라이브러리/API 선택. 한국 사용자에게 필수, 근사 금지.
4. **가계부 메인 레이아웃** — 채택안(남은 예산)과 미채택안(하루 페이스 + 소진 곡선, `1c`)이 둘 다 그려져 있다.
   실제 사용 후 바꿀 수 있게 두 레이아웃을 같은 데이터로 만들어 두면 좋다.
5. **AI 문자·영수증 스캔** — 이번 범위에서 제외. FAB 확장에 자리를 남겨 두면 나중에 붙이기 쉽다.
6. **가전 유지보수 위치** — 현재 탭 없이 별 화면. 홈 또는 설정 어디서 들어갈지 미정.
