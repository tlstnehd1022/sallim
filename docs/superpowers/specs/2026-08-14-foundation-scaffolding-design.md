# 기반 스캐폴딩 + Household/Member 도메인 — 설계

> 2026-08-14 · sallim-master-spec.md 16장 "즉시 착수" 중 첫 서브프로젝트

## 범위

- Gradle 멀티모듈 스캐폴딩 (바운디드 컨텍스트 경계 확정)
- Household/Member 도메인 모델 (포트 인터페이스까지, 구현체 제외)
- 단위 테스트

**이번 서브프로젝트에서 제외** (다음 서브프로젝트로):
- 인증/JWT, 실시간 동기화 구현
- JPA/MySQL 영속성 구현체
- REST API 레이어
- Chore 도메인 모델 + 시드 데이터

## 결정된 사항

- 언어: Kotlin (JDK 21)
- 클라이언트: React Native (스캐폴딩은 이번 서브프로젝트 범위 밖, 다음에)

## 저장소/툴체인

- Gradle Kotlin DSL 멀티모듈
- `gradle/libs.versions.toml` 버전 카탈로그
- Kotlin 2.x / JDK 21 / Spring Boot 3.3.x

## 모듈 구조

```
settings.gradle.kts
build.gradle.kts        # 루트 공통 설정
gradle/libs.versions.toml
bootstrap/               # Spring Boot 실행 모듈 (main, application.yml)
common/                  # 공유 커널: AggregateRoot, DomainEvent, Identifier<T> 등 기반 타입
household/               # Household·Member 도메인 (이번 서브프로젝트에서 채움)
chore/                   # 빈 모듈 (경계만 예약)
calendar/                # 빈 모듈
ledger/                  # 빈 모듈
```

스펙 42행 "바운디드 컨텍스트 경계는 지금 다 잡는다 (나중에 끼워 넣으면 반드시 꼬임)"을 반영 — `chore`/`calendar`/`ledger`는 빈 모듈로만 만들고 내용은 채우지 않는다.

## Household/Member 도메인 모델 (household 모듈)

- `Household` (애그리거트 루트): id, name, createdAt
- `Member` (엔티티): id, householdId, displayName, role(`OWNER`/`MEMBER`)
- 값객체: `HouseholdId`, `MemberId` (타입 세이프 ID)
- 도메인 이벤트: `MemberJoinedEvent`
- 리포지토리는 포트(인터페이스)만 정의 — JPA 구현체는 영속성 단계(다음 서브프로젝트)에서

## 테스트

JUnit5 + Kotest. 도메인 불변식 위주 단위 테스트 (예: household 생성 시 최초 멤버는 OWNER, 멤버 없는 household 불가 등 — 구현 계획 단계에서 구체화).

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환 후 진행.
