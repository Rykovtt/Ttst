import sys, os, json, random, numpy as np, wave
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig
OUT, VOICE = sys.argv[1], sys.argv[2]; random.seed(11)
voice = PiperVoice.load(VOICE); sr = voice.config.sample_rate; NS = voice.config.num_speakers
POS = ["Sahnta", "Sunta!", "Sahnta.", "Hey, Sahnta", "Sahnta?", "Santa", "Sunta,"]
CONF = ("some ta|son ta|sun tan|Sandra|center|Sunday|sauna|panda|Fanta|Shanta|Sontag|sonata|Satan|salt|sand|stand|"
        "sunny|summer|Santiago|sentence|son|sun|tan|tuna|canta|manta|Montana|Atlanta|fantasy|hunter").split("|")
LYR = ("i love you baby|tonight we dance|hold me closer|never let me go|we are the champions|shake it off|"
       "all night long|take my hand|dont stop me now|sweet dreams are made of this|under the moonlight|"
       "you are my sunshine|let it be|i want to break free|feel the rhythm|on the radio|in the summer time|"
       "come on come on|yeah yeah yeah|oh oh oh|stop|next|pause|play|louder|go back|turn it up|one more time").split("|")
WORDS = "the a you i we love night day time heart baby dance sun summer sand stand hand fire light come go now never forever".split()
def say(t, spk, ls, ns, nw):
    a = np.concatenate([c.audio_int16_array for c in voice.synthesize(t, SynthesisConfig(speaker_id=spk, length_scale=ls, noise_scale=ns, noise_w_scale=nw))]).astype(np.float32)
    return resample_poly(a, 16000, sr)
def trim(x, thr=300):
    i = np.where(np.abs(x) > thr)[0]; return x if len(i) == 0 else x[max(0, i[0] - 800): i[-1] + 800]
def save(p, x):
    with wave.open(p, "wb") as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(np.clip(x, -32768, 32767).astype(np.int16).tobytes())
meta = json.load(open(f"{OUT}/meta.json"))
for spk in range(NS):
    tag = f"en{spk:03d}" + ("t" if spk >= 860 else "")
    for i in range(3):
        x = trim(say(random.choice(POS), spk, random.uniform(0.75, 1.35), random.uniform(0.3, 1.0), random.uniform(0.3, 1.1)))
        p = f"{OUT}/pen_{tag}_{i}.wav"; save(p, x); meta.append({"f": p, "y": 1, "spk": tag})
    for i in range(4):
        t = random.choice(CONF) if i == 0 else random.choice(LYR) if i < 3 else " ".join(random.choice(WORDS) for _ in range(random.randint(2, 6)))
        x = trim(say(t, spk, random.uniform(0.8, 1.3), random.uniform(0.3, 1.0), random.uniform(0.3, 1.1)))
        p = f"{OUT}/nen_{tag}_{i}.wav"; save(p, x); meta.append({"f": p, "y": 0, "spk": tag})
    if spk % 100 == 0: print(spk, flush=True)
json.dump(meta, open(f"{OUT}/meta.json", "w"), ensure_ascii=False)
print("total", len(meta), "pos", sum(m["y"] for m in meta))
