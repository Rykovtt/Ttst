# Нейросеть на имя «Санта» (assets/kws/santa.bin)

Обучается только на синтезированной речи и открытой музыке, всё офлайн. На телефоне считается кодом `KwsModel.kt` (без библиотек).

1. Голоса Piper (ru_RU irina/denis/dmitri/ruslan, uk_UA lada/ukrainian_tts, en_US libritts_r) и espeak-ng:
   `gen.py data voices`, `gen_pos.py data voices`, `gen_en.py data voices_en/en_US-libritts_r-medium.onnx`.
2. Музыка — 1000 клипов `lewtun/music_genres_small` (16 кГц PCM в `music/`), 10% — только для проверки.
3. `train.py data music m2.pt 40000` → трудные негативы `mine.py m2.pt data music hard.npy 0.4` →
   дообучение `train.py data music m3.pt 15000 m2.pt hard.npy` → второй круг (`m4.pt`, порог 0.3).
4. Проверка: `evalset.py` → `eval_kws.py m4.pt eval.npz`; цепочка «Санта, команда» — `gen_cmd.py`, `eval_pipe.py`.
5. Экспорт: `export.py m4.pt app/src/main/assets/kws/santa.bin`, эталон для теста `vector.py` → `app/src/test/resources/kws_vector.bin`.

Тестовые голоса (ruslan, ukrainian_tts #2, espeak f3/m5, en ≥ 860) в обучении не участвуют.
