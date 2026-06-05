# Lessons (Compound Engineering)

`compound-engineering` 스킬의 기록부. 실수·교정·발견을 근본 원인까지 적고, 어떤 규약/하네스/테스트로 재발을 막았는지(되먹임)를 남긴다. 최신이 위로.

### 2026-06-05 — 새 데이터셋을 "이전과 같은 형태"로 가정해 어댑터를 먼저 만들었다
- 무엇: 푸쉬업 Kaggle `mohamadashrafsalama/pushup`을 스쿼트처럼 미리 계산된 feature CSV로 가정 → CSV 로더(`pushup_pose_dataset.py`)+학습 스크립트+Colab 노트북을 그 가정 위에 구축. Colab 학습이 `'pushup/pushup_features.csv' not present`로 실패 — 실제론 **raw 영상**(Correct/Wrong sequence/*.mp4)이라 파이프라인을 영상→landmark→rep-feature로 통째로 재작성.
- 근본 원인: 직전 데이터셋(스쿼트=feature CSV)의 형태를 새 데이터셋에 **검증 없이 일반화**. "SCHEMA UNVERIFIED" TODO만 달고도 그 위에 전체 코드를 쌓음(측정 전에 추측으로 구현).
- 고친 방법: 노트북에 INSPECT 셀(파일·컬럼·라벨 출력)을 넣어 실제 구조 확인 → 영상용 `pushup_video_features.py`(`segment_reps`+`rep_features`, `:core` 미러) + `extract_pushup_frames`/`build_pushup_dataframe` 재작성(commit `7626885`).
- 재발 방지(되먹임): `ml-data-engineer` 에이전트에 "새 데이터셋은 어댑터/FEATURE_COLUMNS/학습 스크립트를 쓰기 **전에** 실제 구조(파일·컬럼·형식)를 inspect로 먼저 확인하고, 이전 데이터셋 형태를 가정해 미러링하지 않는다" 규칙 추가. 데이터셋 형태는 (raw 영상 / landmark CSV / feature CSV)로 제각각임을 명시. (이 스킬 원칙 "측정 > 추측"의 구체 사례.)

### 2026-06-05 — replay 런타임 버그(뒤로가기 없음·3D 왜곡)는 디바이스 런에서야 드러남
- 무엇: `:app`은 컴파일만 검증돼서, replay 화면에 **뒤로가기 경로가 없고**(갇힘) 3D 투영(고정 scale + y-up 가정 + 강한 z-skew)이 측면 스쿼트에서 **왜곡/반전**.
- 근본 원인: UI 네비게이션 완결성과 좌표/투영 정확성은 컴파일러가 못 잡는다 → 런타임 검증 필요. MediaPipe world y축 방향을 단정한 게 화근.
- 고친 방법: `BackHandler`+버튼으로 복귀; 렌더링을 **자동맞춤(bbox) + 해부학적 정립(NOSE를 위로) + z-skew 축소**로 좌표 가정과 무관하게 견고화.
- 되먹임: 원칙 "모든 화면에 복귀 경로", "좌표 투영은 가정 대신 데이터로 정립/자동맞춤". 부가: 이번에 `weight` import internal 버그(LESSON 2건 위)가 재발할 뻔했으나 기존 교훈이 **즉시 막음** — compound 작동 확인.

### 2026-06-04 — 푸쉬업 assist는 닫히는 TOP 프레임이 아니라 rep 전체로 판정
- 무엇: 스쿼트 frame-level assist 구조를 푸쉬업에 그대로 쓰려다, 모델 호출 시점(rep close)의 단일 프레임이 팔 펴진 **TOP**라 자세 판정에 무의미함을 발견.
- 근본 원인: 모델 호출은 이미 rep close에만 일어나지만 입력이 "닫히는 한 프레임"이었다. 푸쉬업 form은 rep의 min/max elbow·body-line broken 비율·down-phase 비율 등 **집계**라야 의미가 있다.
- 고친 방법: frame-level `ExerciseFeatureExtractor`는 그대로 두고 `RepFeatureExtractor`(rep의 `ExerciseFeedback` 리스트+duration→벡터)를 additive로 추가. `PushUpRule`이 이미 내는 프레임별 `elbowAngle`/`bodyLineAngle` metrics를 재사용 → 각도 재계산 없음(드리프트 0).
- 되먹임: `CLAUDE.md`에 "assist 모델 확장 패턴(frame=`ExerciseFeatureExtractor` / rep=`RepFeatureExtractor`, 레지스트리 한 줄 등록)" 규약화. 향후 종목은 둘 중 맞는 seam을 고른다.

### 2026-06-04 — 워크트리가 origin/main보다 21커밋 뒤처져 참조 파일이 없었다
- 무엇: 푸쉬업 확장에 필요한 스쿼트 assist 파일(`SquatFeatureExtractor`/`FormClassifierRegistry` 등)이 워크트리에 없었다. 그 앱 통합은 PR #8로 origin/main에 들어가 있었고, 워크트리는 옛 main을 머지한 상태였다.
- 근본 원인: 장수 워크트리가 main 진척을 안 따라감. 한 기능이 여러 브랜치에 걸칠 때(앱 통합=main, ml 트랙=feature branch) 확인 없이 작업을 시작.
- 고친 방법: 작업 전 `git fetch` + 참조 파일 존재 + `origin/main..HEAD` 거리를 점검하고, origin/main을 워크트리에 머지(추가형 CLAUDE.md 충돌은 union).
- 되먹임: 크로스-브랜치 기능 확장 전 "참조 파일이 이 워크트리에 실재하는가 + origin/main과의 거리"를 먼저 확인. (L1 "env의 git 플래그 불신"의 확장.)

### 2026-06-04 — 워크트리 :app 빌드엔 ANDROID_HOME이 필요(settings 탐지 ≠ AGP 해석)
- 무엇: 워크트리에서 `./gradlew :app:assembleDebug`가 "SDK location not found"로 실패. `:core:test`는 통과.
- 근본 원인: `settings.gradle.kts`의 SDK 탐지(기본 경로 포함)는 `:app` *include 여부*만 결정한다. AGP의 실제 `sdk.dir` 해석엔 env `ANDROID_HOME` 또는 `local.properties`가 필요한데 워크트리엔 둘 다 없다(`local.properties`는 gitignore).
- 고친 방법: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- 되먹임: 워크트리에서 `:app`을 빌드할 땐 `ANDROID_HOME`을 지정한다(검증 게이트/`core-build-test` 스킬에 반영 후보).

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
