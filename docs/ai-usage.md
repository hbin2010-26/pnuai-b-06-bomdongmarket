# AI 도구 활용 및 생성 코드 검증

> 프로젝트: FarmBroker · 팀: 봄동마켓

AI 결과는 자동 병합하지 않고 spec, 저장소 규칙, 테스트와 팀원 리뷰를 기준으로 검토·수정했습니다.

## 1. 활용 도구와 범위

|         도구          | 활용 범위                                                                                                                        |
| :-------------------: | :------------------------------------------------------------------------------------------------------------------------------- |
|      Claude Code      | Superpowers 플러그인의 spec·TDD 파이프라인, Custom Skills, SubAgent 병렬 구현, superpowers reviewer를 이용한 PR 검토             |
|         Codex         | 코드 분석, 구현·리팩터링, 테스트·문서 작성과 구현 근거 확인                                                                      |
|  프로젝트 전용 Skill  | `frontend-design-system-bootstrap`과 `frontend-ui-consistency`를 직접 제작·사용해 디자인 규칙 수립, UI 구현과 리뷰 절차를 자동화 |
| ChatGPT·Claude·Gemini | 요구사항, 아키텍처·API·ERD와 예외 상황 교차 검토                                                                                 |
|      Gemini API       | 서비스에서 공간·수익 조건에 맞는 작물 추천 근거를 구조화된 응답으로 생성                                                         |
|     Figma·ChatGPT     | 와이어프레임, UI 시안과 사용자 흐름 검토                                                                                         |

## 2. 저장소 하네스와 적용

하네스는 Agent나 세션이 바뀌어도 같은 입력·구현·검증 절차를 따르게 하는 공통 실행 체계입니다.

`공통 AGENTS → 영역별 AGENT → spec·plan → Skill·Subagent-Driven → reviewer·CI`

- **Harness(Karpathy 기반 지침):** [GitHub 약 20.6만 Star의 공개 지침](https://github.com/multica-ai/andrej-karpathy-skills)을 차용해 프로젝트 `AGENTS.md`로 이식하고 가정 명시·단순성·최소 변경·검증 목표를 공통 적용했습니다.
- **영역 컨텍스트:** 프론트엔드·백엔드 `AGENT`에 버전, 명명, 계층, 보안, 디자인과 검증 명령을 기록했습니다.
- **실행 절차:** `docs/specs`를 검토하고 `docs/plans`에서 파일·실패 테스트·검증 명령으로 나눠 Superpowers와 Subagent-Driven 방식으로 실행했습니다.
- **전용 Skill:** `frontend-design-system-bootstrap`으로 UI를 규격화하고 `frontend-ui-consistency`로 구현·리뷰했습니다.
- **품질 게이트:** reviewer, 팀원 diff 리뷰, PR 체크리스트와 CI를 통과한 변경만 병합했습니다.

## 3. AI 생성 코드 검증·수정 방식

- **TDD:** 실패 테스트를 먼저 확인하고 최소 구현을 추가한 뒤 관련·전체 테스트로 회귀를 점검했습니다.
- **계약 검토:** DTO, API, 인증·권한과 도메인 경계를 기존 코드와 대조하고 추측성 기능은 제거했습니다.
- **Reviewer·CI:** reviewer와 팀원이 diff·회귀 위험을 검토하고 프론트엔드 lint·Vitest·build, 백엔드 Gradle build·JUnit 결과를 PR에 기록했습니다.
- **디자인 검증:** Skill 스크립트·체크리스트와 CI로 토큰, 공통 컴포넌트, 접근성, 상태 화면과 반응형 규칙을 확인했습니다.
- **서비스 AI 검증:** 서버 계산 순위로 Gemini 후보를 제한하고 ID·중복·개수·근거를 검증했습니다. 오류는 1회 재시도하며 타임아웃·쿼터 초과 때만 최근 저장 추천을 재사용합니다.
