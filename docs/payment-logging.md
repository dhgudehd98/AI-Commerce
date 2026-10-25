# 결제 로그 규약

## 형식

서버 로그: `[PAYMENT] stage=단계 event=사건 orderNumber=주문번호 paymentId=결제ID attemptId=승인시도ID ...`

예외 메시지: `[결제/단계] 확인된 실패 원인`

식별자는 해당 단계에서 확보된 값만 기록한다. 주문 생성 전에는 paymentId/attemptId가 없을 수 있다. 동일 주문 재요청은 orderNumber, 동일 승인 시도는 attemptId로 묶는다. 식별자가 없다는 이유로 가상의 값을 생성하지 않는다.

## 주요 단계

| stage | 의미 |
|---|---|
| PREPARE | 결제 준비 트랜잭션 |
| PREPARE_CREATE | 신규 주문·결제·승인 시도 생성 |
| PREPARE_REUSE | 기존 준비 결과 재사용 |
| DECIDE | 결제 상태에 따른 실행 경로 선택 |
| COMPLETED_READ | 기존 완료 내역 조회 |
| APPROVAL_VALIDATE | 소유자·연결·금액·카드·상태 조합 검증 |
| PG_REQUEST | 자동결제 외부 요청 및 응답 |
| APPROVAL_VERIFY | 외부 승인 결과 검증 |
| APPROVAL_APPLY | 내부 완료 반영 및 출고 생성 |
| LOCK | 토큰 소유권을 확인한 락 해제 |

## 사건 해석

- RECEIVED / START: 요청 수신·단계 시작. 성공이 아니다.
- LOCK_REQUEST / LOCK_ACQUIRED / LOCK_BUSY: 락 시도·획득·경합. 경합은 PG 결제 거절이 아니다.
- STATE_READ / STATE_CHECK: 현재 결제 상태와 재요청 여부.
- REQUEST_SENT: PG 호출 직전 기록. PG가 실제로 수신했다는 보장은 아니다.
- RESPONSE_RECEIVED: PG HTTP 응답 수신. pgStatus와 approvedAmount를 확인해야 하며 내부 DB 커밋을 의미하지 않는다.
- HTTP_ERROR / RESULT_UNKNOWN: PG 결과 확인 필요. 자동으로 실패 확정·재고 해제하지 않는다.
- CREATED_PENDING_COMMIT / APPLIED_PENDING_COMMIT: 트랜잭션 내부 작업만 완료. 후속 오류로 롤백될 수 있다.
- COMMITTED: 락 내부 작업의 트랜잭션이 반환된 이후 기록. PREPARE/APPROVAL_VALIDATE/APPROVAL_APPLY로 구분한다. 현재 진입점에 상위 트랜잭션이 없다는 전제다.
- REUSED / REPLAYED: 기존 결과 사용. 새로운 승인·출고 생성을 뜻하지 않는다.
- FAILED: 해당 내부 단계 실패. DB 결과와 PG 결과를 동일시하지 않는다.
- RELEASE_RESULT released=false: 소유권 불일치·TTL 만료 등으로 삭제되지 않음. 무조건 다른 요청의 락을 삭제하지 않는다.

## 민감 정보 및 정합성 확인

BillingKey, customerKey, paymentKey, 멱등키, 인증 헤더, 카드번호, 이름, 이메일, 배송지, 요청·응답 전문을 일반 운영 로그에 기록하지 않는다. 예외 객체의 전문에도 이 정보가 있을 수 있으므로 외부 HTTP 오류는 타입·상태와 정제된 원인만 기록한다.

로그는 DB와 원자적으로 저장되는 감사 원장이 아니다. 커밋 직후 프로세스 종료로 COMMITTED 로그가 없을 수 있고, 내부 로그는 롤백되어도 남는다. AI 분석은 이상 후보를 제시하는 용도로 사용하며, 최종 결제 정합성은 Payment·Orders·PaymentAttempt·PG 조회 및 저장된 감사 내역을 대조한다. 로그만으로 결제 취소·재승인·상태 강제 변경을 실행하지 않는다.

재고는 결제 완료 시 유지한다. 실제 재고 차감·예약 해제는 출고 인계 완료 단계의 책임이다.
