import sys, os, json, random, numpy as np
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "gen.py")).read().split("meta = []")[0]
exec(src)
random.seed(7)
meta = json.load(open(f"{OUT}/meta.json"))
for f in sorted(os.listdir(VOICES)):
    if not f.endswith(".onnx"): continue
    voice = PiperVoice.load(os.path.join(VOICES, f)); sr = voice.config.sample_rate; nspk = max(1, voice.config.num_speakers)
    for spk in range(nspk):
        tag = f"{f[:-5]}-{spk}"
        for i, text in enumerate(POS * 18):
            x = trim(piper_say(voice, sr, text, spk if nspk > 1 else None, random.uniform(0.65, 1.5), random.uniform(0.2, 1.1), random.uniform(0.2, 1.2)))
            p = f"{OUT}/pos2_{tag}_{i:03d}.wav"; save(p, x); meta.append({"f": p, "y": 1, "spk": tag})
        print(tag, flush=True)
json.dump(meta, open(f"{OUT}/meta.json", "w"), ensure_ascii=False)
print("total", len(meta), "pos", sum(m["y"] for m in meta))
