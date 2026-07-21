#!/usr/bin/env python3
"""CLI für Qwen-Audio-3.0-TTS (flash/plus) über die DashScope-API.

Beispiele:
    qwen-tts "Hallo Welt"                          # flash, Stimme Cherry, out.mp3
    qwen-tts "Hallo Welt" -m plus -v Serena -o hallo.mp3
    qwen-tts "你好" -m flash --region cn
"""
import argparse
import os
import sys
import urllib.request

MODELS = {
    "flash": "qwen-audio-3.0-tts-flash",
    "plus": "qwen-audio-3.0-tts-plus",
}

INTL_HTTP = "https://dashscope-intl.aliyuncs.com/api/v1"
INTL_WS = "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference/"


def synthesize(model: str, text: str, voice: str) -> bytes:
    """Versucht zuerst das Streaming-Interface (tts_v2), dann qwen_tts."""
    errors = []
    try:
        from dashscope.audio.tts_v2 import SpeechSynthesizer

        synth = SpeechSynthesizer(model=model, voice=voice)
        audio = synth.call(text)
        if audio:
            return audio
        errors.append(f"tts_v2: leere Antwort ({synth.get_last_request_id()})")
    except Exception as exc:  # noqa: BLE001 - Fallback auf zweites Interface
        errors.append(f"tts_v2: {exc}")

    try:
        from dashscope.audio.qwen_tts import SpeechSynthesizer as QwenTTS

        rsp = QwenTTS.call(model=model, text=text, voice=voice)
        if rsp.status_code == 200:
            url = rsp.output.audio["url"]
            with urllib.request.urlopen(url) as f:
                return f.read()
        errors.append(f"qwen_tts: HTTP {rsp.status_code} {rsp.code}: {rsp.message}")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"qwen_tts: {exc}")

    sys.exit("Synthese fehlgeschlagen:\n  " + "\n  ".join(errors))


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("text", help="zu sprechender Text")
    p.add_argument("-m", "--model", choices=sorted(MODELS), default="flash",
                   help="flash = Echtzeit (~300ms), plus = höchste Qualität (Default: flash)")
    p.add_argument("-v", "--voice", default="Cherry",
                   help="Stimme (Default: Cherry; Liste siehe Model-Studio-Doku)")
    p.add_argument("-o", "--output", default="out.mp3", help="Ausgabedatei (Default: out.mp3)")
    p.add_argument("--region", choices=["intl", "cn"], default="intl",
                   help="intl = Singapur (Default), cn = Peking")
    args = p.parse_args()

    if not os.environ.get("DASHSCOPE_API_KEY"):
        sys.exit("DASHSCOPE_API_KEY ist nicht gesetzt.\n"
                 "Key anlegen unter https://modelstudio.console.alibabacloud.com, dann:\n"
                 "  export DASHSCOPE_API_KEY=sk-...")

    import dashscope

    if args.region == "intl":
        dashscope.base_http_api_url = INTL_HTTP
        dashscope.base_websocket_api_url = INTL_WS

    audio = synthesize(MODELS[args.model], args.text, args.voice)
    with open(args.output, "wb") as f:
        f.write(audio)
    print(f"{args.output} geschrieben ({len(audio)} Bytes, {MODELS[args.model]}, Stimme {args.voice})")


if __name__ == "__main__":
    main()
