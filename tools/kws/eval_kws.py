import sys, os, numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model, stream
ck = torch.load(sys.argv[1]); E = np.load(sys.argv[2])
net = model.KWS(); net.load_state_dict(ck["net"]); net.eval(); mu, sd = ck["mu"], ck["sd"]
pm, tm = stream.probs(net, mu, sd, E["music"]); pv, tv = stream.probs(net, mu, sd, E["voc"])
ins = [stream.probs(net, mu, sd, x) for x in E["ins"]]
hrs_m = len(E["music"]) / 16000 / 3600; hrs_v = len(E["voc"]) / 16000 / 3600
print("th   k  FA/ч музыка  FA/ч вокал  найдено: музыка(-3,3,10) | вокал(-3,3,10) | тишина")
for k in (1, 2):
    for th in (0.5, 0.7, 0.8, 0.9, 0.95, 0.98):
        fm = len(stream.triggers(pm, tm, th, k)) / hrs_m; fv = len(stream.triggers(pv, tv, th, k)) / hrs_v
        hit = {}
        for (pr, t), e, (kind, snr) in zip(ins, E["ends"], E["conds"]):
            tr = stream.triggers(pr, t, th, k); ok = any(e - 0.3 <= x <= e + 1.0 for x in tr)
            hit.setdefault((kind, snr), []).append(ok)
        f = lambda kind: " ".join(f"{np.mean(hit[(kind, s)])*100:3.0f}" for s in (-3, 3, 10))
        print(f"{th:.2f} {k}  {fm:8.1f}  {fv:9.1f}   {f(0)} | {f(1)} | {np.mean(hit[(2, 20)])*100:3.0f}")
