---
name: core-build-test
description: Health Trainer를 빌드·테스트하는 운영 스킬 — system gradle이 없을 때 Gradle wrapper 부트스트랩, :core 단위 테스트 실행 + JUnit XML에서 정확한 통과/실패 수 집계 + :core 순수성(Android import 금지) 검사. Health Trainer에서 "테스트 돌려줘", ":core 빌드", "테스트 몇 개 통과", "gradle이 없다/안 된다", "빌드 환경 설정", 또는 변경 후 검증이 필요할 때 사용. 번들된 scripts/를 우선 쓴다.
---

# Health Trainer 빌드 & 테스트

이 프로젝트의 빌드/테스트에는 두 가지 환경 현실이 있고, 그 때문에 매번 같은 절차를 반복하게 된다. 이 스킬은 그 절차를 결정적 스크립트로 번들한다.

## 환경 현실 (왜 스크립트가 필요한가)
1. **system `gradle`가 없을 수 있다.** → `./gradlew`(wrapper)로 실행한다. wrapper 자체가 없으면 부트스트랩이 필요하다(아래 스크립트).
2. **Android SDK가 없을 수 있다.** → `settings.gradle.kts`가 SDK 없을 때 `:app`을 빌드 그래프에서 제외하므로 `:core`만 빌드/테스트된다. 이게 정상이다(에러 아님).
3. **Gradle 콘솔은 합계 라인을 안 찍는다.** → 정확한 테스트 수는 `core/build/test-results/test/*.xml`(JUnit XML)에서 집계해야 한다.
4. **`:core`는 Android-free여야 한다.** → `android.*`/`androidx.*`/MediaPipe import가 섞이면 JDK만으로 도는 `:core:test`가 깨진다. 매번 grep으로 확인한다.

## 번들 스크립트 (이것을 먼저 써라)

### `scripts/core-test.sh [--rerun]`
`:core` 테스트를 돌리고 **정확한 합계(tests/failures/errors)** 와 **순수성 결과**를 출력한다. `--rerun`을 주면 캐시를 무시하고 강제 재실행한다(검증/증거 캡처용).
```bash
.claude/skills/core-build-test/scripts/core-test.sh           # 일반 실행
.claude/skills/core-build-test/scripts/core-test.sh --rerun   # 강제 재실행
```
순수성 위반이나 테스트 실패가 있으면 0이 아닌 종료 코드로 끝난다(스크립트가 신호를 준다).

### `scripts/bootstrap-gradle.sh`
`./gradlew`가 없을 때만 필요하다. system gradle이 있으면 그걸로, 없으면 핀된 Gradle 배포본을 받아 wrapper를 생성한다. wrapper가 이미 있으면 즉시 종료한다(안전).
```bash
.claude/skills/core-build-test/scripts/bootstrap-gradle.sh
```

## 수동 대체 (스크립트를 못 쓸 때)
```bash
./gradlew :core:test --rerun-tasks          # 강제 재실행
./gradlew projects                          # SDK 없으면 :core만 보임(조건부 include 확인)
./gradlew :core:test --tests "com.healthtrainer.core.exercise.*"   # 일부만
grep -rnE 'import (android|androidx|com\.google\.mediapipe)' core/src   # 순수성(결과 없어야 정상)
```

## 검증 원칙
"통과했다"는 주장은 실제 실행 출력으로 뒷받침한다(superpowers:verification-before-completion). `:app` 런타임은 디바이스가 없으면 검증 불가이며, 그건 정직하게 `unverified (requires device)`로 남긴다 — 이 스크립트들은 `:core`만 다룬다.
