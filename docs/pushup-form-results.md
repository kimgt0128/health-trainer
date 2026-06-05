# 푸쉬업 자세 분류 모델 — 결과 정리

스쿼트와 동일한 optional assist-model 구조를 푸쉬업으로 확장한 실험 결과. 카운팅은 rule
engine + state machine이 담당하고, 모델은 rep 종료 시점의 correct/incorrect **보조 힌트**다.

## 데이터셋
Kaggle `mohamadashrafsalama/pushup` — **영상 단위 라벨**(`Correct sequence` / `Wrong sequence`,
각 ~50개 mp4). CSV가 아니라 raw 영상이라, Colab에서 MediaPipe로 프레임별 랜드마크를 추출해
팔꿈치·몸통 각도를 계산하고 feature를 만든다.

## 방법론 — 정직한 실험 과정
1. **첫 학습 결과 accuracy 1.0** → 의심.
2. **원인: group leakage.** rep 단위 랜덤 split이라 *같은 영상에서 나온 rep*이 train/test에
   흩어졌다. 형제 rep은 거의 똑같아서 "본 적 있는 패턴"을 맞히니 100%가 거저 나온 것.
3. **수정:** 영상(clip) 단위 split + group k-fold CV → 누수 제거 → 정직한 수치.
4. **진단:** 영상 100개 중 47개가 rep 0개(segmentation이 깔끔한 한 사이클을 못 잡음, 특히
   팔을 끝까지 안 펴는 오자세 영상). 그래서 rep 데이터가 54개로 줄었다.
5. **2차 실험:** clip-level(영상 1개 = 1샘플)로 100개 영상 전부 사용.

## 결과 (group-by-clip split + grouped 5-fold CV, 누수 없음)

| 실험 | 단위 | n | CV accuracy | CV macro-F1 | held-out |
|---|---|--:|--:|--:|--:|
| **rep-level** (앱에 탑재) | rep | 54 | 0.835 ± 0.133 | 0.810 ± 0.157 | 0.909 |
| **clip-level** (비교 분석) | clip | 100 | **0.860 ± 0.037** | **0.858 ± 0.038** | 0.95 |

- **clip-level**이 정확도 ↑, **편차 ~3.5배 안정**(100 샘플 + 데이터셋 라벨 단위와 일치).
- **rep-level**은 앱 추론과 분포가 일치(앱은 한 rep이 끝날 때 같은 feature를 만들어 모델에 넘김).

## 결론
- **rep-level = 앱에 붙이는 baseline.** 앱 추론(PushUpFeatureExtractor, rep 단위)과 입력 분포가
  같아 그대로 탑재 가능. CV 0.835 ± 0.133.
- **clip-level = 데이터셋에 더 맞는 비교 실험.** CV 0.860 ± 0.037로 더 좋지만, 앱의 rep-level
  추론과 granularity가 달라 앱엔 직접 붙이지 않고 분석/보고용으로 둔다.
- **segmentation 임계값을 데이터에 맞춰 낮추는 건 하지 않음** — 앱 rule/state machine과의 계약이
  흐려지기 때문(학습이 데이터셋에 과적합된 전처리를 배우게 됨).

## 한계 / 다음 단계
- 데이터가 작다(영상 100개). 앱으로 직접 촬영한 **rep 단위 라벨 데이터**를 모으면 학습 단위와
  앱 추론이 완전히 일치한다(가장 좋은 장기 해법).
- TFLite export + 앱 `assets/models/` 통합은 다음 단계. 모델이 없어도 앱은 **rules-only로 정상
  동작**하며 crash하지 않는다.

## 발표 한 줄 요약
> "학습 결과가 100%로 나와 의심했고, 원인이 group leakage임을 찾아 영상 단위 split + group CV로
> 정직한 수치를 냈다. 데이터셋 라벨 단위(clip)와 앱 추론 단위(rep) 두 가지로 비교했고 —
> clip 0.860±0.037, rep 0.835±0.133 — 앱엔 추론과 일치하는 rep-level을 탑재한다."
