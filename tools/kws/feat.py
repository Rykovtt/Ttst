"""Признаки, одинаковые с Kotlin: окно 512 (Ханн 400 по центру), шаг 160, 40 мел-полос, log."""
import numpy as np, torch
SR = 16000; NFFT = 512; WIN = 400; HOP = 160; NMEL = 40; WINDOW_S = 16000
FRAMES = 1 + (WINDOW_S - NFFT) // HOP   # 97

def hz2mel(f): return 2595.0 * np.log10(1.0 + f / 700.0)
def mel2hz(m): return 700.0 * (10 ** (m / 2595.0) - 1.0)

def mel_matrix(fmin=20.0, fmax=7600.0):
    nb = NFFT // 2 + 1
    freqs = np.arange(nb) * SR / NFFT
    pts = mel2hz(np.linspace(hz2mel(fmin), hz2mel(fmax), NMEL + 2))
    m = np.zeros((NMEL, nb), np.float32)
    for i in range(NMEL):
        l, c, r = pts[i], pts[i + 1], pts[i + 2]
        up = (freqs - l) / (c - l); down = (r - freqs) / (r - c)
        m[i] = np.maximum(0, np.minimum(up, down))
    return m

def window():
    w = np.zeros(NFFT, np.float32)
    n = np.arange(WIN)
    w[(NFFT - WIN) // 2:(NFFT - WIN) // 2 + WIN] = 0.5 - 0.5 * np.cos(2 * np.pi * n / WIN)   # периодический Ханн
    return w

MEL = torch.tensor(mel_matrix()); WINDOW = torch.tensor(window())

def logmel(x):
    """x: (B, N) float в [-1,1] → (B, NMEL, T)."""
    fr = x.unfold(1, NFFT, HOP) * WINDOW            # (B, T, 512)
    spec = torch.fft.rfft(fr, dim=-1)
    p = spec.real ** 2 + spec.imag ** 2              # (B, T, 257)
    m = p @ MEL.T                                    # (B, T, 40)
    return torch.log(m + 1e-6).transpose(1, 2)
