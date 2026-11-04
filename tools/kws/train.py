import sys, os, time, random, numpy as np, torch
sys.path.insert(0, os.path.dirname(__file__))
import feat, model, data
torch.set_num_threads(4)
D, M, OUT, STEPS = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
INIT = sys.argv[5] if len(sys.argv) > 5 else None; HARD = sys.argv[6] if len(sys.argv) > 6 else None
S, mus = data.load(D, M)
print({p: {k: len(v) for k, v in S[p].items()} for p in S}, {k: len(v) for k, v in mus.items()}, flush=True)
tr = data.Mixer(S, mus, "train", random.Random(1)); te = data.Mixer(S, mus, "test", random.Random(2))
if HARD:
    tr.hard = np.load(HARD, mmap_mode="r"); print("hard", len(tr.hard), flush=True)
# нормировка признаков
net = model.KWS()
if INIT:
    c0 = torch.load(INIT); net.load_state_dict(c0["net"]); mu, sd = c0["mu"], c0["sd"]; LR = 1e-3
else:
    f0 = feat.logmel(torch.tensor(tr.batch(512)[0])); mu = f0.mean(dim=(0, 2), keepdim=True); sd = f0.std(dim=(0, 2), keepdim=True); LR = 3e-3
opt = torch.optim.AdamW(net.parameters(), LR, weight_decay=1e-4)
sched = torch.optim.lr_scheduler.OneCycleLR(opt, LR, total_steps=STEPS)
Xte, yte = te.batch(4000); Fte = (feat.logmel(torch.tensor(Xte)) - mu) / sd; yte = torch.tensor(yte)
def specaug(f):
    B = f.shape[0]
    for _ in range(2):
        w = random.randint(0, 6); s0 = random.randint(0, 40 - w); f[:, s0:s0 + w, :] = 0
        w = random.randint(0, 10); s0 = random.randint(0, 97 - w); f[:, :, s0:s0 + w] = 0
    f = f + torch.randn(B, 40, 1) * 0.15   # наклон/окраска микрофона
    return f
t0 = time.time()
for step in range(1, STEPS + 1):
    X, y = tr.batch(128)
    f = (feat.logmel(torch.tensor(X)) - mu) / sd
    net.train(); loss = torch.nn.functional.binary_cross_entropy_with_logits(net(specaug(f)), torch.tensor(y), pos_weight=torch.tensor(1.0))
    opt.zero_grad(); loss.backward(); opt.step(); sched.step()
    if step % 500 == 0 or step == STEPS:
        net.eval()
        with torch.no_grad(): p = torch.sigmoid(net(Fte))
        acc = ((p > 0.5).float() == yte).float().mean().item()
        fpr = (p[yte == 0] > 0.5).float().mean().item(); tpr = (p[yte == 1] > 0.5).float().mean().item()
        print(f"{step} loss {loss.item():.3f} test acc {acc:.3f} tpr {tpr:.3f} fpr {fpr:.4f} {time.time()-t0:.0f}s", flush=True)
torch.save({"net": net.state_dict(), "mu": mu, "sd": sd}, OUT)
