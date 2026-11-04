import sys, os, struct, numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import feat, model
ck = torch.load(sys.argv[1]); out = sys.argv[2]
net = model.KWS(); net.load_state_dict(ck["net"]); net.eval()
buf = bytearray(struct.pack("<i", 0x4B575331))
def arr(a):
    a = np.asarray(a, np.float32).ravel(); buf.extend(struct.pack("<i", a.size)); buf.extend(a.astype("<f4").tobytes())
arr(feat.window()); arr(feat.mel_matrix()); arr(ck["mu"].squeeze().numpy()); arr(ck["sd"].squeeze().numpy())
blocks = list(net.c); buf.extend(struct.pack("<i", len(blocks)))
for b in blocks:
    conv, bn = b[0], b[1]
    s = (bn.weight / torch.sqrt(bn.running_var + bn.eps)).detach()
    w = conv.weight.detach() * s[:, None, None, None]; bb = (bn.bias - bn.running_mean * s).detach()
    buf.extend(struct.pack("<iii", conv.in_channels, conv.out_channels, conv.stride[0]))
    arr(w.numpy()); arr(bb.numpy())
arr(net.fc.weight.detach().numpy()); arr(net.fc.bias.detach().numpy())
arr(net.out.weight.detach().numpy()); arr(net.out.bias.detach().numpy())
open(out, "wb").write(bytes(buf)); print("bytes", len(buf))
