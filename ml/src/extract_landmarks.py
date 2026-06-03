"""CLI: extract MediaPipe landmarks from videos -> per-clip landmark JSON.

Runs on Colab (mediapipe required). Mirrors the command in docs/colab-drive-workflow.md.
"""
from __future__ import annotations

import argparse


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Extract pose landmarks from videos.")
    p.add_argument("--input-dir", required=True, help="dir of .mp4 clips")
    p.add_argument("--output-dir", required=True, help="dir for per-clip landmark JSON")
    p.add_argument("--model", default="pose_landmarker_lite.task", help="MediaPipe .task")
    args = p.parse_args(argv)

    # Heavy deps imported lazily (mediapipe). Not available on this machine.
    from healthtrainer_ml.landmarks import extract_frames_from_video

    import glob
    import json
    import os

    os.makedirs(args.output_dir, exist_ok=True)
    clips = sorted(glob.glob(os.path.join(args.input_dir, "*.mp4")))
    for clip in clips:
        frames = extract_frames_from_video(clip, args.model)
        stem = os.path.splitext(os.path.basename(clip))[0]
        with open(os.path.join(args.output_dir, f"{stem}.json"), "w") as fh:
            json.dump(frames, fh)
    print(f"extracted landmarks for {len(clips)} clips -> {args.output_dir}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
