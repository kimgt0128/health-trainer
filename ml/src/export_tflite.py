"""CLI: convert a trained Keras sequence model to TFLite + copy the artifact set.

Runs on Colab (tensorflow required). Emits the artifact contract validated by
healthtrainer_ml.contract: model.tflite + labels.json + feature_config.json.
"""
from __future__ import annotations

import argparse


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Export a Keras model to TFLite.")
    p.add_argument("--run-dir", required=True, help="dir with model.keras + configs")
    p.add_argument("--export-dir", required=True)
    p.add_argument("--float16", action="store_true", help="apply float16 quantization")
    args = p.parse_args(argv)

    import os
    import shutil

    os.makedirs(args.export_dir, exist_ok=True)

    import tensorflow as tf  # lazy: Colab only

    model = tf.keras.models.load_model(os.path.join(args.run_dir, "model.keras"))
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if args.float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    tflite = converter.convert()
    with open(os.path.join(args.export_dir, "model.tflite"), "wb") as fh:
        fh.write(tflite)

    for name in ("labels.json", "feature_config.json"):
        src = os.path.join(args.run_dir, name)
        if os.path.exists(src):
            shutil.copy(src, os.path.join(args.export_dir, name))
    print(f"exported tflite -> {args.export_dir}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
