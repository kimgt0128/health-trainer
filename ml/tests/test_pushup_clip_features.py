"""clip-level feature aggregation (additional experiment): a WHOLE clip -> one row, using the
same 10 features as the rep-level path. Pure (no mediapipe/cv2)."""
from healthtrainer_ml.pushup_video_features import clip_features, rep_features
from healthtrainer_ml.pushup_pose_dataset import FEATURE_COLUMNS


def _f(elbow, body_line, ts_ms, visible=True):
    return {"elbow": elbow, "body_line": body_line, "visible": visible, "ts_ms": ts_ms}


def test_clip_features_aggregates_the_whole_clip_not_per_rep():
    # Two full down-cycles. The rep-level path would segment 2 reps; clip_features treats the
    # entire clip as ONE unit, so min/max/range/duration span the whole thing.
    frames = [
        _f(170, 175, 0), _f(120, 170, 100), _f(85, 168, 200), _f(120, 170, 300), _f(170, 175, 400),
        _f(120, 170, 500), _f(90, 169, 600), _f(120, 170, 700), _f(170, 175, 800),
    ]
    feats = clip_features(frames)
    assert feats is not None and len(feats) == len(FEATURE_COLUMNS)
    i = FEATURE_COLUMNS.index
    assert feats[i("min_elbow_angle")] == 85.0    # cycle-1 bottom, over the WHOLE clip
    assert feats[i("max_elbow_angle")] == 170.0
    assert feats[i("elbow_angle_range")] == 85.0
    assert feats[i("rep_duration_ms")] == 800.0   # whole-clip span, not one rep


def test_clip_features_is_rep_features_over_all_frames():
    frames = [_f(160, 170, 0), _f(95, 165, 100), _f(160, 170, 200)]
    assert clip_features(frames) == rep_features(frames)


def test_clip_features_none_when_no_elbow_sample():
    assert clip_features([_f(None, None, 0, visible=False)]) is None
