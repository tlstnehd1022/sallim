# 모바일 클라이언트 스캐폴딩 + 홈/캘린더 화면 — 설계

> 2026-08-15 · sallim-master-spec.md 16장 "클라이언트 프레임워크 결정" 후속 + docs/design-handoff/ 반영
>
> 사용자가 야간에 자리를 비운 상태에서 자율 진행. 원래는 클라우드 예약 작업(RemoteTrigger)으로 처리하려 했으나
> 저장소에 대한 GitHub 연동 권한이 없어 403으로 실패 — 대신 이 세션에서 직접, 로컬 워크트리로 진행.
> 질문 없이 판단해야 하는 사안은 이 문서에 결정과 근거를 남긴다.

## 범위

- React Native(Expo) 클라이언트 스캐폴딩: 네비게이션(3탭), 테마 시스템(Nocturne/Organic), 폰트/아이콘
- 화면 1(홈: 평면도 + 요약 스트립) 구현 — 앱의 정체성, 최우선
- 화면 2(캘린더 2a: 월 그리드 + 일 상세 + 일정 생성) 구현
- 정적 목업 데이터만 사용 — 백엔드 API 없음 (Kotlin 백엔드는 별도 트랙에서 진행 중, 이 작업과 독립)

**이번 범위에서 제외** (다음 서브프로젝트로, `docs/design-handoff/README.md` 화면 3~10 참고):
- 가계부 메인/지출입력/정산·분담, 반복 규칙 편집기, 할 일 편집, 가전 유지보수, 프로필·설정, 알림
- 백엔드 연동, 실시간 동기화, 인증

## 결정된 사항

### 클라이언트 스택

- **Expo + TypeScript + Expo Router** (파일 기반 라우팅, 탭 내비게이션 내장 — React Navigation을 직접 세팅하는 것보다 보일러플레이트가 적음)
- 위치: 저장소 루트의 `mobile/` 디렉터리. Gradle 백엔드 모듈(`bootstrap`/`common`/`household`/`chore`/`calendar`/`ledger`)과 완전히 분리된 형제 디렉터리 — 백엔드가 나중에 이 브랜치와 머지될 때 충돌 없음.
- 상태 관리: React Context + useState로 충분 (YAGNI). Redux/Zustand는 실제 필요가 생기기 전까진 추가하지 않음.

### 아이콘

- Nocturne: 시안 원본이 Phosphor. `phosphor-react-native` 사용, 해석 안 되면 `@expo/vector-icons`(Ionicons)로 대체하고 그 사실을 커밋 메시지에 남긴다.
- Organic: 시안 원본이 Lucide(stroke-width 2.75). `lucide-react-native` 사용.

### 폰트

- Nocturne: Inter 400/500/600 (헤딩도 500 — 더 굵게 쓰지 않음)
- Organic: Caprasimo(헤딩) / Figtree(본문)
- `@expo-google-fonts/*` 패키지로 로드. 해석 안 되는 폰트는 시스템 폰트로 폴백하고 커밋 메시지에 남긴다.

### 테마

- 토큰 맵 1개 + 테마 2개 구조 (`docs/design-handoff/design-systems/{nocturne,organic}/styles.css`의 실제 값을 그대로 이식)
- Context/Provider로 런타임 전환 가능하게 만들되, 전환 UI(프로필 화면)는 이번 범위 밖 — 전환은 코드로만 검증

### 목업 데이터

- `sallim-master-spec.md` 4.4장 `DEFAULT_ROOMS`(방 10개 + 할 일 18개) 이식
- 캘린더 목업 이벤트는 `docs/design-handoff/README.md` 화면 2 절의 데이터 모델(`Event`)을 따라 정적 배열로 작성

### 음력 캘린더

- 시안 프로토타입과 동일하게 선형 근사 사용. 실제 변환 라이브러리 도입은 README가 이미 "결정 필요" 항목으로 명시한 별도 과제 — 이번에 임의로 무거운 의존성을 추가하지 않는다.

## 테스트

- Expo 기본 Jest + React Native Testing Library
- 화면당 최소 1개 스모크 테스트(크래시 없이 렌더 + 핵심 요소 존재 확인)

## Git 워크플로

- 브랜치: `worktree-mobile-client-scaffolding` (이미 워크트리로 생성됨), base는 `origin/master`
- 로컬 커밋만 진행, push는 사용자가 아침에 직접 (백엔드 브랜치와 동일한 방침)
- 완료 후 finishing-a-development-branch 스킬로 마무리 (머지 여부는 사용자 재량이나, 부재 시 로컬 merge까지 진행하고 push는 보류)

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환 후 subagent-driven-development로 실행.
