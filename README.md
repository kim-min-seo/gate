# Gate 선착순 예약 시스템

취업 포트폴리오용 선착순 좌석 예약 서비스입니다. 사용자는 시간대를 선택하고 좌석을 고른 뒤 결제수단을 선택해 예약할 수 있습니다.

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
