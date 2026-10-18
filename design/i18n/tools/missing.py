import re,glob,os
d=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T={}
def add(es,en,it,fr,de,ja,zh): T[es]=(en,it,fr,de,ja,zh)
for f in sorted(glob.glob(d+'/t*.py')):
    src=open(f,encoding='utf-8').read().replace('T = {}','')
    src=re.sub(r'^def add.*$','',src,flags=re.M)
    exec(src,{'add':add,'T':T})
found=[]
for f in sorted(glob.glob(os.path.join(d,'..','..','src','com','matias','claudeusage','*.java'))):
    if f.endswith('L.java'): continue
    for m in re.finditer(r'L\.t\("((?:[^"\\]|\\.)*)"\)', open(f,encoding='utf-8').read()):
        k=m.group(1)
        if k not in T and k not in found: found.append(k)
for k in found: print(k)
