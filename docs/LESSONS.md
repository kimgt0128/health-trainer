# Lessons (Compound Engineering)

`compound-engineering` 스킬의 기록부. 실수·교정·발견을 근본 원인까지 적고, 어떤 규약/하네스/테스트로 재발을 막았는지(되먹임)를 남긴다. 최신이 위로.

### 2026-06-05 — replay 런타임 버그(뒤로가기 없음·3D 왜곡)는 디바이스 런에서야 드러남
- 무엇: `:app`은 컴파일만 검증돼서, replay 화면에 **뒤로가기 경로가 없고**(갇힘) 3D 투영(고정 scale + y-up 가정 + 강한 z-skew)이 측면 스쿼트에서 **왜곡/반전**.
- 근본 원인: UI 네비게이션 완결성과 좌표/투영 정확성은 컴파일러가 못 잡는다 → 런타임 검증 필요. MediaPipe world y축 방향을 단정한 게 화근.
- 고친 방법: `BackHandler`+버튼으로 복귀; 렌더링을 **자동맞춤(bbox) + 해부학적 정립(NOSE를 위로) + z-skew 축소**로 좌표 가정과 무관하게 견고화.
- 되먹임: 원칙 "모든 화면에 복귀 경로", "좌표 투영은 가정 대신 데이터로 정립/자동맞춤". 부가: 이번에 `weight` import internal 버그(LESSON 2건 위)가 재발할 뻔했으나 기존 교훈이 **즉시 막음** — compound 작동 확인.

### 2026-06-04 — 모델 통합: 전처리는 모델 안에, feature 계약은 단일 출처로 동기화
- 무엇: 스쿼트 분류기 온디바이스 통합. MLP엔 feature 스케일링이 필요한데, 외부 스케일러를 `:app`이 재현하면 또 다른 드리프트원. 또 12-feature 정의가 `:core`(SquatFeatureExtractor)·`:app`·ml(FEATURE_COLUMNS) 3곳에 흩어짐.
- 근본 원인: 학습 전처리와 추론 전처리가 분리되면 어긋난다. 여러 층에 흩어진 계약은 한쪽만 바뀌면 조용히 깨진다.
- 고친 방법: Keras `Normalization`을 **모델 그래프 안에** adapt → 모델이 raw feature를 받아 내부 정규화(`:app`은 raw만 전달, 스케일러 재현 불필요). feature 이름/순서는 ml `feature_config`를 **canonical 단일 출처**로 삼아 `:core`/`:app`가 일치(테스트로 고정).
- 재발 방지(되먹임): 원칙 "전처리는 가능하면 모델 안에", "여러 층 계약은 단일 출처 + 각 층 일치 테스트". 잔여 리스크: Kaggle feature의 *계산 공식* 자체는 미상 → 실데이터(직접 촬영) 검증이 1순위.

### 2026-06-04 — "트리가 MLP보다 낫다"는 통념은 데이터에 따라 틀린다
- 무엇: 스쿼트 분류기를 TFLite로 올리려고 Keras MLP 재학습을 고민할 때, "tabular는 GBT가 MLP보다 낫다"는 통념으로 정확도 하락을 우려.
- 근본 원인: 통념을 데이터 특성(크기·균형) 무시하고 일반화. 이 데이터는 4.7만 행·완전 균형이라 MLP가 학습할 자료가 충분.
- 고친 방법: 캐시된 실제 데이터로 직접 측정 — HistGBT 0.943 vs MLP(32,16) 0.951 / (64,32) 0.963 (5-fold CV도 MLP 우위). 추측이 틀렸음 확인.
- 되먹임: `compound-engineering` 스킬에 "측정 > 추측" 원칙 명시. 모델 포맷 결정을 측정 기반으로 전환.

### 2026-06-04 — `:app`을 한 번도 컴파일 안 해 버그가 숨어 있었다
- 무엇: `ExerciseScreen`의 `import ...layout.weight`가 internal 심볼을 가리켜 컴파일 에러. main의 `:app`이 빌드 불가 상태였는데 여러 PR 동안 발견 안 됨.
- 근본 원인: Android SDK가 없어 `:app`을 한 번도 컴파일하지 않음 → "requires device"를 "검증 불가"로 확대 해석해 컴파일 검증까지 미룸.
- 고친 방법: SDK 생기자마자 `:app:assembleDebug` 실행 → 즉시 버그 발견·수정.
- 되먹임: `CLAUDE.md` 환경 현실에 "`:app`은 SDK 있으면 컴파일 검증 가능(런타임만 디바이스 필요)" 명시. compound 원칙 "일찍 검증". 향후 SDK 있는 환경에선 `:app:assembleDebug`를 검증 게이트에 포함.

### 2026-06-04 — 조건부 `:app` include가 env변수만 봐서 Android Studio에서 누락
- 무엇: `settings.gradle.kts`가 `ANDROID_HOME`/`ANDROID_SDK_ROOT`만 검사 → env를 안 쓰는 Android Studio(`local.properties`의 `sdk.dir`)에서 `:app`이 빠짐.
- 근본 원인: SDK 탐지 경로를 env 하나로 가정. Android Studio의 일반적 설정(local.properties)을 고려 안 함.
- 고친 방법: 탐지 순서를 env → `local.properties`(sdk.dir) → 기본 경로로 확장.
- 되먹임: 같은 패턴(환경 탐지는 여러 경로) 인지. `CLAUDE.md`에 탐지 순서 명시.

### 2026-06-03 — 세션 중 만든 하네스 산출물은 그 세션에서 즉시 활성화되지 않는다
- 무엇: 세션 시작 후 만든 커스텀 에이전트 타입과 네이티브 `EnterWorktree`가 같은 세션에서 인식 안 됨(레지스트리·git 상태가 세션 시작 시 캐시됨).
- 근본 원인: 에이전트 레지스트리/리포 감지가 세션 시작 시점 스냅샷.
- 고친 방법: general-purpose 서브에이전트가 에이전트 정의 `.md`+스킬을 읽어 그 역할 수행 + 브랜치 기반 격리(워크트리 대체).
- 되먹임: 다음 세션부터는 네이티브로 인식됨. 세션 중 하네스 변경 시 이 한계를 기억.
