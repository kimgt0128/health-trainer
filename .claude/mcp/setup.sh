#!/usr/bin/env bash
# Project-scoped MCP environment setup for the Health Trainer model-training track.
# Idempotent. Creates a local venv for the Jupyter MCP and pre-fetches the MCP servers.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$HERE/.venv"

echo "==> creating local Jupyter venv at $VENV"
uv venv --python 3.11 "$VENV"
uv pip install --python "$VENV/bin/python" jupyterlab ipykernel

echo "==> pre-fetching MCP servers (uvx caches them)"
# Datalayer Jupyter MCP (local kernel, CPU verification)
uvx jupyter-mcp-server@latest --help >/dev/null 2>&1 || \
  echo "   (jupyter-mcp-server will be fetched on first MCP use)"
# Google official Colab MCP (browser-proxy, semi-manual)
echo "   colab-mcp is fetched on first use: uvx git+https://github.com/googlecolab/colab-mcp"

cat <<'EOF'

==> done.
Next:
  1. Restart Claude Code and approve the project MCP servers (.mcp.json).
  2. For jupyter-local (CPU), start a server with the matching token:
       .claude/mcp/.venv/bin/jupyter lab --no-browser --port 8888 \
           --IdentityProvider.token=healthtrainer
  3. For colab (GPU), open a logged-in Colab tab and click "Connect" when prompted.
     Free-tier Colab cannot be driven head-lessly (see .claude/mcp/README.md).
EOF
