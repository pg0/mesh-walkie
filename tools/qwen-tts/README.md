# Qwen-Audio-3.0-TTS auf Thor (WSL)

Setup für die Text-to-Speech-Modelle **`qwen-audio-3.0-tts-flash`** (Echtzeit,
~300 ms First-Packet) und **`qwen-audio-3.0-tts-plus`** (höchste Qualität,
Platz 1 im Artificial-Analysis-Speech-Arena-Leaderboard).

> **Wichtig:** Beide Modelle sind **gehostete API-Modelle** von Alibaba Cloud
> Model Studio (DashScope). Es gibt **keine herunterladbaren Gewichte** — sie
> laufen nicht lokal auf der GPU, sondern werden per API aufgerufen. Lokal
> installiert wird nur das SDK + CLI.

## Installation (einmalig, auf Thor im WSL)

```bash
# Repo aktualisieren, dann:
bash tools/qwen-tts/install-thor-wsl.sh
```

Das Skript ist idempotent und erledigt:

1. `python3` / `python3-venv` installieren, falls nötig (via apt)
2. venv unter `~/.venvs/qwen-tts` anlegen
3. `dashscope`-SDK installieren/aktualisieren
4. CLI-Wrapper `~/.local/bin/qwen-tts` anlegen

## API-Key (einmalig)

1. Bei [Alibaba Cloud Model Studio](https://modelstudio.console.alibabacloud.com)
   anmelden (internationale Region **Singapur** — nicht die China-Konsole)
2. Model Studio aktivieren und unter **API Keys** einen Key erzeugen
3. In `~/.bashrc` eintragen:

   ```bash
   export DASHSCOPE_API_KEY=sk-...
   ```

## Benutzung

```bash
qwen-tts "Hallo von Thor"                              # flash → out.mp3
qwen-tts "Hallo von Thor" -m plus -o hallo.mp3         # beste Qualität
qwen-tts "Guten Morgen" -m flash -v Serena -o gm.mp3   # andere Stimme
qwen-tts --help
```

| Option | Bedeutung | Default |
|---|---|---|
| `-m flash\|plus` | Modellvariante | `flash` |
| `-v VOICE` | Stimme (z. B. `Cherry`, `Serena`, `Ethan`) | `Cherry` |
| `-o DATEI` | Ausgabedatei | `out.mp3` |
| `--region intl\|cn` | API-Region (Singapur / Peking) | `intl` |

## Modell-Überblick

| | flash | plus |
|---|---|---|
| Einsatz | Echtzeit-Interaktion | Höchste Qualität |
| Latenz | ~300 ms First Packet | höher |
| Sprachen | 16 (inkl. Deutsch) + chinesische Dialekte | 16 + Dialekte |
| Extras | Stil-Steuerung in natürlicher Sprache, Tags für Nonverbales (Lachen etc.) | dito |
| Formate | PCM / WAV / MP3 / Opus, bis 48 kHz | dito |

Die vollständige Stimmenliste und Feinheiten (Style-Prompts, Streaming) stehen
in der [Model-Studio-Doku](https://www.alibabacloud.com/help/en/model-studio/realtime-tts-user-guide).

## Troubleshooting

- **`InvalidApiKey` / 401** — Key stammt aus der falschen Region. Für
  `--region intl` muss der Key aus der Singapur-Konsole kommen.
- **`Model not exist`** — Model Studio ist im Account noch nicht aktiviert
  oder die Region passt nicht zum Key.
- **Stimme wird abgelehnt** — nicht jede Stimme ist für jedes Modell
  freigeschaltet; Stimmenliste in der Doku prüfen und mit `-v` setzen.
