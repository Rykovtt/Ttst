"""Фразы «Санта, команда» тестовыми голосами (их не было в обучении) — для проверки цепочки целиком."""
import sys, os, json, random, wave, numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig
VOICES, OUT = sys.argv[1], sys.argv[2]; random.seed(3)
CMDS = [("пауза", "C:PAUSE"), ("дальше", "C:NEXT"), ("назад", "C:PREV"), ("громче", "C:LOUDER"), ("тише", "C:QUIETER"),
        ("включи обратно", "C:RESUME"), ("следующий трек", "C:NEXT"), ("стоп", "C:PAUSE"), ("громкость семьдесят", "V:70"),
        ("громкость на пятьдесят процентов", "V:50"), ("сделай потише", "C:QUIETER"), ("продолжи", "C:RESUME")]
items = []
for f, spks in (("ru_RU-ruslan-medium.onnx", [None]), ("uk_UA-ukrainian_tts-medium.onnx", [2])):
    v = PiperVoice.load(os.path.join(VOICES, f)); sr = v.config.sample_rate
    for spk in spks:
        for rep in range(4):
            for c, lab in CMDS:
                for pause in (False, True):
                    def say(t):
                        a = np.concatenate([x.audio_int16_array for x in v.synthesize(t, SynthesisConfig(speaker_id=spk, length_scale=random.uniform(0.85, 1.25), noise_scale=0.667, noise_w_scale=0.8))]).astype(np.float32)
                        a = resample_poly(a, 16000, sr); i = np.where(np.abs(a) > 300)[0]; return a[max(0, i[0] - 800):i[-1] + 800] / 32768
                    name = say("Санта"); cmd = say(c)
                    gap = np.zeros(int(16000 * (random.uniform(0.8, 1.4) if pause else random.uniform(0.05, 0.2))), np.float32)
                    items.append((np.concatenate([name, gap, cmd]), len(name), lab))
x = np.zeros(sum(len(i[0]) for i in items), np.float32)
np.savez_compressed(OUT, clips=np.array([i[0] for i in items], dtype=object), name_len=np.array([i[1] for i in items]), labels=np.array([i[2] for i in items]))
print(len(items))
