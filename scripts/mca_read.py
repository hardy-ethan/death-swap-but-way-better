#!/usr/bin/env python3
"""Minimal Minecraft region (.mca) + NBT reader.

Extracts block entities (command blocks, signs, etc.) and can survey the
hand-built hub. Self-contained: only stdlib (struct/zlib/gzip).
"""
import sys, os, struct, zlib, gzip, io, glob

# ---- NBT parser (big-endian) ----
def _r(b, o, n):
    return b[o:o+n], o+n

def parse_tag(b, o, tid):
    if tid == 0:   # END
        return None, o
    if tid == 1:   # BYTE
        return struct.unpack_from('>b', b, o)[0], o+1
    if tid == 2:   # SHORT
        return struct.unpack_from('>h', b, o)[0], o+2
    if tid == 3:   # INT
        return struct.unpack_from('>i', b, o)[0], o+4
    if tid == 4:   # LONG
        return struct.unpack_from('>q', b, o)[0], o+8
    if tid == 5:   # FLOAT
        return struct.unpack_from('>f', b, o)[0], o+4
    if tid == 6:   # DOUBLE
        return struct.unpack_from('>d', b, o)[0], o+8
    if tid == 7:   # BYTE_ARRAY
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a, o = _r(b, o, n); return list(a), o
    if tid == 8:   # STRING
        n = struct.unpack_from('>H', b, o)[0]; o += 2
        s, o = _r(b, o, n); return s.decode('utf-8', 'replace'), o
    if tid == 9:   # LIST
        itid = b[o]; o += 1
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        lst = []
        for _ in range(n):
            v, o = parse_tag(b, o, itid); lst.append(v)
        return lst, o
    if tid == 10:  # COMPOUND
        d = {}
        while True:
            ctid = b[o]; o += 1
            if ctid == 0:
                break
            nl = struct.unpack_from('>H', b, o)[0]; o += 2
            nm, o = _r(b, o, nl); nm = nm.decode('utf-8', 'replace')
            v, o = parse_tag(b, o, ctid); d[nm] = v
        return d, o
    if tid == 11:  # INT_ARRAY
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a = list(struct.unpack_from('>%di' % n, b, o)); return a, o+4*n
    if tid == 12:  # LONG_ARRAY
        n = struct.unpack_from('>i', b, o)[0]; o += 4
        a = list(struct.unpack_from('>%dq' % n, b, o)); return a, o+8*n
    raise ValueError("bad tag %d at %d" % (tid, o))

def parse_nbt(b):
    # root: tag id, name, payload
    tid = b[0]
    nl = struct.unpack_from('>H', b, 1)[0]
    o = 3 + nl
    v, _ = parse_tag(b, o, tid)
    return v

# ---- region reader ----
def chunks(path):
    with open(path, 'rb') as f:
        data = f.read()
    header = data[:4096]
    for i in range(1024):
        off, = struct.unpack_from('>I', b'\x00' + header[i*4:i*4+3], 0)
        cnt = header[i*4+3]
        if off == 0:
            continue
        start = off * 4096
        length = struct.unpack_from('>I', data, start)[0]
        comp = data[start+4]
        raw = data[start+5:start+4+length]
        try:
            if comp == 1:
                payload = gzip.decompress(raw)
            elif comp == 2:
                payload = zlib.decompress(raw)
            else:
                payload = raw
        except Exception as e:
            continue
        yield parse_nbt(payload)

def iter_block_entities(region_dir):
    for path in sorted(glob.glob(os.path.join(region_dir, 'r.*.mca'))):
        for ch in chunks(path):
            bes = ch.get('block_entities') or []
            for be in bes:
                yield be

if __name__ == '__main__':
    region_dir = sys.argv[1] if len(sys.argv) > 1 else \
        'dimensions/minecraft/overworld/region'
    mode = sys.argv[2] if len(sys.argv) > 2 else 'cmd'
    if mode == 'cmd':
        rows = []
        for be in iter_block_entities(region_dir):
            bid = str(be.get('id', ''))
            if 'command_block' in bid:
                rows.append((be.get('x'), be.get('y'), be.get('z'), bid,
                             be.get('auto'), (be.get('Command') or '')))
        rows.sort(key=lambda r: (r[1], r[0], r[2]))
        print("# %d command blocks" % len(rows))
        for x, y, z, bid, auto, cmd in rows:
            print("[%s %s %s] %s auto=%s : %s" %
                  (x, y, z, bid.replace('minecraft:', ''), auto, cmd))
    elif mode == 'be-ids':
        from collections import Counter
        c = Counter()
        for be in iter_block_entities(region_dir):
            c[str(be.get('id', ''))] += 1
        for k, v in c.most_common():
            print("%6d  %s" % (v, k))
