# sallim (살림)

한 가구가 함께 쓰는 살림 관리 앱 — 집안일 · 캘린더 · 가계부 통합. 기획 전체 배경은 `sallim-master-spec.md` 참조.

## 아키텍처 규칙

- 모듈러 모놀리스. 바운디드 컨텍스트: `household`(공유 기반) / `chore` / `calendar` / `ledger`
- 컨텍스트 간 직접 참조 금지 — 오직 도메인 이벤트로만 통신
- 각 컨텍스트 내부는 hexagonal 레이어링: `domain` / `application` / `infrastructure` / `api`
- `domain` 패키지는 Spring·JPA 등 프레임워크 의존 금지 — 순수 Kotlin
- 표현 관심사는 별도 값객체로 분리해 엔티티를 오염시키지 않는다 (예: `Room` vs `FloorPlan` — 4.3장)

## 기술 스택

Kotlin(JDK 21) · Spring Boot 3.x · JPA + QueryDSL · MySQL · Redis · Kafka · Docker/Kubernetes · JUnit5 + Kotest + Testcontainers

## 설계 원칙

- MSA로 미리 쪼개지 않는다 — 모듈러 모놀리스로 시작하고 경계만 명확히 잡는다
- YAGNI — 스펙에 명시된 것만 구현, 추측성 확장 금지
- 디자인 시스템: Nocturne(다크) / Organic(웜) 두 테마를 토큰 맵 1개로 공유. 세부 값은 `sallim-master-spec.md` 10장

## 문서

- 전체 기획: `sallim-master-spec.md`
- 설계 스펙(서브프로젝트 단위): `docs/superpowers/specs/`
