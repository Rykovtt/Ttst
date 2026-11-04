import torch, torch.nn as nn
class KWS(nn.Module):
    """Маленькая CNN: 4 свёртки 3×3 (шаг 2,2,2,1) + плотный слой-эмбеддинг 32 + выход."""
    def __init__(s, emb=48, ch=(16, 32, 48, 48)):
        super().__init__()
        def blk(i, o, st): return nn.Sequential(nn.Conv2d(i, o, 3, st, 1, bias=False), nn.BatchNorm2d(o), nn.ReLU())
        s.c = nn.Sequential(blk(1, ch[0], 2), blk(ch[0], ch[1], 2), blk(ch[1], ch[2], 2), blk(ch[2], ch[3], 1))
        s.fc = nn.Linear(ch[3] * 5 * 13, emb); s.out = nn.Linear(emb, 1)
    def embed(s, f):            # f: (B, 40, 97) нормированные признаки
        h = s.c(f.unsqueeze(1))
        return torch.relu(s.fc(h.flatten(1)))
    def forward(s, f): return s.out(s.embed(f)).squeeze(1)
