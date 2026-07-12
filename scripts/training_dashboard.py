#!/usr/bin/env python3
"""Training Dashboard Backend — Flask + SSE real-time log streaming."""

import json
import os
import re
import subprocess
import threading
import time
from pathlib import Path

from flask import Flask, Response, jsonify, render_template_string, request

app = Flask(__name__, static_folder=None)

BASE = Path(__file__).parent
MODELS_CFG = {
    "url": {
        "name": "URL 检测模型",
        "log": BASE / "training_url.log",
        "mode": "url",
        "fresh": True,
        "epochs": 20,
        "color": "#3b82f6",
    },
    "english": {
        "name": "英文 SMS 模型",
        "log": BASE / "training_english.log",
        "mode": "english",
        "fresh": False,
        "epochs": 10,
        "color": "#10b981",
    },
    "sms": {
        "name": "中文 SMS 模型",
        "log": BASE / "training_sms.log",
        "mode": "sms",
        "fresh": True,
        "epochs": 20,
        "color": "#f59e0b",
    },
    "chinese": {
        "name": "中文文本模型",
        "log": BASE / "training_chinese.log",
        "mode": "chinese",
        "fresh": True,
        "epochs": 20,
        "color": "#ef4444",
    },
}

state = {
    mid: {
        "status": "idle",
        "pid": None,
        "progress": 0.0,
        "epoch": 0,
        "total_epochs": cfg["epochs"],
        "acc": 0.0,
        "loss": 0.0,
        "val_acc": 0.0,
        "val_loss": 0.0,
        "batch": 0,
        "total_batches": 0,
        "error": None,
        "started_at": None,
        "finished_at": None,
    }
    for mid, cfg in MODELS_CFG.items()
}

_lock = threading.Lock()
_log_readers = {}  # model_id -> {"offset": int, "lines": list}


# ── helpers ─────────────────────────────────────────────────


def _parse_progress(line: str):
    """Parse a tqdm-style log line for progress info."""
    m = re.search(r"Epoch (\d+)/(\d+):\s*(\d+)%", line)
    if m:
        epoch, total_ep, pct = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return {"epoch": epoch, "total_epochs": total_ep, "pct": pct / 100.0}

    m = re.search(r"Epoch (\d+)/(\d+) \|", line)
    if m:
        return {"epoch": int(m.group(1)), "total_epochs": int(m.group(2))}
    return None


def _parse_metrics(line: str):
    """Extract acc/loss from epoch summary or tqdm line."""
    r = {}
    m = re.search(r"acc=([\d.]+)", line)
    if m:
        r["acc"] = float(m.group(1))
    m = re.search(r"loss=([\d.]+)", line)
    if m:
        r["loss"] = float(m.group(1))
    m = re.search(r"Train Loss: ([\d.]+)", line)
    if m:
        r["loss"] = float(m.group(1))
    m = re.search(r"Train Acc: ([\d.]+)", line)
    if m:
        r["acc"] = float(m.group(1))
    m = re.search(r"Val Loss: ([\d.]+)", line)
    if m:
        r["val_loss"] = float(m.group(1))
    m = re.search(r"Val Acc: ([\d.]+)", line)
    if m:
        r["val_acc"] = float(m.group(1))
    return r


def _parse_batch(line: str):
    """Extract batch/total from tqdm line like '1431/1609'."""
    m = re.search(r"(\d+)/(\d+)", line)
    if m:
        return int(m.group(1)), int(m.group(2))
    return None, None


def _parse_model_output(text: str, mid: str):
    """Parse log text to update model state."""
    s = state[mid]
    for line in text.splitlines():
        p = _parse_progress(line)
        if p:
            s["epoch"] = p["epoch"]
            s["total_epochs"] = p.get("total_epochs", s["total_epochs"])
            if "pct" in p:
                s["progress"] = p["pct"]

        m = _parse_metrics(line)
        if "acc" in m:
            s["acc"] = m["acc"]
        if "loss" in m:
            s["loss"] = m["loss"]
        if "val_acc" in m:
            s["val_acc"] = m["val_acc"]
        if "val_loss" in m:
            s["val_loss"] = m["val_loss"]

        b, tb = _parse_batch(line)
        if b is not None:
            s["batch"] = b
        if tb is not None:
            s["total_batches"] = tb

        if "Accuracy:" in line:
            m_acc = re.search(r"Accuracy:\s*(\d+)/(\d+)\s*=\s*([\d.]+%)", line)
            if m_acc:
                s["acc"] = float(m_acc.group(3).replace("%", "")) / 100.0


def _run_training(mid: str):
    """Run training in a background thread."""
    cfg = MODELS_CFG[mid]
    log_path = cfg["log"]

    # Clear previous log if fresh
    if cfg["fresh"] and log_path.exists():
        log_path.write_text("")

    cmd = [
        "python",
        str(BASE / "train_phishing_model.py"),
        "--mode",
        cfg["mode"],
    ]
    if cfg["fresh"]:
        cmd.append("--fresh")
    else:
        # Remove --fresh to resume from checkpoint
        pass

    with _lock:
        state[mid]["status"] = "running"
        state[mid]["pid"] = None
        state[mid]["error"] = None
        state[mid]["started_at"] = time.time()

    proc = subprocess.Popen(
        cmd, cwd=str(BASE), stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    with _lock:
        state[mid]["pid"] = proc.pid

    # Read output line by line
    offset = 0
    for line_bytes in iter(proc.stdout.readline, b""):
        line = line_bytes.decode("utf-8", errors="replace")

        # Append to log file
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(line)

        # Update state from this line
        with _lock:
            _parse_model_output(line, mid)

        offset += len(line_bytes)

    proc.wait()

    with _lock:
        if proc.returncode == 0:
            state[mid]["status"] = "completed"
        else:
            state[mid]["status"] = "failed"
            state[mid]["error"] = f"exit code {proc.returncode}"
        state[mid]["finished_at"] = time.time()
        state[mid]["progress"] = 1.0


def _start_all_sequential():
    """Run all models one after another in a background thread."""

    def _runner():
        for mid in ["url", "english", "sms", "chinese"]:
            with _lock:
                if state[mid]["status"] == "completed":
                    continue
            _run_training(mid)

    t = threading.Thread(target=_runner, daemon=True)
    t.start()
    return t


# ── API routes ──────────────────────────────────────────────


@app.route("/")
def index():
    return render_template_string(HTML)


@app.route("/api/status")
def api_status():
    with _lock:
        return jsonify(
            {
                "models": {
                    mid: {
                        k: v
                        for k, v in s.items()
                        if k != "pid"  # don't leak pid
                    }
                    for mid, s in state.items()
                },
                "config": {
                    mid: {
                        "name": cfg["name"],
                        "color": cfg["color"],
                        "fresh": cfg["fresh"],
                    }
                    for mid, cfg in MODELS_CFG.items()
                },
            }
        )


@app.route("/api/start/<mid>")
def api_start(mid):
    if mid not in MODELS_CFG:
        return jsonify({"error": f"unknown model {mid}"}), 400
    with _lock:
        if state[mid]["status"] == "running":
            return jsonify({"error": "already running"}), 409
    t = threading.Thread(target=_run_training, args=(mid,), daemon=True)
    t.start()
    return jsonify({"status": "started", "model": mid})


@app.route("/api/start-all")
def api_start_all():
    for mid in MODELS_CFG:
        with _lock:
            if state[mid]["status"] == "running":
                return jsonify({"error": f"{mid} is already running"}), 409
    _start_all_sequential()
    return jsonify({"status": "started", "models": list(MODELS_CFG.keys())})


@app.route("/api/logs/<mid>")
def api_logs(mid):
    if mid not in MODELS_CFG:
        return jsonify({"error": "unknown model"}), 400
    log_path = MODELS_CFG[mid]["log"]
    if not log_path.exists():
        return jsonify({"lines": [], "offset": 0})
    text = log_path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    return jsonify({"lines": lines[-200:], "total": len(lines)})


@app.route("/api/stream/<mid>")
def api_stream(mid):
    if mid not in MODELS_CFG:
        return "", 404

    def generate():
        last_size = 0
        while True:
            log_path = MODELS_CFG[mid]["log"]
            try:
                if log_path.exists():
                    sz = log_path.stat().st_size
                    if sz > last_size:
                        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                            f.seek(last_size)
                            new_data = f.read()
                            last_size = f.tell()
                        if new_data:
                            yield f"data: {json.dumps({'type': 'log', 'text': new_data})}\n\n"
            except Exception:
                pass

            with _lock:
                s = state[mid]
                yield f"data: {json.dumps({'type': 'status', 'state': {k: v for k, v in s.items() if k != 'pid'}})}\n\n"

            time.sleep(0.5)

    return Response(generate(), mimetype="text/event-stream")


if __name__ == "__main__":
    print("=" * 50)
    print("  Training Dashboard starting at http://localhost:5050")
    print("  Press Ctrl+C to stop")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5050, debug=False, threaded=True)


HTML = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Training Dashboard</title>
<style>
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :root {
    --bg: #0f1117; --surface: #1a1d27; --border: #2a2d3a;
    --text: #e1e4eb; --text-dim: #8b8fa3; --accent: #3b82f6;
    --green: #10b981; --amber: #f59e0b; --red: #ef4444;
    --radius: 12px;
  }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
    background: var(--bg); color: var(--text); min-height: 100vh;
    padding: 24px 32px;
  }
  .header {
    display: flex; align-items: center; justify-content: space-between;
    margin-bottom: 28px; flex-wrap: wrap; gap: 16px;
  }
  .header h1 {
    font-size: 24px; font-weight: 700;
    background: linear-gradient(135deg, var(--accent), #8b5cf6);
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  }
  .header-actions { display: flex; gap: 12px; align-items: center; }
  .overall-progress {
    display: flex; align-items: center; gap: 12px;
    flex: 1; min-width: 200px;
  }
  .overall-progress .bar-bg {
    flex: 1; height: 6px; background: var(--border); border-radius: 3px; overflow: hidden;
  }
  .overall-progress .bar-fill {
    height: 100%; background: linear-gradient(90deg, var(--accent), #8b5cf6);
    border-radius: 3px; transition: width 0.5s ease;
  }
  .overall-progress .label { font-size: 13px; color: var(--text-dim); white-space: nowrap; }

  .cards {
    display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 16px; margin-bottom: 24px;
  }
  .card {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: var(--radius); padding: 20px; position: relative;
    transition: border-color 0.3s, box-shadow 0.3s;
  }
  .card:hover { border-color: #3b82f640; }
  .card.running { border-color: var(--accent); box-shadow: 0 0 20px #3b82f620; }
  .card.completed { border-color: var(--green); }
  .card.failed { border-color: var(--red); }

  .card-header {
    display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px;
  }
  .card-title {
    font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 8px;
  }
  .card-title .dot {
    width: 10px; height: 10px; border-radius: 50%; display: inline-block;
  }
  .badge {
    font-size: 11px; padding: 2px 10px; border-radius: 20px;
    font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
  }
  .badge.idle { background: #2a2d3a; color: var(--text-dim); }
  .badge.running { background: #3b82f630; color: var(--accent); }
  .badge.completed { background: #10b98120; color: var(--green); }
  .badge.failed { background: #ef444420; color: var(--red); }

  .card-stats {
    display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px;
  }
  .stat { }
  .stat-label { font-size: 11px; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.3px; }
  .stat-value { font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
  .stat-value .unit { font-size: 12px; font-weight: 400; color: var(--text-dim); }
  .stat-value.acc { color: var(--green); }
  .stat-value.loss { color: var(--amber); }
  .stat-value.val-acc { color: var(--accent); }

  .progress-bar {
    height: 4px; background: var(--border); border-radius: 2px; overflow: hidden; margin-bottom: 12px;
  }
  .progress-bar .fill {
    height: 100%; border-radius: 2px; transition: width 0.5s ease;
  }

  .card-btn {
    width: 100%; padding: 8px 0; border: 1px solid var(--border); border-radius: 8px;
    background: transparent; color: var(--text); font-size: 13px; font-weight: 500;
    cursor: pointer; transition: all 0.2s;
  }
  .card-btn:hover:not(:disabled) { background: #3b82f620; border-color: var(--accent); }
  .card-btn:disabled { opacity: 0.4; cursor: not-allowed; }
  .card-btn.start-all {
    background: linear-gradient(135deg, var(--accent), #8b5cf6); border: none;
    padding: 10px 24px; border-radius: 10px; font-size: 14px; font-weight: 600;
  }
  .card-btn.start-all:hover:not(:disabled) { opacity: 0.9; }

  .log-section {
    background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
    overflow: hidden;
  }
  .log-header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 12px 20px; border-bottom: 1px solid var(--border);
  }
  .log-header h3 { font-size: 14px; font-weight: 600; }
  .log-tabs { display: flex; gap: 4px; }
  .log-tabs button {
    padding: 4px 14px; border: 1px solid var(--border); border-radius: 6px;
    background: transparent; color: var(--text-dim); font-size: 12px; cursor: pointer;
  }
  .log-tabs button.active { background: #3b82f620; color: var(--accent); border-color: var(--accent); }

  .log-body {
    height: 360px; overflow-y: auto; padding: 16px 20px;
    font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 12px; line-height: 1.6; color: var(--text-dim);
    background: #0a0c12;
  }
  .log-body::-webkit-scrollbar { width: 6px; }
  .log-body::-webkit-scrollbar-track { background: transparent; }
  .log-body::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
  .log-body .line {
    white-space: pre-wrap; word-break: break-all;
  }
  .log-body .line.info { color: var(--text-dim); }
  .log-body .line.epoch { color: var(--accent); font-weight: 500; }
  .log-body .line.metric { color: var(--green); }
  .log-body .line.error { color: var(--red); }
  .log-body .line.acc { color: var(--green); }
  .log-body .line.loss { color: var(--amber); }
  .log-body .line.delim { color: #3b82f640; text-align: center; }

  .server-status {
    position: fixed; bottom: 16px; right: 24px;
    display: flex; align-items: center; gap: 6px;
    font-size: 12px; color: var(--text-dim);
  }
  .server-status .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--green); animation: pulse 2s infinite; }
  @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

  @media (max-width: 600px) {
    body { padding: 16px; }
    .cards { grid-template-columns: 1fr; }
  }
</style>
</head>
<body>

<div class="header">
  <h1>Model Training Dashboard</h1>
  <div class="overall-progress">
    <span class="label" id="overall-label">0 / 4 complete</span>
    <div class="bar-bg"><div class="bar-fill" id="overall-bar" style="width:0%"></div></div>
  </div>
  <div class="header-actions">
    <button class="card-btn start-all" id="btn-start-all" onclick="startAll()">▶ Start All</button>
  </div>
</div>

<div class="cards" id="cards"></div>

<div class="log-section">
  <div class="log-header">
    <h3>Log Output</h3>
    <div class="log-tabs" id="log-tabs"></div>
  </div>
  <div class="log-body" id="log-body">
    <div class="line info">Waiting for training output…</div>
  </div>
</div>

<div class="server-status">
  <span class="dot"></span> Server connected
</div>

<script>
const MODELS = ['url', 'english', 'sms', 'chinese'];
let currentLog = 'url';
let autoScroll = true;
let logStates = {};

for (const m of MODELS) logStates[m] = { lines: [], unread: 0 };

// ── Render cards ──────────────────────────────────────

function renderCards(status, config) {
  const container = document.getElementById('cards');
  container.innerHTML = MODELS.map(mid => {
    const s = status.models[mid];
    const c = config[mid];
    const pct = Math.round((s.progress || 0) * 100);
    const barColor = s.status === 'completed' ? '#10b981' : s.status === 'failed' ? '#ef4444' : c.color;
    return `
      <div class="card ${s.status}" id="card-${mid}" data-model="${mid}">
        <div class="card-header">
          <div class="card-title">
            <span class="dot" style="background:${c.color}"></span>
            ${c.name}
          </div>
          <span class="badge ${s.status}">${s.status}</span>
        </div>
        <div class="card-stats">
          <div class="stat">
            <div class="stat-label">Epoch</div>
            <div class="stat-value">${s.epoch}<span class="unit"> / ${s.total_epochs}</span></div>
          </div>
          <div class="stat">
            <div class="stat-label">Progress</div>
            <div class="stat-value">${pct}<span class="unit">%</span></div>
          </div>
          <div class="stat">
            <div class="stat-label">Accuracy</div>
            <div class="stat-value acc">${(s.acc * 100).toFixed(1)}<span class="unit">%</span></div>
          </div>
          <div class="stat">
            <div class="stat-label">Loss</div>
            <div class="stat-value loss">${s.loss.toFixed(4)}</div>
          </div>
        </div>
        <div class="progress-bar">
          <div class="fill" style="width:${pct}%;background:${barColor}"></div>
        </div>
        <button class="card-btn" id="btn-${mid}" onclick="startModel('${mid}')"
          ${s.status === 'running' || s.status === 'completed' ? 'disabled' : ''}>
          ${s.status === 'running' ? 'Running…' : s.status === 'completed' ? 'Completed ✓' : s.status === 'failed' ? 'Retry' : '▶ Start'}
        </button>
      </div>
    `;
  }).join('');
}

function updateOverall(status) {
  const models = status.models;
  const total = Object.keys(models).length;
  const done = Object.values(models).filter(s => s.status === 'completed').length;
  const running = Object.values(models).filter(s => s.status === 'running').length;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;
  document.getElementById('overall-label').textContent = `${done} / ${total} complete${running ? ` (${running} running)` : ''}`;
  document.getElementById('overall-bar').style.width = pct + '%';
}

// ── Log tabs ──────────────────────────────────────────

function renderLogTabs() {
  const container = document.getElementById('log-tabs');
  container.innerHTML = MODELS.map(mid =>
    `<button class="${mid === currentLog ? 'active' : ''}" onclick="switchLog('${mid}')">
      ${mid.toUpperCase()}${logStates[mid].unread > 0 ? ` (${logStates[mid].unread})` : ''}
    </button>`
  ).join('');
}

function switchLog(mid) {
  currentLog = mid;
  logStates[mid].unread = 0;
  renderLogTabs();
  loadLog(mid);
}

async function loadLog(mid) {
  try {
    const r = await fetch(`/api/logs/${mid}`);
    const data = await r.json();
    logStates[mid].lines = data.lines;
    renderLogContent(mid, data.lines);
  } catch (e) {
    console.error('loadLog error', e);
  }
}

function renderLogContent(mid, lines) {
  const body = document.getElementById('log-body');
  body.innerHTML = lines.length === 0
    ? '<div class="line info">No log data yet…</div>'
    : lines.map(l => {
        let cls = 'line info';
        if (/Epoch \d+\/\d+:/.test(l) && /\d+%/.test(l)) cls = 'line epoch';
        else if (/Acc(uracy)?:/.test(l) || /acc=/.test(l)) cls = 'line acc';
        else if (/Loss:/.test(l) || /loss=/.test(l)) cls = 'line loss';
        else if (/Train Loss:/.test(l)) cls = 'line metric';
        else if (/error|Error|ERROR|fail|Fail|FAIL/.test(l)) cls = 'line error';
        else if (/^={2,}/.test(l)) cls = 'line delim';
        return `<div class="${cls}">${escHtml(l)}</div>`;
      }).join('');
  if (autoScroll) body.scrollTop = body.scrollHeight;
}

function escHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

// ── SSE stream ────────────────────────────────────────

function connectSSE(mid) {
  const evtSource = new EventSource(`/api/stream/${mid}`);
  evtSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data.type === 'log' && data.text) {
        logStates[mid].lines.push(...data.text.split('\n').filter(l => l));
        if (mid !== currentLog) logStates[mid].unread += data.text.split('\n').filter(l => l).length;
      }
      if (data.type === 'status' && data.state) {
        // Update global state (re-fetched from /api/status periodically)
      }
    } catch (e) { /* ignore parse errors */ }
  };
  return evtSource;
}

// ── Polling / refresh ─────────────────────────────────

async function refreshStatus() {
  try {
    const r = await fetch('/api/status');
    const data = await r.json();
    renderCards(data, data.config);
    updateOverall(data);
    renderLogTabs();
  } catch (e) {
    console.error('refresh error', e);
  }
}

// ── Actions ────────────────────────────────────────────

async function startModel(mid) {
  try {
    const r = await fetch(`/api/start/${mid}`);
    const data = await r.json();
    if (data.error) { alert(data.error); return; }
    refreshStatus();
  } catch (e) {
    alert('Failed to start: ' + e.message);
  }
}

async function startAll() {
  try {
    const r = await fetch('/api/start-all');
    const data = await r.json();
    if (data.error) { alert(data.error); return; }
    refreshStatus();
  } catch (e) {
    alert('Failed to start: ' + e.message);
  }
}

// ── Init ──────────────────────────────────────────────

refreshStatus();
loadLog(currentLog);

// Connect SSE for all models
const streams = MODELS.map(connectSSE);

// Periodic full refresh
setInterval(refreshStatus, 2000);

// Log auto-scroll
document.getElementById('log-body').addEventListener('scroll', () => {
  const el = document.getElementById('log-body');
  autoScroll = (el.scrollTop + el.clientHeight >= el.scrollHeight - 30);
});

// Also refresh log content periodically
setInterval(() => {
  if (currentLog) loadLog(currentLog);
}, 3000);
</script>
</body>
</html>
"""
