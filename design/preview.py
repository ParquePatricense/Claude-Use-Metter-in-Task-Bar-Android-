import sys; sys.path.insert(0,"/root/design")
from mascots import M, ORDER
from PIL import Image
def hx(s): s=s.lstrip("#"); return tuple(int(s[i:i+2],16) for i in (0,2,4))
def mix(a,b,k): return tuple(int(a[i]+(b[i]-a[i])*k) for i in range(3))
BASE=dict(w="#FFFFFF",k="#1E1E24",r="#E5484D",y="#F5C542",p="#F29CA3",g="#8A8A8A",c="#5FE3F0",s="#F6D186")
def colors(m):
    c={k:hx(v) for k,v in BASE.items()}
    c.update({k:hx(v) for k,v in m["colors"].items()})
    if "b" not in m["colors"]:
        prim=c[list(m["colors"].keys())[0]]; c["b"]=prim
    b=c["b"]
    c.setdefault("o",mix(b,(0,0,0),0.6)) if "o" not in m["colors"] else None
    if "o" not in m["colors"]: c["o"]=mix(b,(0,0,0),0.6)
    if "h" not in m["colors"]: c["h"]=mix(b,(255,255,255),0.45)
    if "d" not in m["colors"]: c["d"]=mix(b,(0,0,0),0.2)
    c["S"]=mix(c["s"],(0,0,0),0.25)
    return c
EYES={"open":[(0,0),(1,0),(0,1),(1,1)],"line":[(0,1),(1,1)],"x":[(-1,-1),(1,-1),(0,0),(-1,1),(1,1)],"happy":[(-1,1),(0,0),(1,1)]}
MOUTH={0:[(0,0),(3,0),(1,1),(2,1)],1:[(0,0),(3,0),(1,1),(2,1)],2:[(0,1),(1,1),(2,1),(3,1)],3:[(0,1),(1,0),(2,1),(3,0)],4:[(1,0),(2,0),(1,1),(2,1)],5:[(1,1),(2,1)]}
def render(key,mood,frame,blink=False):
    m=M[key]; c=colors(m); f=m["frames"][frame%len(m["frames"])]
    im=Image.new("RGBA",(16,16),(0,0,0,0))
    for y,row in enumerate(f):
        for x,ch in enumerate(row):
            if ch!=".": im.putpixel((x,y),c.get(ch,(255,0,255))+(255,))
    ec=c[m.get("eye","o")]
    if m.get("sockets"): ec=c["o"]
    kind="line" if blink or mood in (3,5) else "x" if mood==4 else "happy" if mood==0 else "open"
    if m.get("sockets") and kind=="open":
        pts=[(-1,0),(0,0),(1,0),(-1,1),(0,1),(1,1)]
    else: pts=EYES[kind]
    for ex,ey in m["eyes"]:
        for dx,dy in pts: im.putpixel((ex+dx,ey+dy),ec+(255,))
    if m.get("mouth"):
        mx,my=m["mouth"]
        for dx,dy in MOUTH[mood]: im.putpixel((mx+dx,my+dy),c["o"]+(255,))
    if m.get("nose"): im.putpixel(m["nose"],c["p"]+(255,)); 
    if mood<=1 and not m.get("noface"):
        (lx,ly),(rx,ry)=m["eyes"]
        for p in [(lx-1,ly+3),(rx+2,ly+3)]:
            if 0<=p[0]<16: im.putpixel(p,c["p"]+(255,))
    if mood in (3,4):
        sx,sy=m["sweat"]
        for dx,dy in [(0,0),(0,1),(-1,2),(0,2),(1,2),(0,3)]:
            if 0<=sx+dx<16 and 0<=sy+dy<16: im.putpixel((sx+dx,sy+dy),(0x7E,0xC8,0xF2,255))
    return im
cell=20; moods=[0,1,2,3,4,5]
sheet=Image.new("RGB",(cell*(len(moods)+2),cell*len(ORDER)),(31,30,29))
for i,k in enumerate(ORDER):
    for j,md in enumerate(moods):
        sheet.paste(render(k,md,0),(j*cell+2,i*cell+2),render(k,md,0))
    sheet.paste(render(k,1,1),(6*cell+2,i*cell+2),render(k,1,1))
    sheet.paste(render(k,1,0,True),(7*cell+2,i*cell+2),render(k,1,0,True))
sheet.resize((sheet.width*6,sheet.height*6),Image.NEAREST).save("/tmp/claude-0/prev/mascotas.png")
