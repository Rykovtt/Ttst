"""Синтез данных для детектора «Санта»: позитивы (имя), негативы (похожие слова, команды, фразы)."""
import sys, os, random, wave, subprocess, json
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig

OUT = sys.argv[1]
VOICES = sys.argv[2]
os.makedirs(OUT, exist_ok=True)
random.seed(1)

POS = ["санта", "Санта.", "Санта!", "Санта?", "Санта,", "Эй, Санта", "Привет, Санта", "Ну, Санта", "Слушай, Санта", "Санта…"]

CONFUSE = ("сантехник санки санкции сантиметр санаторий сантименты саня санёк сандра сантьяго сатана соната сатин стан санитар сауна "
           "сам там сама там сам дам сон там сонта санда санька сама тот самба салат саната сантим кантата мантра танта "
           "занята занято сенат сонет сандал сантехника санитарка самара самоса сантана саша сауна тайна танк саня там").split()
COMMANDS = ("пауза стоп дальше назад громче тише плей играй продолжи следующий трек предыдущая песня выключи музыку "
            "громкость семьдесят процентов перемотай заново останови хватит замолчи включи обратно поехали давай дальше").split()
SENT = [
 "я иду по улице одна", "сам ты знаешь как мне тяжело", "стоп не уходи постой ещё немного", "санки катятся по снегу",
 "сама не знаю что со мной", "эта песня громче всех", "тише тише ночь пришла", "давай играй гитара моя",
 "самолёт летит домой", "пауза между нами длинная", "следующий поезд через час", "ты моя половина ты мой свет",
 "сантехник пришёл в субботу", "сам там был и всё видел", "сорок дней и сорок ночей", "перемотай назад всё что было",
 "позвони мне завтра утром", "запиши меня на пятницу", "какая сегодня погода", "включи свет на кухне",
 "мы танцуем до утра", "я люблю тебя как прежде", "в этом городе нет тебя", "солнце светит нам в глаза",
 "не говори мне ничего", "это было так давно", "я снова здесь с тобой", "мама мыла раму", "сколько стоит хлеб",
 "давай поговорим потом", "где мои ключи", "открой окно пожалуйста", "у меня всё хорошо", "санкт петербург красивый город",
 "сатана там правит бал", "соната для фортепиано", "сантиметр за сантиметром", "сама по себе", "сам себе хозяин",
 "ну давай же быстрее", "слушай сюда", "привет как дела", "эй ты куда", "она поёт о любви", "назад дороги нет",
 "громче музыку соседи спят", "пусть будет так", "я не сплю всю ночь", "моё сердце бьётся", "ты слышишь меня",
 "сегодня праздник", "вот это да", "ой мама", "всё будет хорошо", "посмотри на небо", "звёзды падают вниз",
]
WORDS = ("я ты он она мы они это там тут вот так как что где когда кто нет да ещё уже снова опять очень много мало "
         "день ночь небо солнце луна звезда город дом дорога любовь сердце душа жизнь время мама папа друг брат "
         "иду бегу пою танцуем живу люблю жду знаю помню верю хочу могу буду была был были стало стал "
         "сам сама сами само сани санки саня сон сонная стол стал там так тот туда тогда теперь сейчас сегодня завтра").split()

def neg_texts(n):
    t = list(CONFUSE) + COMMANDS + SENT
    while len(t) < n:
        t.append(" ".join(random.choice(WORDS) for _ in range(random.randint(2, 7))))
    return t

def save(path, x16):
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(x16.astype(np.int16).tobytes())

def piper_say(voice, sr, text, spk, ls, ns, nw):
    cfg = SynthesisConfig(speaker_id=spk, length_scale=ls, noise_scale=ns, noise_w_scale=nw)
    a = np.concatenate([c.audio_int16_array for c in voice.synthesize(text, cfg)]).astype(np.float32)
    return resample_poly(a, 16000, sr) if sr != 16000 else a

def trim(x, thr=300):
    idx = np.where(np.abs(x) > thr)[0]
    if len(idx) == 0: return x
    return x[max(0, idx[0] - 800): idx[-1] + 800]

meta = []
for f in sorted(os.listdir(VOICES)):
    if not f.endswith(".onnx"): continue
    name = f[:-5]
    voice = PiperVoice.load(os.path.join(VOICES, f))
    sr = voice.config.sample_rate
    nspk = max(1, voice.config.num_speakers)
    for spk in range(nspk):
        tag = f"{name}-{spk}"
        for i, text in enumerate(POS * 4):
            ls = random.uniform(0.7, 1.4); ns = random.uniform(0.3, 1.0); nw = random.uniform(0.3, 1.1)
            x = trim(piper_say(voice, sr, text, spk if nspk > 1 else None, ls, ns, nw))
            p = f"{OUT}/pos_{tag}_{i:03d}.wav"; save(p, x); meta.append({"f": p, "y": 1, "spk": tag})
        for i, text in enumerate(neg_texts(300)):
            ls = random.uniform(0.75, 1.3); ns = random.uniform(0.3, 1.0); nw = random.uniform(0.3, 1.1)
            x = trim(piper_say(voice, sr, text, spk if nspk > 1 else None, ls, ns, nw))
            p = f"{OUT}/neg_{tag}_{i:03d}.wav"; save(p, x); meta.append({"f": p, "y": 0, "spk": tag})
        print(tag, flush=True)

# espeak-ng: другие «механические» голоса — шире разброс тембров
ev = ["ru", "ru+m1", "ru+m2", "ru+m3", "ru+m4", "ru+m5", "ru+m7", "ru+f1", "ru+f2", "ru+f3", "ru+f4", "ru+f5", "ru+klatt", "ru+croak"]
for v in ev:
    tag = "espeak-" + v.replace("+", "_")
    for i, text in enumerate(POS * 3):
        sp = random.randint(110, 200); pi = random.randint(20, 85)
        subprocess.run(["espeak-ng", "-v", v, "-s", str(sp), "-p", str(pi), "-w", "/dev/shm/e.wav", text], check=True)
        w = wave.open("/dev/shm/e.wav"); a = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32); r = w.getframerate()
        x = trim(resample_poly(a, 16000, r)); p = f"{OUT}/pos_{tag}_{i:03d}.wav"; save(p, x); meta.append({"f": p, "y": 1, "spk": tag})
    for i, text in enumerate(neg_texts(150)):
        sp = random.randint(110, 200); pi = random.randint(20, 85)
        subprocess.run(["espeak-ng", "-v", v, "-s", str(sp), "-p", str(pi), "-w", "/dev/shm/e.wav", text], check=True)
        w = wave.open("/dev/shm/e.wav"); a = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32); r = w.getframerate()
        x = trim(resample_poly(a, 16000, r)); p = f"{OUT}/neg_{tag}_{i:03d}.wav"; save(p, x); meta.append({"f": p, "y": 0, "spk": tag})
    print(tag, flush=True)
json.dump(meta, open(f"{OUT}/meta.json", "w"), ensure_ascii=False)
print("total", len(meta), "pos", sum(m["y"] for m in meta))
