# Gate 좌석 예약 서비스

Gate는 좌석 예약 서비스입니다. 사용자는 시간대를 선택하고 좌석을 고른 뒤 결제수단을 선택해 예약할 수 있습니다.

## 기술 스택
- Java 25 / Spring Boot 4.0.0 / Gradle Kotlin DSL
- PostgreSQL 17 / Redis 8 (Docker Compose)
- Spring MVC, Thymeleaf, Spring Data JPA, Validation, Actuator

## 현재 구현한 기능
- 회원가입, 로그인, 로그아웃 및 10분 세션 만료
- 10시·14시·18시 고정 입장 시간대
- 시간대별 100석 좌석 선택 화면
- 결제수단 선택 화면(카드·간편결제·무통장입금 모의 흐름)
- 내 예약에서 시간대와 좌석 확인
- 예약 취소 시 좌석 복구

## 동시성 제어
선착순 요청이 동시에 들어와도 초과 예약과 같은 좌석 중복 예약이 발생하지 않도록 PostgreSQL 비관적 쓰기 락(`PESSIMISTIC_WRITE`)을 시간대와 좌석 조회에 적용했습니다.

동시성 실험 결과:
`requests=100, success=100, reservations=100, remaining=0`

실행 명령:
```powershell
.\gradlew.bat test --tests com.gate.reservation.ConcurrencyExperimentTest --rerun-tasks --console=plain
```

## 실행
```powershell
docker compose up -d postgres redis
.\gradlew.bat bootRun
```
브라우저에서 `http://localhost:8080/slots`를 엽니다.

## Redis 보호 기능
- 시간대별 Redis 분산 락(10초 TTL)으로 다중 서버 중복 처리 방지
- 사용자별 10초당 예약 요청 10회 제한으로 폭주 완화

## 설계 결정과 문제 해결 기록

### 예약 정합성
처음에는 단순히 남은 좌석 수를 읽고 감소시키는 방식으로 예약을 처리했습니다. 동시에 요청이 들어오면 여러 요청이 같은 값을 읽어 초과 예약이 발생할 수 있어 PostgreSQL `PESSIMISTIC_WRITE` 락을 적용했습니다. 시간대와 좌석을 잠근 뒤 검증·감소·저장을 하나의 트랜잭션으로 처리합니다.

### 다중 서버와 폭주 대응
서버가 여러 대로 늘어나면 DB 락만으로 애플리케이션 요청 흐름을 조정하기 어려워 Redis 시간대별 분산 락을 추가했습니다. 같은 시간대 처리 중인 요청은 10초 TTL로 보호합니다. 사용자별 예약 요청은 10초당 10회로 제한해 반복 클릭과 폭주를 완화합니다.

### 대기열
분산 락을 얻지 못한 요청은 `gate:queue:slot:{slotId}` Redis 리스트에 도착 순서대로 저장합니다. 로그인 사용자는 `/api/queue/{slotId}`에서 자신의 대기 순번을 확인할 수 있습니다. 현재는 대기열 등록·조회 단계이며, 자동 처리 워커는 확장 작업입니다.

## 테스트 시나리오

- 정상 회원가입·로그인·로그아웃
- 시간대별 좌석 선택과 결제수단 선택
- 같은 사용자의 동일 시간대 중복 예약 차단
- 예약 취소 후 좌석 복구
- 같은 좌석 동시 요청 시 중복 예약 방지
- 100명 동시 예약 시 정원 초과 방지
- Redis 요청 제한과 대기열 등록

동시성 테스트는 실험 데이터를 자동으로 생성하고 종료 후 삭제합니다.

## AI 활용과 검증

AI는 코드 초안과 오류 원인 후보를 제시하는 데 활용했습니다. 최종 구현은 개발자가 직접 실행하고, 로그를 확인하고, 오류를 재현하고, 동시성 테스트 결과를 검증한 뒤 반영했습니다. 모든 변경은 Git 커밋으로 기록합니다.

## 유지보수 계획

기능 추가보다 먼저 테스트를 유지하고, 오류 로그와 재현 절차를 기록합니다. 이후 대기열 자동 처리 워커, 실제 결제 연동, 부하 테스트, 배포, 모니터링과 장애 복구를 단계적으로 추가합니다.

## 자동 좌석 배정

좌석을 직접 선택하지 않아도 사용자가 선택한 시간대의 빈 좌석을 서버가 무작위로 선택해 예약합니다. 후보 좌석을 섞은 뒤 기존 Redis 분산 락과 PostgreSQL 좌석 락을 적용해 동시에 요청해도 같은 좌석이 중복 배정되지 않도록 처리합니다.

## 장애 확인
docker compose ps에서 PostgreSQL과 Redis가 healthy인지 확인합니다. PostgreSQL은 pg_isready, Redis는 edis-cli ping으로 연결 상태를 검사합니다.

## CI
GitHub Actions는 Java 25 환경에서 Docker 의존성을 healthy 상태까지 기다린 뒤 전체 Gradle 테스트를 실행합니다.

## 부하 검증 결과

200개의 동시 예약 요청을 100석 슬롯에 보낸 결과 100건만 성공하고 초과 요청은 거절되었습니다.

```text
requests=200 success=100 saved=100 elapsedMs=1142 remaining=0
```
## 배포 전 체크리스트

- `GATE_ADMIN_EMAIL`을 실제 관리자 계정으로 설정
- DB·Redis 비밀번호를 환경 변수로 분리
- `docker compose ps`에서 두 서비스가 healthy인지 확인
- `./gradlew test --no-daemon` 통과 확인
- `/actuator/health`가 UP인지 확인
- HTTPS와 방화벽을 배포 서버에서 설정
- 배포 전 DB 백업과 롤백 이미지를 준비

## 현재 진행 상태 (2026-10-06)

완료:
- 토스페이먼츠 테스트 결제창, 서버 승인, 결제 금액 검증
- 결제 승인 기록 저장 및 중복 결제 키 차단
- 내 예약 화면의 결제 내역 표시
- 토스 테스트 취소 API와 예약·좌석 반환 연결
- 부분 취소를 지원하지 않는 주문 전체 취소 정책 적용
- 결제·예약·동시성 테스트 통과

다음 작업 우선순위:
1. 새 2좌석 주문을 결제한 뒤 한 좌석 취소 시 주문 전체가 취소되는지 수동 검증
2. 결제 시작 시 좌석을 임시 점유하고 결제 만료 시 자동 반환하도록 보강
3. 결제 웹훅과 실패·중복·만료 테스트 추가
4. 운영용 DB 마이그레이션(Flyway)과 환경 변수 점검
5. HTTPS·도메인·백업·로그·롤백을 포함한 실제 배포 준비

운영 전 주의:
- 현재 결제는 토스 테스트 모드이며 실제 금액은 청구되지 않는다.
- 실제 운영 결제 전에는 부분 취소를 지원하지 않는 정책을 화면과 환불 안내에 명시해야 한다.

## 2026-11-05 deployment preparation

- Reservations now carry orderId. Cancellation targets that exact order, not every purchase for the same time slot.
- Checkout holds selected seats for ten minutes. Approval confirms the held rows.
- If reservation finalization fails after payment approval, a compensating test payment cancellation is attempted.
- Dockerfile and compose.demo.yml prepare one-server hosting with Caddy HTTPS; database/cache ports are internal.
- Environment files are excluded from Git. Copy .env.demo.example to .env.demo on the server and supply test keys privately.
- Deployment command: docker compose --env-file .env.demo -f compose.demo.yml up -d --build
- SITE_ADDRESS must resolve to the server. Allow inbound TCP 80/443; restrict SSH to the operator.
- Pending: payment timeout/reconciliation tests, historical payment-to-reservation linkage, CSRF protection, database migrations, backup/restore verification, container build and browser end-to-end verification.
- Public deployment is not complete. A cloud account, server, address and budget choice remain required.

## Safety review 2026-11-05
- Removed the legacy direct reservation confirmation endpoint. Auto-assignment now selects seats and follows checkout.
- Enabled Spring Security CSRF filtering and Thymeleaf form tokens. Live HTTP verification: login 200, token present, cancellation without token 403, health 200.
- Session ID rotates after login/signup. Payment customer identifiers are session-specific UUIDs. Payment API accepts test secret keys only.
- Cancellation and expiration recheck reservation state under the slot lock. Unpaid holds may be cancelled without a payment record.
- Queue processing no longer creates reservations without checkout; admission-ready signalling is basic and requires further UI integration.
- Remaining blockers: approval timeout/reconciliation, compensation failure recovery, historical unlinked payments, migration/backups, full browser payment/cancellation regression. Do not treat passing unit tests as end-to-end payment verification.

## Payment approval hardening (2026-11-05)
- Slot database lock now covers the local replay check, gateway confirmation and reservation finalization. This serializes confirmations for the same slot; it also means gateway latency holds that slot lock.
- Confirmation and full cancellation use stable Idempotency-Key headers.
- Connect timeout: 5 seconds; read timeout: 15 seconds. Confirmation errors trigger a payment query. Only matching paymentKey/orderId/totalAmount with DONE status is accepted.
- Tests: timeout followed by approved query, response amount mismatch, live-key rejection and repeated recorded order without a second gateway call passed.
- Removed controller-level automatic cancellation on any reservation error: that could cancel a valid payment belonging to a parallel request. Durable reconciliation/compensation jobs are still required for DB commit failures or unavailable payment queries.
- A replay after the temporary Redis order expires is still rejected by the controller. Durable order state and recovery remain pending.

## 결제 복구 기록과 재처리

- 결제 승인 API 호출 전에 `payment_recovery`에 요청을 별도 트랜잭션으로 저장합니다. 기록 저장이 실패하면 결제 API를 호출하지 않습니다.
- 최초 1분 후부터 워커가 30초 간격으로 재처리 대상을 조회합니다. 실패 간격은 점진적으로 늘리며 10회 후 `MANUAL_REVIEW`로 남깁니다.
- `PENDING`: 결제 조회 필요, `RESOLVED`: 로컬 상태 정리 완료, `MANUAL_REVIEW`: 관리자 확인 필요.
- 토스 조회에서 주문번호·결제키·금액을 검증합니다. 승인 완료이고 좌석 점유가 유효하면 예약을 복구합니다. 좌석이 만료됐다면 전체 취소를 확인한 뒤 취소 기록을 저장합니다.
- 정상 로컬 결제 기록이 존재하는 주문에는 보상 취소를 호출하지 않습니다. 사용자에게는 내 예약에서 확인 중/관리자 확인 안내를 표시합니다.
- 테스트: 복구 분기 6개, 예약 트랜잭션 롤백 후 복구 기록 보존(PostgreSQL 통합 테스트), 기존 전체 테스트 통과. 서버 재시작 후 회원가입·내 예약 HTTP 200 확인.
- 한계: 자동 복구는 이번 변경 이후 기록된 결제 승인 시도 대상입니다. 과거 결제와 사용자 요청에 의한 취소 API의 불확실한 결과 복구는 별도 보강이 필요합니다. DB 자체 장애 중에는 재처리도 DB 복구를 기다립니다. 관리자 수동 재처리 화면은 아직 없습니다.

## 취소 응답 유실 복구 및 실행 서버 검증

- 사용자 취소 의도를 `payment_recovery.cancellation_requested`에 먼저 저장합니다. 승인 복구와 취소 복구가 경합해도 취소 의도를 유지합니다.
- 취소 API 응답 유실 시 조회 API에서 `CANCELED`를 확인합니다. 확인 실패 시 결제/예약을 그대로 유지하고 워커가 다시 처리합니다.
- 워커는 이미 취소된 결제를 다시 취소하지 않고 로컬 기록을 동기화합니다. 좌석 반환은 활성 예약에만 적용합니다.
- 자동 테스트: 취소 응답 타임아웃 후 조회 복구, 취소 실패 시 상태 보존, 원격 취소 완료 시 동기화, 반복 복구 시 좌석 중복 반환 방지 및 전체 테스트 통과.
- 실행 서버 HTTP 검증: `pwsh -File scripts/verify-local.ps1`. 서버와 Docker DB/Redis가 실행 중이어야 합니다. 테스트용 계정과 취소된 예약이 생성되며 실제 PG 승인 API는 호출하지 않습니다.
- 검증된 흐름: 회원가입/CSRF → 날짜/100석 화면 → 자동 2석 배정 → 수동 2석 선택 → 결제 페이지 렌더링 → 두 주문 전체 취소 → 좌석 반환 → 로그아웃/재로그인.
- 브라우저 UI 제어는 현재 연결 제한으로 완료하지 못했습니다. 토스 SDK 렌더링/인증을 포함한 최신 브라우저 전체 흐름은 별도 수동 확인이 필요합니다.
