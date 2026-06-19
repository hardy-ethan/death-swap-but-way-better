#!/usr/bin/env python3
"""Minecraft region (.mca) + NBT tool — read, survey, and export structures.

Self-contained (stdlib only). Uses a *typed* NBT model so block entities and
block states round-trip exactly into a vanilla structure (.nbt) file.

Modes:
  cmd        list command blocks (x,y,z,auto,Command)
  be-ids     count block-entity ids
  signs      dump sign positions + text lines
  slice      print a Y-slice block map over an X/Z box (debug bounds)
  nonair     report the non-air bounding box within an X/Z/Y box
  export     write blocks in a box to a gzipped structure .nbt

Examples:
  python3 scripts/mca_read.py <region_dir> cmd
  python3 scripts/mca_read.py <region_dir> signs
  python3 scripts/mca_read.py <region_dir> slice X1 Z1 X2 Z2 Y
  python3 scripts/mca_read.py <region_dir> nonair X1 Y1 Z1 X2 Y2 Z2
  python3 scripts/mca_read.py <region_dir> export X1 Y1 Z1 X2 Y2 Z2 out.nbt [--no-air]
"""
import sys, os, struct, zlib, gzip, glob

# ====================== typed NBT model ======================
class Byte(int): tid = 1
class Short(int): tid = 2
class Int(int): tid = 3
class Long(int): tid = 4
class Float(float): tid = 5
class Double(float): tid = 6
class ByteArray(bytes): tid = 7
class String(str): tid = 8
class List(list):
    tid = 9
    def __init__(self, subtype=0, items=()):
        super().__init__(items); self.subtype = subtype
class Compound(dict): tid = 10
class IntArray(list): tid = 11
class LongArray(list): tid = 12

# ====================== NBT reader ======================
def _read(b, o, n):
    return b[o:o+n], o + n

def parse_tag(b, o, tid):
    if tid == 1:  return Byte(struct.unpack_from('>b', b, o)[0]), o+1
    if tid == 2:  return Short(struct.unpack_from('>h', b, o)[0]), o+2
    if tid == 3:  return Int(struct.unpack_from('>i', b, o)[0]), o+4
    if tid == 4:  return Long(struct.unpack_from('>q', b, o)[0]), o+8
    if tid == 5:  return Float(struct.unpack_from('>f', b, o)[0]), o+4
    if tid == 6:  return Double(struct.unpack_from('>d', b, o)[0]), o+8
    if tid == 7:
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a, o = _read(b, o, n); return ByteArray(a), o
    if tid == 8:
        n = struct.unpack_from('>H', b, o)[0]; o += 2
        s, o = _read(b, o, n); return String(s.decode('utf-8', 'replace')), o
    if tid == 9:
        itid = b[o]; o += 1
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        lst = List(itid, [])
        for _ in range(n):
            v, o = parse_tag(b, o, itid); lst.append(v)
        return lst, o
    if tid == 10:
        d = Compound()
        while True:
            ctid = b[o]; o += 1
            if ctid == 0: break
            nl = struct.unpack_from('>H', b, o)[0]; o += 2
            nm, o = _read(b, o, nl); nm = nm.decode('utf-8', 'replace')
            v, o = parse_tag(b, o, ctid); d[nm] = v
        return d, o
    if tid == 11:
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a = IntArray(struct.unpack_from('>%di' % n, b, o)); return a, o+4*n
    if tid == 12:
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a = LongArray(struct.unpack_from('>%dq' % n, b, o)); return a, o+8*n
    raise ValueError("bad tag %d @ %d" % (tid, o))

def parse_nbt(b):
    nl = struct.unpack_from('>H', b, 1)[0]
    v, _ = parse_tag(b, 3 + nl, b[0])
    return v

# ====================== NBT writer ======================
def tag_id(v):
    if isinstance(v, bool): return 1
    if isinstance(v, (Byte, Short, Int, Long, Float, Double, ByteArray,
                      String, List, Compound, IntArray, LongArray)):
        return v.tid
    if isinstance(v, str): return 8
    if isinstance(v, float): return 6
    if isinstance(v, int): return 3
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError("no nbt id for %r" % type(v))

def write_payload(out, v, tid):
    if tid == 1:  out += struct.pack('>b', int(v))
    elif tid == 2: out += struct.pack('>h', int(v))
    elif tid == 3: out += struct.pack('>i', int(v))
    elif tid == 4: out += struct.pack('>q', int(v))
    elif tid == 5: out += struct.pack('>f', float(v))
    elif tid == 6: out += struct.pack('>d', float(v))
    elif tid == 7:
        out += struct.pack('>i', len(v)); out += bytes(v)
    elif tid == 8:
        s = v.encode('utf-8'); out += struct.pack('>H', len(s)); out += s
    elif tid == 9:
        sub = getattr(v, 'subtype', 0) or (tag_id(v[0]) if v else 0)
        out += struct.pack('>b', sub); out += struct.pack('>i', len(v))
        for it in v: write_payload(out, it, sub)
    elif tid == 10:
        for k, val in v.items():
            t = tag_id(val)
            out += struct.pack('>b', t)
            ks = k.encode('utf-8'); out += struct.pack('>H', len(ks)); out += ks
            write_payload(out, val, t)
        out += b'\x00'
    elif tid == 11:
        out += struct.pack('>i', len(v))
        for it in v: out += struct.pack('>i', int(it))
    elif tid == 12:
        out += struct.pack('>i', len(v))
        for it in v: out += struct.pack('>q', int(it))
    else:
        raise TypeError("bad tid %d" % tid)

def write_nbt(root, root_name=''):
    out = bytearray()
    out += struct.pack('>b', 10)
    ks = root_name.encode('utf-8'); out += struct.pack('>H', len(ks)); out += ks
    write_payload(out, root, 10)
    return bytes(out)

# ====================== region reader ======================
def region_chunks(path):
    with open(path, 'rb') as f:
        data = f.read()
    header = data[:4096]
    for i in range(1024):
        off = struct.unpack('>I', b'\x00' + header[i*4:i*4+3])[0]
        if off == 0: continue
        start = off * 4096
        length = struct.unpack_from('>I', data, start)[0]
        comp = data[start+4]
        raw = data[start+5:start+4+length]
        try:
            payload = (gzip.decompress(raw) if comp == 1 else
                       zlib.decompress(raw) if comp == 2 else raw)
        except Exception:
            continue
        yield parse_nbt(payload)

def all_chunks(region_dir):
    for path in sorted(glob.glob(os.path.join(region_dir, 'r.*.mca'))):
        for ch in region_chunks(path):
            yield ch

# ====================== world block access ======================
class World:
    """Random block access across a region dir, with a chunk cache."""
    def __init__(self, region_dir):
        self.dir = region_dir
        self.chunks = {}     # (cx,cz) -> chunk compound
        self.loaded_regions = set()
        self.data_version = 4790

    def _load_region(self, rx, rz):
        key = (rx, rz)
        if key in self.loaded_regions: return
        self.loaded_regions.add(key)
        path = os.path.join(self.dir, 'r.%d.%d.mca' % (rx, rz))
        if not os.path.exists(path): return
        for ch in region_chunks(path):
            cx, cz = ch.get('xPos'), ch.get('zPos')
            if cx is None: continue
            self.chunks[(cx, cz)] = ch
            dv = ch.get('DataVersion')
            if dv: self.data_version = int(dv)

    def chunk(self, cx, cz):
        if (cx, cz) not in self.chunks:
            self._load_region(cx >> 5, cz >> 5)
        return self.chunks.get((cx, cz))

    def block_entities(self, cx, cz):
        ch = self.chunk(cx, cz)
        out = {}
        if not ch: return out
        for be in (ch.get('block_entities') or []):
            out[(int(be['x']), int(be['y']), int(be['z']))] = be
        return out

    def section_grid(self, ch, sy):
        for s in (ch.get('sections') or []):
            if int(s.get('Y', -999)) == sy:
                bs = s.get('block_states')
                if not bs: return None
                pal = bs['palette']
                if 'data' not in bs:
                    return pal, [0] * 4096
                data = bs['data']
                bits = max(4, (len(pal) - 1).bit_length())
                per = 64 // bits
                mask = (1 << bits) - 1
                idxs = [0] * 4096
                p = 0
                for lv in data:
                    u = lv & 0xFFFFFFFFFFFFFFFF
                    for k in range(per):
                        if p >= 4096: break
                        idxs[p] = (u >> (k * bits)) & mask
                        p += 1
                    if p >= 4096: break
                return pal, idxs
        return None

    def block(self, x, y, z):
        ch = self.chunk(x >> 4, z >> 4)
        if not ch: return None
        g = self.section_grid(ch, y >> 4)
        if not g: return None
        pal, idxs = g
        li = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
        return pal[idxs[li]]

def pal_name(entry):
    return entry.get('Name', 'minecraft:air')

def pal_key(entry):
    name = entry.get('Name', 'minecraft:air')
    props = entry.get('Properties')
    if not props: return name
    return name + '[' + ','.join('%s=%s' % (k, props[k]) for k in sorted(props)) + ']'

# ====================== modes ======================
def mode_cmd(region_dir):
    rows = []
    for ch in all_chunks(region_dir):
        for be in (ch.get('block_entities') or []):
            if 'command_block' in str(be.get('id', '')):
                rows.append((int(be['x']), int(be['y']), int(be['z']),
                             str(be['id']).replace('minecraft:', ''),
                             be.get('auto'), be.get('Command') or ''))
    rows.sort(key=lambda r: (r[1], r[0], r[2]))
    print("# %d command blocks" % len(rows))
    for x, y, z, bid, auto, cmd in rows:
        print("[%d %d %d] %s auto=%s : %s" % (x, y, z, bid, auto, cmd))

def mode_be_ids(region_dir):
    from collections import Counter
    c = Counter()
    for ch in all_chunks(region_dir):
        for be in (ch.get('block_entities') or []):
            c[str(be.get('id', ''))] += 1
    for k, v in c.most_common():
        print("%6d  %s" % (v, k))

def mode_signs(region_dir):
    rows = []
    for ch in all_chunks(region_dir):
        for be in (ch.get('block_entities') or []):
            if 'sign' in str(be.get('id', '')):
                txt = []
                for side in ('front_text', 'back_text'):
                    st = be.get(side)
                    if st and st.get('messages'):
                        txt.append(side + ':' + ' | '.join(str(m) for m in st['messages']))
                rows.append((int(be['x']), int(be['y']), int(be['z']), ' /// '.join(txt)))
    rows.sort(key=lambda r: (r[1], r[0], r[2]))
    print("# %d signs" % len(rows))
    for x, y, z, t in rows:
        print("[%d %d %d] %s" % (x, y, z, t))

def mode_nonair(region_dir, box):
    x1, y1, z1, x2, y2, z2 = box
    w = World(region_dir)
    mnx = mny = mnz = 10**9; mxx = mxy = mxz = -10**9; n = 0
    for y in range(min(y1, y2), max(y1, y2) + 1):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                b = w.block(x, y, z)
                if b and pal_name(b) != 'minecraft:air':
                    n += 1
                    mnx, mxx = min(mnx, x), max(mxx, x)
                    mny, mxy = min(mny, y), max(mxy, y)
                    mnz, mxz = min(mnz, z), max(mxz, z)
    if n == 0:
        print("no non-air blocks in box"); return
    print("non-air count: %d" % n)
    print("bbox: %d %d %d  ..  %d %d %d" % (mnx, mny, mnz, mxx, mxy, mxz))
    print("size: %d x %d x %d" % (mxx-mnx+1, mxy-mny+1, mxz-mnz+1))

def mode_slice(region_dir, x1, z1, x2, z2, y):
    w = World(region_dir)
    xs = range(min(x1, x2), max(x1, x2) + 1)
    zs = range(min(z1, z2), max(z1, z2) + 1)
    print("# Y=%d  rows=z %d..%d  cols=x %d..%d" % (y, min(zs), max(zs), min(xs), max(xs)))
    legend = {}
    nextc = ord('A')
    for z in zs:
        line = []
        for x in xs:
            b = w.block(x, y, z)
            nm = pal_name(b) if b else 'minecraft:air'
            if nm == 'minecraft:air':
                line.append('.')
            else:
                short = nm.replace('minecraft:', '')
                if short not in legend:
                    legend[short] = chr(nextc); nextc += 1
                line.append(legend[short])
        print('%6d ' % z + ''.join(line))
    print("legend:", {v: k for k, v in legend.items()})

def mode_export(region_dir, box, outfile, include_air=True):
    x1, y1, z1, x2, y2, z2 = box
    x1, x2 = min(x1, x2), max(x1, x2)
    y1, y2 = min(y1, y2), max(y1, y2)
    z1, z2 = min(z1, z2), max(z1, z2)
    w = World(region_dir)
    sx, sy, sz = x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1

    palette = []          # list of Compound entries
    pal_index = {}        # pal_key -> index
    blocks = List(10, [])
    be_cache = {}         # (cx,cz) -> {(x,y,z): be}
    n_be = 0

    def get_be(x, y, z):
        ck = (x >> 4, z >> 4)
        if ck not in be_cache:
            be_cache[ck] = w.block_entities(*ck)
        return be_cache[ck].get((x, y, z))

    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                b = w.block(x, y, z)
                if b is None:
                    b = Compound(); b['Name'] = String('minecraft:air')
                if not include_air and pal_name(b) == 'minecraft:air':
                    continue
                key = pal_key(b)
                if key not in pal_index:
                    pal_index[key] = len(palette)
                    palette.append(b)
                entry = Compound()
                entry['state'] = Int(pal_index[key])
                pos = List(3, [Int(x - x1), Int(y - y1), Int(z - z1)])
                entry['pos'] = pos
                be = get_be(x, y, z)
                if be is not None:
                    nbt = Compound()
                    for k, val in be.items():
                        if k in ('x', 'y', 'z'):  # structure stores relative pos separately
                            continue
                        nbt[k] = val
                    entry['nbt'] = nbt
                    n_be += 1
                blocks.append(entry)

    pal_list = List(10, palette)
    root = Compound()
    root['DataVersion'] = Int(w.data_version)
    root['size'] = List(3, [Int(sx), Int(sy), Int(sz)])
    root['palette'] = pal_list
    root['blocks'] = blocks
    root['entities'] = List(10, [])

    raw = write_nbt(root, '')
    with gzip.open(outfile, 'wb') as f:
        f.write(raw)
    print("wrote %s" % outfile)
    print("size: %d x %d x %d   blocks=%d   palette=%d   block_entities=%d"
          % (sx, sy, sz, len(blocks), len(palette), n_be))

# ====================== CLI ======================
if __name__ == '__main__':
    region_dir = sys.argv[1] if len(sys.argv) > 1 else \
        'dimensions/minecraft/overworld/region'
    mode = sys.argv[2] if len(sys.argv) > 2 else 'cmd'
    a = sys.argv[3:]
    if mode == 'cmd':
        mode_cmd(region_dir)
    elif mode == 'be-ids':
        mode_be_ids(region_dir)
    elif mode == 'signs':
        mode_signs(region_dir)
    elif mode == 'slice':
        mode_slice(region_dir, int(a[0]), int(a[1]), int(a[2]), int(a[3]), int(a[4]))
    elif mode == 'nonair':
        mode_nonair(region_dir, [int(v) for v in a[:6]])
    elif mode == 'export':
        box = [int(v) for v in a[:6]]
        outfile = a[6]
        include_air = '--no-air' not in a
        mode_export(region_dir, box, outfile, include_air)
    else:
        print("unknown mode:", mode); sys.exit(2)
