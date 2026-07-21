#!/usr/bin/env bash
# Installiert die Qwen-Audio-3.0-TTS-Anbindung (flash + plus) auf Thor (WSL/Ubuntu).
#
# Die Modelle qwen-audio-3.0-tts-flash und qwen-audio-3.0-tts-plus sind
# gehostete API-Modelle (Alibaba Cloud Model Studio / DashScope) — es gibt
# keine herunterladbaren Gewichte. "Installieren" heisst daher: Python-venv
# anlegen, DashScope-SDK installieren und einen CLI-Wrapper bereitstellen.
#
# Aufruf:   bash install-thor-wsl.sh
# Danach:   export DASHSCOPE_API_KEY=sk-...   (siehe README.md)
#           qwen-tts "Hallo Welt" -m flash -o hallo.mp3
set -euo pipefail

VENV_DIR="${QWEN_TTS_VENV:-$HOME/.venvs/qwen-tts}"
BIN_DIR="$HOME/.local/bin"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Qwen-Audio-3.0-TTS Setup (Flash + Plus) für WSL"

if ! grep -qiE 'microsoft|wsl' /proc/version 2>/dev/null; then
    echo "    Hinweis: Das sieht nicht nach WSL aus — Installation läuft trotzdem weiter."
fi

if ! command -v python3 >/dev/null; then
    echo "==> python3 fehlt, installiere python3 + venv (sudo nötig)..."
    sudo apt-get update && sudo apt-get install -y python3 python3-venv
elif ! python3 -m venv --help >/dev/null 2>&1; then
    echo "==> python3-venv fehlt, installiere (sudo nötig)..."
    sudo apt-get update && sudo apt-get install -y python3-venv
fi

if [ ! -d "$VENV_DIR" ]; then
    echo "==> Erzeuge venv unter $VENV_DIR"
    python3 -m venv "$VENV_DIR"
fi

echo "==> Installiere/aktualisiere dashscope-SDK"
"$VENV_DIR/bin/pip" install --quiet --upgrade pip dashscope

echo "==> Verifiziere SDK-Import"
"$VENV_DIR/bin/python" - <<'PY'
import dashscope
print(f"    dashscope {dashscope.__version__} OK")
PY

echo "==> Lege CLI-Wrapper an: $BIN_DIR/qwen-tts"
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/qwen-tts" <<EOF
#!/usr/bin/env bash
exec "$VENV_DIR/bin/python" "$SCRIPT_DIR/qwen_tts.py" "\$@"
EOF
chmod +x "$BIN_DIR/qwen-tts"

case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *) echo "    Hinweis: $BIN_DIR ist nicht im PATH. Füge in ~/.bashrc hinzu:"
       echo "        export PATH=\"\$HOME/.local/bin:\$PATH\"" ;;
esac

echo
if [ -n "${DASHSCOPE_API_KEY:-}" ]; then
    echo "==> DASHSCOPE_API_KEY ist gesetzt — Smoke-Test:"
    "$BIN_DIR/qwen-tts" "Installation erfolgreich." -m flash -o /tmp/qwen-tts-test.mp3 \
        && echo "    Test-Audio: /tmp/qwen-tts-test.mp3"
else
    echo "==> Fertig. Es fehlt nur noch der API-Key (kostenlos anlegbar):"
    echo "    1. https://modelstudio.console.alibabacloud.com  (Region Singapur)"
    echo "    2. API-Key erzeugen, dann in ~/.bashrc:"
    echo "         export DASHSCOPE_API_KEY=sk-..."
    echo "    3. Testen:  qwen-tts \"Hallo von Thor\" -m plus -o test.mp3"
fi
