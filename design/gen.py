import sys; sys.path.insert(0,"/root/design")
from mascots import M, ORDER, CATS
ANIMS=["bob","squash","float","cat","robot","march","hover","chill","waddle"]
out=['package com.matias.claudeusage;','','/** Generado desde el diseño de las mascotas (16x16, pixel art). */','final class MascotData {',
     '    static final String[] ANIMS = {'+",".join('"%s"'%a for a in ANIMS)+'};','    static final Object[][] ALL = {']
for k in ORDER:
    m=M[k]
    frames="new String[][]{"+",".join("{"+",".join('"%s"'%r for r in f)+"}" for f in m["frames"])+"}"
    cols=",".join("%s%s"%(a,b) for a,b in m["colors"].items())
    mouth="new int[]{%d,%d}"%m["mouth"] if m.get("mouth") else "null"
    nose="new int[]{%d,%d}"%m["nose"] if m.get("nose") else "null"
    (lx,ly),(rx,ry)=m["eyes"]; sx,sy=m["sweat"]
    flags=("n" if m.get("noface") else "")+("s" if m.get("sockets") else "")
    out.append('            {"%s", %d, %s, "%s", new int[]{%d,%d,%d,%d}, %s, new int[]{%d,%d}, "%s", %s, "%s"},'%(
        m["name"],ANIMS.index(m["anim"]),frames,cols,lx,ly,rx,ry,mouth,sx,sy,m.get("eye","o"),nose,flags))
out+=['    };']
out.append('    static final String[] CAT_NAMES = {'+",".join('"%s"'%n for n,_ in CATS)+'};')
out.append('    static final int[][] CATS = {'+",".join('{'+",".join(str(ORDER.index(k)) for k in ks)+'}' for _,ks in CATS)+'};')
out.append('    static final String[] KEYS = {'+",".join('"%s"'%k for k in ORDER)+'};')
out+=['}']
open("/root/app/src/com/matias/claudeusage/MascotData.java","w").write("\n".join(out)+"\n")
