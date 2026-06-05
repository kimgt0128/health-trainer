# Push-Up Assist Model — Implementation Plan

> 스쿼트 optional assist-model 구조를 푸쉬업으로 확장. 카운팅은 rule/state machine이 그대로 담당하고
> 모델은 rep 종료 시점의 correct/incorrect **보조 힌트**일 뿐. 모델 없이도 앱은 rules-only로 동작.

## 설계 결정 — frame-level vs rep-level (점3)

**선택: rep-level seam을 추가한다.** 이유:
- assist는 이미 `FramePipeline`에서 **rep 닫힐 때만** 호출된다(`closedRep != null`). "rep 종료 시 표시" 제약 충족.
- 하지만 현재는 **닫히는 단일 프레임**을 넘긴다. rep는 `TOP→BOTTOM→TOP`에서 닫히므로 그 프레임은 팔 펴진 **TOP** — 푸쉬업 자세 판정엔 부적합(틀린 순간을 분류).
- `PushUpRule.evaluate`가 **프레임마다 `metrics["elbowAngle"]`/`["bodyLineAngle"]` + phase + hardFailures**를 이미 낸다. rep의 피드백 리스트를 재사용하면 각도 재계산 없이(=드리프트 0) rep-level feature를 만든다.
- frame-level(squat)은 **건드리지 않고** rep-level 경로를 **추가** → 비파괴 + 확장.

향후 종목: **frame-level이면 `ExerciseFeatureExtractor`, rep-level이면 `RepFeatureExtractor`** 중 맞는 seam을 구현하고 `FormClassifierRegistry`에 한 줄 등록한다.

## rep-level feature 계약 (NAMES + ORDER — :core ↔ ml 일치 필수)

```
min_elbow_angle, max_elbow_angle, mean_elbow_angle, elbow_angle_range,
min_body_line_angle, mean_body_line_angle, body_line_broken_ratio,
visible_frame_ratio, rep_duration_ms, down_phase_ratio
```
rep의 `List<ExerciseFeedback>` + `repDurationMs`로부터:
- elbow* : `metrics["elbowAngle"]` 모음의 min/max/mean, range=max−min
- *_body_line_angle : `metrics["bodyLineAngle"]` 모음의 min/mean
- body_line_broken_ratio : `PUSH_UP_BODY_LINE_BROKEN ∈ hardFailures` 프레임 수 / **visible**(bodyLineAngle 있는) 프레임 수
- visible_frame_ratio : visible(elbowAngle 있는) 프레임 수 / 전체 프레임 수
- rep_duration_ms : `repDurationMs.toFloat()`
- down_phase_ratio : `phase == BOTTOM` 프레임 수 / 전체 프레임 수
- **게이트:** elbowAngle 표본이 없으면(=요청 joint 모두 가려짐) `null` 반환 → 모델 조용히 defer.

라벨(binary, 출력 순): `["correct", "incorrect"]`. 모델 asset: `models/pushup_form.tflite`(gitignore, 없으면 inert).

## 구현 (모두 additive — 스쿼트 비파괴)

### :core (kotlin-tdd-engineer)
1. `RepRecordData` += `frameFeedbacks: List<ExerciseFeedback> = emptyList()` (transient). `RepStateMachine`이 close 시 `buffer.toList()`를 담는다(persisted `RepRecord`는 불변).
2. `SetTracker` += `lastClosedRepFrameFeedbacks: List<ExerciseFeedback>?`(rep 닫힐 때 채움, 아니면 null) — 또는 `onFrame`이 닫은 rep의 피드백을 노출.
3. `core/features/RepFeatureExtractor.kt` (신규 interface): `exerciseType`, `featureNames()`, `extract(frameFeedbacks, repDurationMs): FloatArray?`.
4. `core/features/PushUpFeatureExtractor.kt : RepFeatureExtractor` — 위 계약 구현 + companion `FEATURE_NAMES`.
5. 테스트: `PushUpFeatureExtractorTest`(합성 피드백→기대 feature·null 게이트), 트래커가 rep 피드백 노출하는지 테스트.

### :app (android-platform-engineer)
6. `FormClassifierRegistry.Spec` += `repExtractor: RepFeatureExtractor? = null`(frame `extractor`와 정확히 하나만 non-null). PUSH_UP 등록: `repExtractor=PushUpFeatureExtractor(), modelAsset="models/pushup_form.tflite", labels=["correct","incorrect"]`. `repExtractorFor(type)` 추가.
7. `FramePipeline` += `repFeatureExtractor: RepFeatureExtractor?`. rep close 시: repExtractor 있으면 `setTracker.lastClosedRepFrameFeedbacks` + `(end−start)`로 feature → classify → `fuseAssist`. 없으면 기존 frame 경로. `fuseAssist`는 그대로(`correct` 억제 + confidence 게이트) → "incorrect"만 표시.
8. `MainViewModel.pipelineFor` += `repFeatureExtractor = FormClassifierRegistry.repExtractorFor(type)`.
9. `FeedbackText.modelFormLabel` += `"incorrect" -> "푸쉬업 자세 확인 필요 (모델)"`.

### ml track (ml-data-engineer)  — 로컬 deterministic, heavy는 lazy
10. `ml/src/healthtrainer_ml/pushup_pose_dataset.py` — squat 미러: `DATASET_HANDLE="mohamadashrafsalama/pushup"`, `FEATURE_COLUMNS`=위 10개, `LABELS={0:"correct",1:"incorrect"}`, `validate/split/feature_config`. **⚠️ 데이터셋 스키마 미검증** — 실제 컬럼이 다르거나 per-frame raw면 Colab에서 확인 후 집계 단계 필요(코드 주석 + docs에 TODO 명시).
11. `ml/src/train_pushup_form_classifier.py` — train_squat 미러: HGB 기본(squat 교훈 L5), binary, 산출물 4종 `runs/pushup_form_classifier_v1/`.
12. `ml/configs/pushup_form_classifier.yaml`, CLI smoke 목록에 추가, config/contract 테스트.

## 검증
- `./gradlew :core:test :app:assembleDebug`  (SDK 있음 → 실제 컴파일/APK)
- `cd ml && .venv/bin/pytest`
- 모델 artifact 없이도 빌드/실행(rules-only). missing model로 crash 금지.

## 기록 (compound-engineering)
- `docs/LESSONS.md`: ① frame-vs-rep 결정(닫히는 TOP 프레임 문제) ② 워크트리가 origin/main보다 21커밋 뒤처져 참조파일 부재 → 작업 전 sync 확인 ③ rep 피드백 재사용으로 드리프트 회피.
- `CLAUDE.md`: "assist 모델 확장 패턴"(frame `ExerciseFeatureExtractor` / rep `RepFeatureExtractor` + 레지스트리 한 줄 등록) 규약 + 변경이력 1행.
