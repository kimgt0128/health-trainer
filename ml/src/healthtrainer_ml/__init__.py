"""Health Trainer model-training track.

Pure-Python, CPU-testable feature engineering that mirrors the Android ``:core``
normalization/angle contract, plus a RandomForest baseline. Heavy Colab-only
dependencies (mediapipe, tensorflow) are imported lazily inside the functions that
need them, so importing this package never requires them.
"""

__version__ = "0.1.0"
