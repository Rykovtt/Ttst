"""Цепочка целиком на «Санта, команда» поверх тестовой музыки. new — нейросеть + команда после приглушения; old — 3.9.13."""
import sys, os, json, random, numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model, stream, data
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
MODE = sys.argv[1]; ck = torch.load(sys.argv[2]); C = np.load(sys.argv[3], allow_pickle=True); E = np.load(sys.argv[4])
vm = Model(sys.argv[5]); cmds = json.load(open(sys.argv[6])); dec = open(sys.argv[7]).read().split()
TH = float(os.environ.get("TH", "0.9")); K = int(os.environ.get("K", "2"))
net = None
if MODE in ("new", "hybrid"): net = model.KWS(); net.load_state_dict(ck["net"]); net.eval()
name = "санта"; keys = set(cmds)
LAB = {}
for c in cmds:
    LAB[c] = None
def lab(c):
    import re
    m = {"пауза":"C:PAUSE","паузу":"C:PAUSE","стоп":"C:PAUSE","хватит":"C:PAUSE","замолчи":"C:PAUSE","останови":"C:PAUSE","выключи":"C:PAUSE",
         "дальше":"C:NEXT","далее":"C:NEXT","следующий":"C:NEXT","следующий трек":"C:NEXT","следующая":"C:NEXT","переключи":"C:NEXT",
         "назад":"C:PREV","предыдущий":"C:PREV","громче":"C:LOUDER","погромче":"C:LOUDER","тише":"C:QUIETER","потише":"C:QUIETER","сделай потише":"C:QUIETER","сделай тише":"C:QUIETER",
         "включи обратно":"C:RESUME","продолжи":"C:RESUME","продолжай":"C:RESUME","плей":"C:RESUME","играй":"C:RESUME"}
    if c in m: return m[c]
    w = c.split()
    tens = {"десять":10,"двадцать":20,"тридцать":30,"сорок":40,"пятьдесят":50,"шестьдесят":60,"семьдесят":70,"восемьдесят":80,"девяносто":90,"сто":100}
    for x in w:
        if x in tens and w[0] in ("громкость","звук"): return f"V:{tens[x]}"
    return "other:" + c
def find(t, prefix_ok):
    t = [x for x in t if x]
    starts = [i + 1 for i in range(len(t)) if t[i] == name]
    if prefix_ok: starts = [0] + starts
    for s0 in reversed(starts):
        rest = t[s0:]
        for k in range(len(rest), 0, -1):
            c = " ".join(rest[:k])
            if c in keys: return c
    return None
def conf_of(o, c):
    ws = set(c.split()); cc = [w["conf"] for w in o.get("result", []) if w["word"] in ws]
    return min(cc) if cc else 1.0
G_new = json.dumps(list(dict.fromkeys(cmds + ["ты тут", "[unk]"] + [w for w in dec if w not in cmds])), ensure_ascii=False)
ph = [name, f"эй {name}", f"привет {name}", f"{name} ты тут", "[unk]"] + [f"{name} {c}" for c in cmds]
G_old = json.dumps(ph + [w for w in dec if w not in ph], ensure_ascii=False)
def stage2(audio, prefix_ok, G):
    """Команда: устойчивый кандидат → дослушать 5 чтений → решение; естественный финал — с уверенностью ≥ 0.6."""
    r = KaldiRecognizer(vm, 16000, G); r.SetWords(True); pcm = (np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes()
    last = None; st = 0; settle = -1; cand = None
    for i in range(0, len(pcm), 3200):
        fin = r.AcceptWaveform(pcm[i:i + 3200])
        if settle >= 0:
            settle -= 1
            if settle < 0 or fin:
                o = json.loads(r.Result() if fin else r.FinalResult()); c = find(o.get("text", "").split(), prefix_ok)
                return c or cand
            continue
        o = json.loads(r.Result() if fin else r.PartialResult()); txt = o.get("text" if fin else "partial", "")
        c = find(txt.split(), prefix_ok)
        if fin:
            if c and (conf_of(o, c) >= 0.6 or not prefix_ok): return c
            last = None; st = 0; continue
        st = st + 1 if c and c == last else (1 if c else 0); last = c
        if c and st >= 2: settle = 5; cand = c
    return None
music = E["music"]; rng = random.Random(9)
res = {}; out = []
for idx, (clip, nlen, lab_true) in enumerate(zip(C["clips"], C["name_len"], C["labels"])):
    clip = clip.astype(np.float32); snr = [-3, 3, 10][idx % 3]
    pre = 32000; L = pre + len(clip) + 48000; o0 = rng.randint(0, len(music) - L); bg = music[o0:o0 + L].copy()
    g = 10 ** (snr / 20) * data.rms(bg) / data.rms(clip); g = min(g, 0.8 / np.abs(clip).max())
    sp = np.zeros(L, np.float32); sp[pre:pre + len(clip)] = clip * g
    name_end = (pre + nlen) / 16000
    got = None
    if MODE == "new":
        pr, t = stream.probs(net, ck["mu"], ck["sd"], np.clip(sp + bg, -1, 1))
        trig = stream.triggers(pr, t, TH, K)
        trig = [x for x in trig if x >= pre / 16000]
        if trig:
            t0 = trig[0]; a = int((t0 - 0.3) * 16000); b = min(L, int((t0 + 4) * 16000)); d0 = int((t0 + 0.15) * 16000)
            bgd = bg.copy(); bgd[d0:] *= 0.12
            got = stage2(np.clip(sp + bgd, -1, 1)[a:b], True, G_new)
    elif MODE == "hybrid":
        # имя: грамматика Vosk (3 чтения) И нейросеть p ≥ TH рядом (±0.6 с); команда — как в new (приглушение 0.12)
        mix = np.clip(sp + bg, -1, 1)
        pr, t = stream.probs(net, ck["mu"], ck["sd"], mix)
        r = KaldiRecognizer(vm, 16000, G_old); stv = 0; t0 = None
        pcm = (mix * 32767).astype(np.int16).tobytes()
        for i in range(0, len(pcm), 3200):
            fin = r.AcceptWaveform(pcm[i:i + 3200]); tt = (i + 3200) / 32000
            txt = json.loads(r.Result() if fin else r.PartialResult()).get("text" if fin else "partial", "")
            stv = stv + 1 if name in txt.split() else 0
            if (stv >= 3 or (fin and name in txt.split())) and tt >= pre / 16000:
                if np.any((pr >= TH) & (t >= tt - 0.8) & (t <= tt + 0.4)): t0 = tt; break
        if t0 is not None:
            a = int((t0 - 0.5) * 16000); b = min(L, int((t0 + 4) * 16000)); d0 = int((t0 + 0.1) * 16000)
            bgd = bg.copy(); bgd[d0:] *= 0.12
            got = stage2(np.clip(sp + bgd, -1, 1)[a:b], True, G_old.replace('"' + name + '", ', '', 0) and G_new)
    else:
        # 3.9.13: имя по грамматике; приглушение 0.3 после 3 чтений; команда с именем или в окне — без него
        r = KaldiRecognizer(vm, 16000, G_old); r.SetWords(True); stv = 0; duck = None
        mix = sp + bg; pcmw = None; i = 0
        while i < L and got is None:
            seg = mix[i:i + 1600].copy()
            if duck is not None and i >= duck: seg = sp[i:i + 1600] + bg[i:i + 1600] * float(os.environ.get("DUCK", "0.3"))
            fin = r.AcceptWaveform((np.clip(seg, -1, 1) * 32767).astype(np.int16).tobytes()); i += 1600
            o = json.loads(r.Result() if fin else r.PartialResult()); txt = o.get("text" if fin else "partial", "")
            stv = stv + 1 if name in txt.split() else 0
            if stv >= 3 and duck is None: duck = i + 2400
            DK = float(os.environ.get("DUCK", "0.3"))
            if stv >= 5 or (fin and name in txt.split()):
                rest = sp[i:i + 64000] + bg[i:i + 64000] * (float(os.environ.get("DUCK", "0.3")) if duck is not None else 1)
                got = stage2(np.concatenate([seg, rest]), True, G_old); break
            c = find(txt.split(), False)
            if c and fin: got = c
    ok = got is not None and lab(got) == lab_true
    res.setdefault(snr, []).append(ok); out.append((lab_true, got))
print(MODE, {k: f"{np.mean(v)*100:.0f}%" for k, v in sorted(res.items())}, "всего", f"{np.mean(sum(res.values(), []))*100:.0f}%")
wrong = [o for o in out if o[1] is not None and lab(o[1]) != o[0]]
print("ложных команд (не та):", len(wrong), wrong[:8])
