"""Трудные негативы: окна обучающей музыки и «вокала» (обучающие голоса), где сеть ошибочно видит имя."""
import sys, os, random, numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model, stream, data
torch.set_num_threads(4)
ck = torch.load(sys.argv[1]); D, M, OUT = sys.argv[2], sys.argv[3], sys.argv[4]; TH = float(sys.argv[5]) if len(sys.argv) > 5 else 0.4
net = model.KWS(); net.load_state_dict(ck["net"]); net.eval()
S, mus = data.load(D, M); r = random.Random(4)
CAP = 30000; wins = []; seen = 0
def mine(x):
    global seen
    pr, t = stream.probs(net, ck["mu"], ck["sd"], x); last = -1
    for p, tt in zip(pr, t):
        if p >= TH and tt - last >= 0.3:
            last = tt; e = int(tt * 16000); s0 = e - 16000
            if s0 < 0: continue
            w = (np.clip(x[s0:e], -1, 1) * 32767).astype(np.int16); seen += 1
            if len(wins) < CAP: wins.append(w)
            else:
                j = r.randint(0, seen - 1)
                if j < CAP: wins[j] = w
for i, m in enumerate(mus["train"]):
    mine(m)
    # тот же клип + чужая речь поверх (обучающие голоса)
    v = m.copy(); pos = 0
    while pos < len(v):
        u = r.choice(S["train"]["neg"]); g = 10 ** (r.uniform(-4, 8) / 20) * data.rms(v[pos:pos + 16000]) / data.rms(u)
        b = min(len(v), pos + len(u)); v[pos:b] += u[:b - pos] * g; pos = b + r.randint(0, 6000)
    mine(np.clip(v, -1, 1))
    if i % 100 == 0: print(i, seen, flush=True)
np.save(OUT, np.stack(wins)); print("mined", seen, "saved", len(wins))
