"""CLI: landmark JSON -> normalized feature windows + RandomForest summary vectors.

Uses only light deps (numpy, pyyaml) + the tested core. Input JSON per clip is a list of
frames, each ``{name: [x, y, z, visibility]}``, plus a top-level ``label`` (int).
"""
from __future__ import annotations

import argparse


def _load_feature_config(path):
    import yaml
    from healthtrainer_ml.features import FeatureConfig

    with open(path) as fh:
        d = yaml.safe_load(fh)
    return FeatureConfig(
        landmarks=d["landmarks"],
        per_landmark_features=d.get("per_landmark_features"),
        angle_features=d["angle_features"],
        sequence_length=d.get("sequence_length", 60),
        normalization=d.get("normalization", {"center": "hip_center", "scale": "shoulder_width"}),
    )


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Build feature windows from landmark JSON.")
    p.add_argument("--landmarks-dir", required=True)
    p.add_argument("--config", required=True, help="feature config YAML")
    p.add_argument("--out", required=True, help="output .npz")
    p.add_argument("--stride", type=int, default=30)
    args = p.parse_args(argv)

    import glob
    import json
    import os

    import numpy as np

    from healthtrainer_ml.normalize import normalize_frame
    from healthtrainer_ml.features import frame_feature_vector
    from healthtrainer_ml.windowing import make_windows
    from healthtrainer_ml.summarize import summary_vector

    cfg = _load_feature_config(args.config)
    summaries, labels = [], []
    for clip_path in sorted(glob.glob(os.path.join(args.landmarks_dir, "*.json"))):
        with open(clip_path) as fh:
            clip = json.load(fh)
        label = clip["label"]
        frames = [{k: tuple(v) for k, v in fr.items()} for fr in clip["frames"]]
        vecs = [frame_feature_vector(normalize_frame(fr), cfg) for fr in frames]
        for window in make_windows(vecs, length=cfg.sequence_length, stride=args.stride):
            summaries.append(summary_vector(window))
            labels.append(label)

    np.savez(args.out, X=np.asarray(summaries, dtype=float), y=np.asarray(labels))
    print(f"wrote {len(summaries)} windows -> {args.out}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
