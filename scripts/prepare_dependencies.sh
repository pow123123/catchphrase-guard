#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS_DIR="${ROOT_DIR}/app/libs"
MODEL_ASSET_DIR="${ROOT_DIR}/app/src/main/assets/model"

mkdir -p "${LIBS_DIR}" "${MODEL_ASSET_DIR}"

echo "=== [1/2] Preparing sherpa-onnx Android AAR ==="
if [ -s "${LIBS_DIR}/sherpa-onnx.aar" ] && unzip -l "${LIBS_DIR}/sherpa-onnx.aar" | grep -q "libsherpa-onnx-jni.so"; then
  echo "Cached sherpa-onnx.aar is valid, skipping download."
else
  rm -f "${LIBS_DIR}/sherpa-onnx.aar"
  AAR_URL=""

  # 1) Try official k2-fsa/sherpa-onnx GitHub Releases first
  GH_HEADERS=(-H "Accept: application/vnd.github+json")
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    GH_HEADERS+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  fi

  LATEST_RELEASE_JSON="$(curl -fsSL "${GH_HEADERS[@]}" "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/latest" || echo "{}")"
  LATEST_VER="$(echo "${LATEST_RELEASE_JSON}" | jq -r '.tag_name // empty' | sed 's/^v//')"

  AAR_URL="$(echo "${LATEST_RELEASE_JSON}" \
    | jq -r '.assets[]? | select(.name | test("^sherpa-onnx-v?[0-9]+\\.[0-9]+\\.[0-9]+\\.aar$")) | .browser_download_url' \
    | head -n 1 || true)"

  # 1b) Direct version lookup on Hugging Face if latest GitHub tag matches
  if { [ -z "${AAR_URL}" ] || [ "${AAR_URL}" = "null" ]; } && [ -n "${LATEST_VER}" ]; then
    CANDIDATE_HF="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-${LATEST_VER}.aar"
    if curl -fsSLI "${CANDIDATE_HF}" >/dev/null 2>&1; then
      AAR_URL="${CANDIDATE_HF}"
    fi
  fi

  # 2) Fallback to csukuangfj/sherpa-onnx-libs on Hugging Face
  if [ -z "${AAR_URL}" ] || [ "${AAR_URL}" = "null" ]; then
    HF_PATH="$(curl -fsSL "https://huggingface.co/api/models/csukuangfj/sherpa-onnx-libs/tree/main/android/aar" \
      | jq -r '.[].path | select(test("^android/aar/sherpa-onnx-[0-9]+\\.[0-9]+\\.[0-9]+\\.aar$"))' \
      | sort -V | tail -n 1 || true)"
    if [ -n "${HF_PATH}" ] && [ "${HF_PATH}" != "null" ]; then
      AAR_URL="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/${HF_PATH}"
    fi
  fi

  # 3) Fallback to csukuangfj2/sherpa-onnx-libs mirror on Hugging Face
  if [ -z "${AAR_URL}" ] || [ "${AAR_URL}" = "null" ]; then
    HF2_PATH="$(curl -fsSL "https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-libs/tree/main/android/aar" \
      | jq -r '.[].path | select(test("^android/aar/sherpa-onnx-[0-9]+\\.[0-9]+\\.[0-9]+\\.aar$"))' \
      | sort -V | tail -n 1 || true)"
    if [ -n "${HF2_PATH}" ] && [ "${HF2_PATH}" != "null" ]; then
      AAR_URL="https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/${HF2_PATH}"
    fi
  fi

  if [ -z "${AAR_URL}" ] || [ "${AAR_URL}" = "null" ]; then
    echo "ERROR: Could not resolve sherpa-onnx.aar download URL." >&2
    exit 1
  fi

  echo "Downloading sherpa-onnx AAR from: ${AAR_URL}"
  curl -fL --retry 3 --retry-delay 3 -o "${LIBS_DIR}/sherpa-onnx.aar" "${AAR_URL}"

  unzip -l "${LIBS_DIR}/sherpa-onnx.aar" | grep -q "jni/arm64-v8a/libsherpa-onnx-jni.so"
  echo "Verified sherpa-onnx.aar successfully."
fi

echo "=== [2/2] Preparing Chinese offline streaming ASR model ==="
if [ -s "${MODEL_ASSET_DIR}/encoder.onnx" ] && \
   [ -s "${MODEL_ASSET_DIR}/decoder.onnx" ] && \
   [ -s "${MODEL_ASSET_DIR}/joiner.onnx" ] && \
   [ -s "${MODEL_ASSET_DIR}/tokens.txt" ]; then
  echo "Cached offline ASR model assets are complete, skipping download."
else
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_DIR}"' EXIT

  MODEL_NAME="sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
  MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/${MODEL_NAME}.tar.bz2"

  echo "Downloading Chinese streaming model from: ${MODEL_URL}"
  curl -fL --retry 3 --retry-delay 3 -o "${TMP_DIR}/model.tar.bz2" "${MODEL_URL}"
  tar -xjf "${TMP_DIR}/model.tar.bz2" -C "${TMP_DIR}"

  EXTRACTED_DIR="${TMP_DIR}/${MODEL_NAME}"
  cp "${EXTRACTED_DIR}/encoder-epoch-99-avg-1.int8.onnx" "${MODEL_ASSET_DIR}/encoder.onnx"
  cp "${EXTRACTED_DIR}/decoder-epoch-99-avg-1.onnx" "${MODEL_ASSET_DIR}/decoder.onnx"
  cp "${EXTRACTED_DIR}/joiner-epoch-99-avg-1.int8.onnx" "${MODEL_ASSET_DIR}/joiner.onnx"
  cp "${EXTRACTED_DIR}/tokens.txt" "${MODEL_ASSET_DIR}/tokens.txt"
fi

ls -lh "${LIBS_DIR}/sherpa-onnx.aar" "${MODEL_ASSET_DIR}/"
echo "All dependencies ready!"
