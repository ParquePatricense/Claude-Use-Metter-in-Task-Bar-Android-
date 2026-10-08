# Diseño de mascotas 16x16. Mitad izquierda (8 cols) + espejo, con overrides.
# Colores: o contorno, b principal, h brillo, d sombra, s secundario, S sombra secundaria,
# w blanco, k negro, r rojo, y amarillo, p rosa, g gris, c cian
def mirror(rows):
    out=[]
    for r in rows:
        assert len(r)==8, r
        out.append(r+r[::-1])
    return out
def setpx(rows,pts,ch):
    rows=[list(r) for r in rows]
    for x,y in pts: rows[y][x]=ch
    return ["".join(r) for r in rows]

M={}
# 0 Blob (original)
M["blob"]=dict(name="Blob", colors=dict(b="#D97757"), eyes=((4,6),(10,6)), mouth=(6,9), sweat=(14,1),
 frames=[["................",".....oooooo.....","...oobbbbbboo...","..obhhbbbbbbbo..","..obhbbbbbbbbo..",".obbbbbbbbbbbbo.",".obbbbbbbbbbbbo.",".obbbbbbbbbbbbo.",".obbbbbbbbbbbbo.",".obbbbbbbbbbbbo.",".obbbbbbbbbbbbo.","..obbbbbbbbbbo..","..obbbbbbbbbbo..","...oobbbbbboo...","....oo....oo....","................"]], anim="bob")
# 1 Slime
slime=mirror(["........",".......o","......ob",".....obh","....obhb","...obbbb","..obbbbb","..obbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".odbbbbb","..oddddd","...ooooo","........"])
slime=setpx(slime,[(8,2),(9,3),(9,4),(10,5)],"b")  # quitar brillo espejado
slime=setpx(slime,[(6,3),(5,4),(5,5),(4,6)],"h")
M["slime"]=dict(name="Slime", colors=dict(b="#4FA3E8"), eyes=((5,8),(9,8)), mouth=(6,11), sweat=(13,3), frames=[slime], anim="squash")
# 2 Fantasma
g=mirror(["........",".....ooo","...oobbb","..obbbbb","..obbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb","........","........"])
gA=g[:]; gA[14]=".obbo.obbo.obbo."; gA[15]="..oo...oo...oo.."
gB=g[:]; gB[14]=".obbbo.oo.obbbo."; gB[15]="..ooo......ooo.."
gA=setpx(gA,[(3,4),(3,5),(4,3)],"h"); gB=setpx(gB,[(3,4),(3,5),(4,3)],"h")
M["ghost"]=dict(name="Fantasma", colors=dict(b="#E9E4FF", o="#3B3366"), eyes=((4,7),(10,7)), mouth=(6,10), sweat=(14,2), frames=[gA,gB], anim="float", eye="k")
# 3 Gato
cat=mirror(["........","..o.....",".obo....",".obbo...",".obbbooo",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb","..obbbbb","...oobbb",".....ooo","........","........","........"])
cat=setpx(cat,[(2,2),(13,2)],"p"); cat=setpx(cat,[(2,3),(13,3)],"p")
cat=setpx(cat,[(0,8),(1,9),(15,8),(14,9)],"o")  # bigotes
catB=setpx(cat,[(2,1),(2,2),(1,2),(3,2)],".")
catB=setpx(catB,[(1,3),(2,3),(3,3)],"o"); catB=setpx(catB,[(1,4)],"o")
M["cat"]=dict(name="Gato", colors=dict(b="#F2A65A"), eyes=((4,6),(10,6)), mouth=(6,9), sweat=(14,4), frames=[cat,catB], anim="cat", nose=(7,8))
# 4 Robot
rb=mirror(["........",".......o",".......o",".......o","...ooooo","..obbbbb","..obkkkk","..obkkkk","..obkkkk","..obbbbb","..obbbbb","..obbbbb","..obbbbb","...ooooo","....obbb","....oooo"])
rb=setpx(rb,[(7,0),(8,0)],"r"); rb=setpx(rb,[(3,5),(3,6)],"h")
rbB=setpx(rb,[(7,0),(8,0)],"y")
M["robot"]=dict(name="Robot", colors=dict(b="#9AA7B8"), eyes=((5,7),(9,7)), mouth=(6,10), sweat=(14,4), frames=[rb,rbB], anim="robot", eye="c")
# 5 Invasor
inv=mirror(["..b.....","...b....","..bbbbbb",".bb.bbbb","bbbbbbbb","b.bbbbbb","b.b.....","...bb...","........","........","........","........","........","........","........","........"])
invA=["................"]*4+inv[:12]
invB=invA[:]; invB=list(invB)
invB[11]="b.b........b.b".center(16,".") if False else invB[11]
invB=setpx(invA,[(0,9),(0,10),(15,9),(15,10),(2,10),(13,10),(3,11),(4,11),(11,11),(12,11)],".")
invB=setpx(invB,[(0,6),(0,7),(15,6),(15,7),(1,11),(14,11),(2,11),(13,11)],"b")
M["invader"]=dict(name="Invasor", colors=dict(b="#7CF57C"), eyes=((3,7),(12,7)), mouth=None, sweat=(14,3), frames=[invA,invB], anim="march", eye="k", noface=True)
# 6 Dragoncito
dr=mirror(["........","...o....","..obo...","..obbooo","..obbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb","..obbbbb","..obssss","...ossss","....oooo","....o..o","........"])
dr=setpx(dr,[(3,1),(12,1)],"y"); dr=setpx(dr,[(3,2),(12,2)],"y")
drA=setpx(dr,[(0,6),(0,7),(1,5),(15,6),(15,7),(14,5)],"s")
drB=setpx(dr,[(0,9),(0,10),(1,11),(15,9),(15,10),(14,11)],"s")
M["dragon"]=dict(name="Dragoncito", colors=dict(b="#59C28C", s="#F6D186"), eyes=((4,6),(10,6)), mouth=(6,9), sweat=(14,3), frames=[drA,drB], anim="hover")
# 7 Ninja
nj=mirror(["........",".....ooo","...ookkk","..okkkkk","..okkkkk",".okkkkkk",".orrrrrr",".okkkkkk",".okkwwww",".okkwwww",".okkkkkk",".okkkkkk","..okkkkk","...ookkk",".....ooo","........"])
njA=setpx(nj,[(15,5),(15,6),(14,7)],"r")
njB=setpx(nj,[(15,6),(15,7),(15,8)],"r")
M["ninja"]=dict(name="Ninja", colors=dict(k="#2E2E3A", o="#111118"), eyes=((5,8),(9,8)), mouth=None, sweat=(13,2), frames=[njA,njB], anim="bob", eye="k")
# 8 Hongo
hg=mirror(["........",".....ooo","...oorrr","..orrrrw",".orrwwrr",".orrwwrr","orrrrrrr","orrrrrrw","oooooooo","..owwwww","..owwwww","..owwwww","..owwwww","...ooooo","........","........"])
M["mush"]=dict(name="Hongo", colors=dict(r="#E5484D"), eyes=((5,9),(9,9)), mouth=(6,11), sweat=(14,1), frames=[hg], anim="squash", eye="k")
# 9 Capibara
cp=mirror(["........","..oo....",".obbo...",".obbbooo",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb","..obbbbb","..obbddd","..obdddd","..obbbbb","...ooooo","........","........"])
cp=setpx(cp,[(6,0),(7,0),(8,0),(9,0)],"y"); cp=setpx(cp,[(7,1),(8,1)],"y")
M["capy"]=dict(name="Capibara", colors=dict(b="#B07A4F", d="#8A5A38", y="#F5A524"), eyes=((4,6),(10,6)), mouth=(6,11), sweat=(14,3), frames=[cp], anim="chill", nose=(6,10))
# 10 Pingüino
pg=mirror(["........",".....ooo","...ookkk","..okkkkk","..okkwww",".okkwwww",".okkwwww",".okkwwww",".okkwwww",".okkwwww",".okkwwww","..okkwww","..okkkww","...ookkk","...oyyoo","........"])
pg=setpx(pg,[(7,9),(8,9)],"y")
M["penguin"]=dict(name="Pingüino", colors=dict(k="#2B3A55", o="#121826"), eyes=((5,6),(9,6)), mouth=None, sweat=(14,2), frames=[pg], anim="waddle", eye="k")
# 11 Calavera
sk=mirror(["........",".....ooo","...oowww","..owwwww","..owwwww",".owwwwww",".owwwwww",".owwwwww",".owwwwww","..owwwww","...owwww","...owowo","...owwww","....oooo","........","........"])
skB=sk[:]; skB=setpx(sk,[(4,12),(5,12),(6,12),(9,12),(10,12),(11,12)],"."); skB=setpx(skB,[(4,13),(11,13)],"o")
M["skull"]=dict(name="Calavera", colors=dict(w="#F2EFE6", o="#3A3530"), eyes=((4,6),(10,6)), mouth=None, sweat=(14,2), frames=[sk,skB], anim="float", eye="k", sockets=True)
ORDER=["blob","slime","ghost","cat","robot","invader","dragon","ninja","mush","capy","penguin","skull"]
for k in ORDER:
    for f in M[k]["frames"]:
        assert len(f)==16 and all(len(r)==16 for r in f), (k,[len(r) for r in f])
