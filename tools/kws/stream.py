"""Поток как в Kotlin: кадр каждые 160 отсчётов (последние 512), сеть каждые 10 кадров по последним 97."""
import numpy as np, torch, feat
def probs(net, mu, sd, x, every=10):
    x = torch.tensor(x, dtype=torch.float32)
    fr = x.unfold(0, feat.NFFT, feat.HOP) * feat.WINDOW
    sp = torch.fft.rfft(fr, dim=-1); p = sp.real ** 2 + sp.imag ** 2
    lm = torch.log(p @ feat.MEL.T + 1e-6)          # (T, 40)
    T = lm.shape[0]; idx = list(range(feat.FRAMES - 1, T, every))
    if not idx: return np.zeros(0), np.zeros(0)
    win = torch.stack([lm[i - feat.FRAMES + 1:i + 1].T for i in idx])     # (N, 40, 97)
    with torch.no_grad():
        out = []
        for k in range(0, len(win), 512):
            f = (win[k:k + 512] - mu.squeeze(-1).squeeze(0)[None, :, None]) / sd.squeeze(-1).squeeze(0)[None, :, None]
            out.append(torch.sigmoid(net(f)))
        pr = torch.cat(out).numpy()
    t_end = np.array([(i * feat.HOP + feat.NFFT) / feat.SR for i in idx])    # время конца окна, с
    return pr, t_end
def triggers(pr, t, th, k=2, refractory=2.0):
    res = []; run = 0; last = -1e9
    for p, tt in zip(pr, t):
        run = run + 1 if p >= th else 0
        if run >= k and tt - last > refractory: res.append(tt); last = tt; run = 0
    return res
