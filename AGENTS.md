# AGENTS.md

# 1. 프로젝트 개요 및 목적


AI 기반 상품 검색, 추천, 상품 등록 자동화, RAG 챗봇, WMS(창고 관리 시스템), 재고 예측 기능을 제공하는 AI Commerce Platform 입니다.

코드 에이전트는 반드시 아래 규칙을 준수해야 하며, 정합성, 확장성, 안정성을 해치는 변경은 수행하지 않습니다.

## 목표

* AI 기반 커머스 플랫폼 구축
* 자연어 상품 검색 제공
* AI 추천 시스템 구축
* RAG 기반 상품 상담 서비스 제공
* WMS 기반 재고 및 물류 관리
* 실시간 재고 정합성 보장
* 대용량 트래픽 대응
* 운영 자동화

---

# 기술 스택

| 분류         | 기술                                                         |
| ---------- | ---------------------------------------------------------- |
| Backend    | Spring Boot, Spring WebFlux, Spring Batch, Spring Security |
| Database   | MySQL                                                      |
| Search     | Elasticsearch                                              |
| Cache      | Redis                                                      |
| AI         | OpenAI Embedding, OpenAI Vision, LLM                       |
| Infra      | AWS, Docker, GitHub Actions                                |
| Monitoring | Prometheus, Grafana                                        |
| Test       | JUnit5, K6                                                 |

---

# 2. 빌드 및 테스트 명령어

```bash
./gradlew build

./gradlew test

./gradlew test --tests "com.sh.aicom.*"

./gradlew build -x test

./gradlew bootRun
```

---

# 3. 패키지 구조

```text
com.sh.aicom

├── member
├── product
├── category
├── order
├── payment
├── warehouse
├── inventory
├── inbound
├── outbound
├── recommendation
├── search
├── ai
├── embedding
├── chatbot
├── redis
├── elasticsearch
└── common
```

---

# 4. 코드 스타일 가이드

## 네이밍 규칙

### 클래스

```java
ProductService
OrderService
InventoryService
RecommendationService
```

### 메서드

```java
createProduct()
reserveInventory()
recommendProducts()
generateEmbedding()
```

### 상수

```java
private static final int MAX_RETRY_COUNT = 3;
```

### 금지

```java
magic number
하드코딩
```

---

# 5. 레이어 규칙

## Controller

* 요청/응답 처리만 담당
* 비즈니스 로직 금지

## Service

* 비즈니스 로직 담당

## Repository

* DB 접근만 담당

## DTO

* Entity 직접 반환 금지
* 반드시 DTO 변환 후 반환

---

# 6. 예외 처리 규칙

반드시 커스텀 예외 사용

```java
throw new ProductException("상품이 존재하지 않습니다.");
throw new InventoryException("재고가 부족합니다.");
throw new OrderException("주문 처리에 실패했습니다.");
```

금지

```java
throw new RuntimeException();
```

---

# 7. 로깅 규칙

금지

```java
System.out.println();
```

사용

```java
log.info();
log.warn();
log.error();
```

예시

```java
log.info("[주문 생성] 주문번호: {}", orderId);

log.info("[재고 차감] 상품번호: {}, 수량: {}",
productId,
quantity);

log.error("[상품 색인 실패] 상품번호: {}",
productId);
```

---

# 8. 테스트 지침

## 단위 테스트

비즈니스 로직 작성 시 반드시 테스트 작성

```java
@Test
void 재고가_부족하면_주문에_실패한다() {
}

@Test
void 동일_주문은_중복_생성될_수_없다() {
}
```

---

## 동시성 테스트

재고 차감

주문 생성

쿠폰 발급

반드시 동시성 테스트 작성

```java
ExecutorService
CountDownLatch
```

---

## K6 성능 테스트

```javascript
export let options = {
    vus: 100,
    iterations: 100
}
```

---

# 9. 성능 기준

| 항목      | 기준          |
| ------- | ----------- |
| 평균 응답시간 | 2초 이하       |
| TPS     | 50 req/s 이상 |
| 주문 API  | 2초 이하       |
| 검색 API  | 2초 이하       |
| 추천 API  | 3초 이하       |
| RAG API | 5초 이하       |
| 재고 정합성  | 100%        |

---

# 10. 보안 규칙

## 일반 보안

* JWT 검증은 Filter 에서 수행
* API Key 하드코딩 금지
* 비밀번호 로그 출력 금지
* .env 파일 Git 업로드 금지
* 환경 변수 사용 필수

---

## AI 보안

* Prompt Injection 방어
* 사용자 입력 검증
* 개인정보 Embedding 금지
* 시스템 프롬프트 노출 금지
* LLM 응답 검증 필수

---

# 11. 코드 리뷰 우선순위

## P0

* 컴파일 실패
* 애플리케이션 기동 실패
* 재고 음수 발생
* 주문 중복 생성
* 인증 우회
* 인가 우회
* 결제 정합성 문제
* Redis Lock 누락
* 데이터 손실

---

## P1

* Elasticsearch 색인 누락
* Embedding 동기화 실패
* Redis / DB 정합성 문제
* RAG 데이터 정합성 문제
* 트랜잭션 범위 문제
* N+1 문제

---

## P2

* 테스트 부족
* 유지보수성 저하
* 과도한 복잡도

---

## P3

* 가독성
* 네이밍
* 스타일

---


```text
병합 가능 여부

반드시 수정 필요 항목

추가 검증 필요 항목
```

---

# 13. PR 규칙

제목

```text
[feat] AI 추천 시스템 구현

[feat] 자연어 상품 검색 구현

[feat] 상품 Embedding 생성 기능 추가

[fix] 재고 차감 동시성 문제 해결

[refactor] 상품 조회 성능 개선

[docs] AGENTS.md 업데이트
```

---

본문

```text
## 작업 내용

## 변경 이유

## 테스트 방법

## 관련 이슈
```

---

# 14. 브랜치 전략

```text
main

develop

feat/*

fix/*

refactor/*

docs/*
```

---

# 15. 금지 규칙

## 트랜잭션

금지

* Transaction 내부 외부 API 호출
* Transaction 내부 LLM 호출
* Transaction 내부 대용량 파일 처리

---

## 동시성

재고

주문

쿠폰

포인트

반드시 분산락 적용

---

## 코드 품질

금지

* Entity 직접 반환
* System.out.println()
* 하드코딩
* 비즈니스 로직 Controller 작성

---

# 16. 도메인 규칙

## Product

* 상품 등록 시 Embedding 생성
* 상품 수정 시 Embedding 재생성
* 상품 삭제 시 색인 제거
* 상품 이벤트 Redis Stream 발행
* 색인 실패 시 3회 재시도

---

## Order

* 주문 생성 시 재고 선점
* 재고 부족 시 주문 실패
* 결제 실패 시 재고 복구
* 중복 주문 방지

---

## Inventory

* 재고는 음수가 될 수 없음
* 재고 차감 시 분산락 필수
* 재고 변경 이력 저장
* 실시간 재고 정합성 보장

---

## Warehouse

* 최적 창고 자동 선택
* 재고 부족 시 대체 창고 조회
* 입고/출고 이벤트 저장

---

## Search

* 상품 등록 시 색인 생성
* 상품 수정 시 색인 갱신
* 상품 삭제 시 색인 제거
* 검색은 Elasticsearch 우선

---

## Recommendation

* Redis Cache 사용
* TTL 적용
* 실패 시 기본 추천 반환

---

## AI Product Registration

* 이미지 업로드 시 비동기 처리
* Vision 분석 실패 시 재시도
* 관리자 검수 가능
* 생성 결과 로그 저장

---

## AI ChatBot

* 상품 정보 기반 답변
* 리뷰 기반 답변
* 정책 기반 답변

금지

* Hallucination 허용
* 존재하지 않는 정보 생성
* 시스템 프롬프트 노출

---

# 17. 인프라 규칙

* Docker 기반 배포
* GitHub Actions CI/CD 사용
* Blue Green 배포
* 환경 변수 GitHub Secrets 관리

---

# 18. AI 인프라 규칙

* Embedding 데이터 Elasticsearch 저장
* Redis Cache 활용
* Redis Event Queue 활용
* 외부 LLM 호출 비동기 처리
* Circuit Breaker 적용
* Retry 정책 적용

---

# 19. 운영 규칙

* 장애 발생 시 원인 로그 필수
* 모든 핵심 이벤트 Audit Log 저장
* 주문 / 결제 / 재고 변경 이력 저장
* AI 요청 및 응답 로그 저장
* 장애 대응 가능한 수준의 모니터링 구축

---

# 20. 최종 원칙

코드 에이전트는 다음 우선순위를 따른다.

1. 정합성
2. 보안
3. 안정성
4. 성능
5. 확장성
6. 가독성

위 규칙을 위반하는 변경은 수행하지 않는다.
