# Project-scoped MCP — Colab + local Jupyter

These MCP servers are registered in the repo-root `.mcp.json`, so they load **only for
this project**. After pulling this branch, restart Claude Code and approve the servers
when prompted (project MCP servers require a one-time trust approval).

## What each server does (and its honest limits)

### `colab` — Google official Colab MCP  *(semi-manual)*
- Package: `uvx git+https://github.com/googlecolab/colab-mcp` (announced Mar 2026).
- Lets an agent create/run cells on a **real Colab runtime, including free GPU**.
- **Load-bearing manual step:** you must keep a **logged-in Colab tab open** and click
  **"Connect"** when the tab prompts. The headless `--enable-runtime` mode is *not*
  available to free/non-Google users, so fully unattended Colab driving is **not possible**
  here. Treat the Colab GPU run as a human-in-the-loop step.
- ToS: free Colab is for interactive use and runtimes can drop at any time — don't build an
  unattended pipeline on it. (See `ml/LESSONS.md` L3.)

### `jupyter-local` — Datalayer Jupyter MCP  *(autonomous, CPU only)*
- Package: `uvx jupyter-mcp-server@latest`, pointed at a **local** JupyterLab kernel.
- Use for autonomous, low-fragility **code-correctness** checks on CPU (no GPU). This is the
  reliable leg; the unit-test suite (`ml/`) already covers the deterministic core, so this
  MCP is mainly for interactive notebook-style verification.

## Setup

```bash
bash .claude/mcp/setup.sh        # creates .claude/mcp/.venv with jupyterlab, pre-fetches MCPs
# then, to use jupyter-local, start a local server with the matching token:
.claude/mcp/.venv/bin/jupyter lab --no-browser --port 8888 \
    --IdentityProvider.token=healthtrainer
```

The token `healthtrainer` must match `JUPYTER_TOKEN` in `.mcp.json`.

## Can Claude drive Colab GPU from here?
**Partially.** Configured: yes. Autonomous/headless on free Colab: **no** — it needs your
open browser tab + a "Connect" click. For genuinely autonomous GPU runs the honest options
are Modal or Colab-Enterprise/Vertex (paid), which are out of scope for this MVP.
