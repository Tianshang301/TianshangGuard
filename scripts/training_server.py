#!/usr/bin/env python3
"""Training Dashboard Backend — Flask + SSE"""

import json, os, re, subprocess, threading, time
from pathlib import Path
from flask import Flask, Response, jsonify, send_file

app = Flask(__name__)

BASE = Path(__file__).parent
MODELS = {
    "url":     {"name": "URL 检测", "log": "training_url.log",     "mode": "url",     "fresh": False, "epochs": 20, "color": "#3b82f6"},
    "english": {"name": "英文 SMS", "log": "training_english.log", "mode": "english", "fresh": False, "epochs": 10, "color": "#10b981"},
    "sms":     {"name": "中文 SMS", "log": "training_sms.log",     "mode": "sms",     "fresh": False, "epochs": 20, "color": "#f59e0b"},
    "chinese": {"name": "中文文本", "log": "training_chinese.log", "mode": "chinese", "fresh": False, "epochs": 20, "color": "#ef4444"},
}

def init_state():
    s = {}
    for mid, cfg in MODELS.items():
        s[mid] = {"status": "idle", "epoch": 0, "total_epochs": cfg["epochs"],
                  "acc": 0.0, "loss": 0.0, "val_acc": 0.0, "val_loss": 0.0,
                  "progress": 0.0, "batch": 0, "total_batches": 0,
                  "error": None, "pid": None}
    return s

state = init_state()
_lock = threading.Lock()

def _parse(line):
    s = {}
    m = re.search(r"Epoch (\d+)/(\d+)", line)
    if m: s["epoch"] = int(m.group(1)); s["total_epochs"] = int(m.group(2))

    m = re.search(r"(\d+)%\|", line)
    if m: s["progress"] = float(m.group(1)) / 100.0

    m = re.search(r"acc=([\d.]+)", line)
    if m: s["acc"] = float(m.group(1))

    m = re.search(r"loss=([\d.]+)", line)
    if m: s["loss"] = float(m.group(1))

    m = re.search(r"Train Acc: ([\d.]+)", line)
    if m: s["acc"] = float(m.group(1))

    m = re.search(r"Train Loss: ([\d.]+)", line)
    if m: s["loss"] = float(m.group(1))

    m = re.search(r"Val Acc: ([\d.]+)", line)
    if m: s["val_acc"] = float(m.group(1))

    m = re.search(r"Val Loss: ([\d.]+)", line)
    if m: s["val_loss"] = float(m.group(1))

    m = re.search(r"(\d+)/(\d+)", line)
    if m and "batch" not in s:
        b, tb = int(m.group(1)), int(m.group(2))
        if tb > 100: s["batch"] = b; s["total_batches"] = tb

    if "Accuracy:" in line:
        m = re.search(r"Accuracy:\s*(\d+)/(\d+)\s*=\s*([\d.]+%)", line)
        if m: s["acc"] = float(m.group(3).replace("%", "")) / 100.0

    return s

def _run(mid):
    cfg = MODELS[mid]
    log_path = BASE / cfg["log"]
    if cfg["fresh"]:
        log_path.write_text("")
    cmd = ["python", str(BASE / "train_phishing_model.py"), "--mode", cfg["mode"]]
    if cfg["fresh"]: cmd.append("--fresh")
    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"
    with _lock: state[mid].update(status="running", error=None, progress=0, epoch=0, acc=0, loss=0)
    proc = subprocess.Popen(cmd, cwd=str(BASE), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, bufsize=0)
    with _lock: state[mid]["pid"] = proc.pid
    log_f = open(log_path, "ab")  # binary: preserve \r from tqdm
    leftover = b""
    while True:
        chunk = proc.stdout.read(4096)
        if not chunk:
            break
        log_f.write(chunk); log_f.flush()
        # Parse each line (handle \r from tqdm)
        data = (leftover + chunk).decode("utf-8", errors="replace")
        lines = data.replace("\r", "\n").split("\n")
        leftover = b"" if data.endswith("\n") else lines[-1].encode()
        for line in lines:
            line = line.strip()
            if line:
                parsed = _parse(line)
                if parsed:
                    with _lock:
                        for k, v in parsed.items(): state[mid][k] = v
    log_f.close()
    proc.wait()
    with _lock:
        state[mid]["status"] = "completed" if proc.returncode == 0 else "failed"
        state[mid]["error"] = None if proc.returncode == 0 else f"exit={proc.returncode}"
        state[mid]["progress"] = 1.0 if proc.returncode == 0 else state[mid]["progress"]

def _run_all():
    for mid in ["url", "english", "sms", "chinese"]:
        with _lock:
            if state[mid]["status"] == "completed": continue
        _run(mid)

@app.route("/")
def index(): return send_file(BASE / "dashboard.html")

@app.route("/api/status")
def api_status():
    with _lock:
        return jsonify({"models": {mid: {k: v for k, v in s.items() if k != "pid"} for mid, s in state.items()},
                        "config": {mid: {"name": c["name"], "color": c["color"]} for mid, c in MODELS.items()}})

@app.route("/api/start/<mid>")
def api_start(mid):
    if mid not in MODELS: return jsonify({"error": "unknown"}), 400
    with _lock:
        if state[mid]["status"] == "running": return jsonify({"error": "running"}), 409
        state[mid]["status"] = "starting"
    threading.Thread(target=_run, args=(mid,), daemon=True).start()
    return jsonify({"ok": True, "model": mid})

@app.route("/api/start-all")
def api_start_all():
    for mid in MODELS:
        with _lock:
            if state[mid]["status"] == "running" or state[mid]["status"] == "starting":
                return jsonify({"error": f"{mid} already running"}), 409
            if state[mid]["status"] != "completed":
                state[mid]["status"] = "queued"
    threading.Thread(target=_run_all, daemon=True).start()
    return jsonify({"ok": True, "models": list(MODELS.keys())})

@app.route("/api/logs/<mid>")
def api_logs(mid):
    if mid not in MODELS: return jsonify({"error": "unknown"}), 400
    p = BASE / MODELS[mid]["log"]
    if not p.exists(): return jsonify({"lines": []})
    lines = p.read_text("utf-8", errors="replace").splitlines()
    return jsonify({"lines": lines[-300:]})

@app.route("/api/stream/<mid>")
def api_stream(mid):
    if mid not in MODELS: return "", 404
    def gen():
        pos = 0
        while True:
            p = BASE / MODELS[mid]["log"]
            try:
                if p.exists() and p.stat().st_size > pos:
                    with open(p, "r", encoding="utf-8", errors="replace") as f:
                        f.seek(pos); new = f.read(); pos = f.tell()
                    if new: yield f"data: {json.dumps({'type':'log','text':new})}\n\n"
            except: pass
            with _lock:
                yield f"data: {json.dumps({'type':'status','state':{k:v for k,v in state[mid].items() if k!='pid'}})}\n\n"
            time.sleep(0.5)
    return Response(gen(), mimetype="text/event-stream")

if __name__ == "__main__":
    print("=" * 50)
    print("  Dashboard: http://localhost:5050")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5050, debug=False, threaded=True)
