"""Тестовый вектор для Kotlin: звук (2.5 с) и ожидаемые вероятности потока."""
import sys, os, struct, numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model, stream, data
ck = torch.load(sys.argv[1]); out = sys.argv[2]
net = model.KWS(); net.load_state_dict(ck["net"]); net.eval()
E = np.load(sys.argv[3]); x = E["ins"][2][:40000]           # тишина + «Санта»
pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
pr, _ = stream.probs(net, ck["mu"], ck["sd"], pcm.astype(np.float32) / 32768)
with torch.no_grad():
    pass
buf = struct.pack("<i", len(pcm)) + pcm.astype("<i2").tobytes() + struct.pack("<i", len(pr)) + pr.astype("<f4").tobytes()
open(out, "wb").write(buf); print(len(pcm), len(pr), pr.max())
