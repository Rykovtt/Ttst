import os, json, wave, random
import numpy as np
W = 16000
TEST_SPK = {"ru_RU-ruslan-medium-0", "uk_UA-ukrainian_tts-medium-2", "espeak-ru_f3", "espeak-ru_m5"}

def rd(p):
    w = wave.open(p); return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768

def load(data_dir, music_dir):
    meta = json.load(open(os.path.join(data_dir, "meta.json")))
    S = {"train": {"pos": [], "neg": []}, "test": {"pos": [], "neg": []}}
    for m in meta:
        part = "test" if (m["spk"] in TEST_SPK or m["spk"].endswith("t")) else "train"
        S[part]["pos" if m["y"] else "neg"].append(rd(m["f"]))
    mus = {"train": [], "test": []}
    for i, f in enumerate(sorted(os.listdir(music_dir))):
        x = np.fromfile(os.path.join(music_dir, f), np.int16).astype(np.float32) / 32768
        if len(x) < W * 3: continue
        mus["test" if i % 10 == 0 else "train"].append(x)
    return S, mus

def warp(u, r):
    """Сдвиг высоты/темпа (передискретизация) — больше разных голосов."""
    k = r.uniform(0.86, 1.16)
    if abs(k - 1) < 0.02: return u
    n = int(len(u) / k); return np.interp(np.arange(n) * k, np.arange(len(u)), u).astype(np.float32)

def rms(x): return float(np.sqrt(np.mean(x ** 2)) + 1e-8)

class Mixer:
    def __init__(s, S, mus, part, rng=None):
        s.pos, s.neg, s.mus = S[part]["pos"], S[part]["neg"], mus[part]; s.r = rng or random.Random(0)
    def bg(s):
        r = s.r; t = r.random()
        if t < 0.55:
            m = r.choice(s.mus); o = r.randint(0, len(m) - W); x = m[o:o + W].copy()
            x *= 10 ** (r.uniform(-30, -8) / 20) / rms(x)
        elif t < 0.75:                       # чужая речь
            x = np.zeros(W, np.float32); pos = r.randint(-W // 2, W // 2)
            while pos < W:
                u = r.choice(s.neg); a, b = max(0, pos), min(W, pos + len(u))
                if b > a: x[a:b] += u[a - pos:b - pos]
                pos += len(u) + r.randint(0, 3000)
            x *= 10 ** (r.uniform(-32, -12) / 20) / rms(x)
        elif t < 0.88:
            x = np.random.randn(W).astype(np.float32); x = np.cumsum(x) if r.random() < 0.5 else x
            x -= x.mean(); x *= 10 ** (r.uniform(-50, -25) / 20) / rms(x)
        else:
            x = (np.random.randn(W) * 10 ** (r.uniform(-70, -50) / 20)).astype(np.float32)
        if r.random() < 0.3:                 # музыка + речь вместе
            m = r.choice(s.mus); o = r.randint(0, len(m) - W); y = m[o:o + W]
            x = x + y * (10 ** (r.uniform(-34, -14) / 20) / rms(y))
        return x
    def place(s, x, u, end, gain):
        st = end - len(u); a, b = max(0, st), min(W, end)
        if b > a: x[a:b] += u[a - st:b - st] * gain
    hard = None
    def sample(s):
        r = s.r
        if s.hard is not None and r.random() < 0.3:          # окно, где прошлая модель ошиблась
            w = s.hard[r.randint(0, len(s.hard) - 1)].astype(np.float32) / 32768
            return np.clip(w * 10 ** (r.uniform(-6, 6) / 20), -1, 1), 0
        x = s.bg(); bgr = rms(x); t = r.random(); y = 0
        snr = r.uniform(-4, 22)
        if t < 0.45:                         # имя: конец в последних 0.6 с окна
            u = warp(r.choice(s.pos), r); g = 10 ** (snr / 20) * bgr / rms(u)
            g = min(g, 0.7 / (np.abs(u).max() + 1e-6))
            end = r.randint(int(0.4 * W), W); s.place(x, u, end, g); y = 1
            if end < W - 1600 and r.random() < 0.6:   # сразу за именем — начало команды
                c = r.choice(s.neg); gc = g * r.uniform(0.6, 1.4); seg = c[:W - end - r.randint(0, 2400)]
                s.place(x, seg, W, gc) if len(seg) > 0 else None
        elif t < 0.60:                        # имя обрезано окном: ещё не договорено → нет
            u = r.choice(s.pos); g = min(10 ** (snr / 20) * bgr / rms(u), 0.7 / (np.abs(u).max() + 1e-6))
            cut = r.uniform(0.25, 0.7); end = W + int(len(u) * cut); s.place(x, u, end, g)
        else:                                  # похожие слова / команды / фразы
            u = r.choice(s.neg); g = min(10 ** (snr / 20) * bgr / rms(u), 0.7 / (np.abs(u).max() + 1e-6))
            end = r.randint(int(0.3 * W), W + len(u) // 2); s.place(x, u, end, g)
        x *= 10 ** (r.uniform(-12, 6) / 20)
        return np.clip(x, -1, 1), y
    def batch(s, n):
        xs, ys = zip(*(s.sample() for _ in range(n)))
        return np.stack(xs), np.array(ys, np.float32)
