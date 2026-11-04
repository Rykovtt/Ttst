"""Оценочный набор (только тестовые голоса и музыка): длинная музыка, «песни» из чужой речи, вставки имени."""
import sys, os, random, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data
D, M, OUT = sys.argv[1:4]
S, mus = data.load(D, M); r = random.Random(5)
music = np.concatenate(mus["test"])                       # ~50 мин
# «вокал»: тестовые негативы поверх тестовой музыки, 10 мин
voc = music[:16000 * 600].copy(); pos = 0
while pos < len(voc):
    u = r.choice(S["test"]["neg"]); g = 10 ** (r.uniform(-2, 8) / 20) * data.rms(voc[pos:pos + 16000]) / data.rms(u)
    b = min(len(voc), pos + len(u)); voc[pos:b] += u[:b - pos] * g; pos = b + r.randint(0, 4000)
# вставки имени: 3 фона × 3 уровня
ins, ends, conds = [], [], []
for i, u in enumerate(S["test"]["pos"][:240]):
    kind = i % 3; snr = [-3, 3, 10][(i // 3) % 3]
    if kind == 0: bg = music[r.randint(0, len(music) - 64000):][:64000].copy()
    elif kind == 1: bg = voc[r.randint(0, len(voc) - 64000):][:64000].copy()
    else: bg = (np.random.randn(64000) * 0.0005).astype(np.float32); snr = 20
    st = r.randint(16000, 32000); g = 10 ** (snr / 20) * data.rms(bg) / data.rms(u)
    g = min(g, 0.8 / np.abs(u).max()); bg[st:st + len(u)] += u * g
    ins.append(np.clip(bg, -1, 1)); ends.append((st + len(u)) / 16000); conds.append((kind, snr))
np.savez_compressed(OUT, music=music.astype(np.float32), voc=np.clip(voc, -1, 1).astype(np.float32), ins=np.stack(ins), ends=np.array(ends), conds=np.array(conds))
print(len(music) / 16000 / 60, "min music", len(voc) / 16000 / 60, "min vocal", len(ins), "inserts")
