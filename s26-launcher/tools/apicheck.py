#!/usr/bin/env python3
"""Verifica que cada método java.*/android.* invocado desde el DEX exista en android.jar (API 36).
Uso: apicheck.py classes.dex android.jar   — sale con código 1 si falta alguno."""
import re, subprocess, sys
dex, jar = sys.argv[1], sys.argv[2]
dump = subprocess.run(['dexdump', '-d', dex], capture_output=True, text=True).stdout
refs = sorted(set(re.findall(r'invoke-[a-z/-]+ \{[^}]*\}, (L(?:java|android|javax|org/json)/[^;]+;\.[^:]+:\([^)]*\)\S+)', dump)))
refs += [l.strip() for l in sys.stdin] if not sys.stdin.isatty() else []
db, sup = {}, {}
def load(c):
    if c in db: return
    out = subprocess.run(['javap', '-s', '-p', '-cp', jar, c.replace('/', '.')], capture_output=True, text=True).stdout
    lines = out.split('\n'); head = next((l for l in lines if re.search(r'\b(class|interface)\b', l)), '')
    while '<' in head: head = re.sub(r'<[^<>]*>', '', head)
    parents = []
    for kw in ('extends', 'implements'):
        m = re.search(kw + r' ([\w.$, ]+?)(?: implements| \{|$)', head)
        if m: parents += [x.strip().replace('.', '/') for x in m.group(1).split(',')]
    sup[c] = parents
    ms = set()
    for i, l in enumerate(lines[:-1]):
        if 'descriptor:' not in lines[i + 1]: continue
        m = re.search(r'([\w$]+)\(', l)
        if not m: continue
        name = '<init>' if m.group(1) == c.split('/')[-1].split('$')[-1] or (c.replace('/', '.') + '(') in l else m.group(1)
        ms.add(name + ':' + lines[i + 1].split('descriptor:')[1].strip())
    db[c] = ms if out.strip() else None
def has(c, sig, seen):
    if c in seen: return False
    seen.add(c); load(c)
    if db.get(c) is None: return False
    if sig in db[c]: return True
    return any(has(p, sig, seen) for p in sup.get(c, []) + ['java/lang/Object'])
missing = []
for r in refs:
    cls, rest = r.split(';.', 1)
    if not has(cls[1:], rest, set()): missing.append(r)
print(f"{len(refs)} llamadas a la plataforma, {len(missing)} inexistentes en {jar.split('/')[-1]}")
for m in missing: print("  ✗", m)
sys.exit(1 if missing else 0)
