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

# ======== Tanda 2: animales, humanos, armas y minerales ========
def full(rows):
    for r in rows: assert len(r)==16, (r,len(r))
    return rows
# --- Animales ---
dog=mirror(["........","........","....oooo","..oobbbb",".oddobbb","oddddobb","oddddbbb","oddddbbb",".oddbbbb","..obbbbb","..obbwww","..obwwww","...owwww","....oooo","........","........"])
dog=setpx(dog,[(7,10),(8,10)],"k")
M["dog"]=dict(name="Perro", colors=dict(b="#C8915A", d="#7A4E2D", w="#F4E6D0"), eyes=((5,7),(9,7)), mouth=(6,11), sweat=(14,2), frames=[dog], anim="bob")
blackcat=[r for r in cat]; blackcatB=[r for r in catB]
M["blackcat"]=dict(name="Gato negro", colors=dict(b="#34343F", o="#15151B"), eyes=((4,6),(10,6)), mouth=(6,9), sweat=(14,4), frames=[blackcat,blackcatB], anim="cat", nose=(7,8), eye="y")
par=mirror(["........",".....ooo","....obbb","...obhbb","..obbbbb","..obbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbyy",".obbbbyy",".obbbbbb","..orrbbb","..orrrbb","...ooooo","........"])
par=setpx(par,[(8,3),(9,4)],"b"); par=setpx(par,[(7,11),(8,11)],"k")
parB=setpx(par,[(1,8),(1,9),(1,10),(14,8),(14,9),(14,10)],"r")
M["parrot"]=dict(name="Loro", colors=dict(b="#3BB273", y="#F5B700", r="#E84855"), eyes=((4,6),(10,6)), mouth=None, sweat=(14,1), frames=[par,parB], anim="hover")
ham=mirror(["........","........","..oo....",".oppoooo",".obbbbbb","obbbbbbb","obbbbbbb","obbbbbbb","obbbbwww","obbbwwww","obbwwwww",".obwwwww","..owwwww","...ooooo","........","........"])
M["hamster"]=dict(name="Hámster", colors=dict(b="#E8A15C", w="#FFF3E0"), eyes=((4,6),(10,6)), mouth=(6,10), sweat=(14,2), frames=[ham], anim="squash", nose=(7,9))
fish=full(["................","................","....oooooo......","...obbwbbbo...o.","..obhbwbbbbo.obo",".obbbbwbbbbbobbo",".obbbbwbbbbbbbbo",".obbbbwbbbbbbbbo",".obbbbwbbbbbbbbo",".obbbbwbbbbbobbo","..obbbwbbbbo.obo","...obbwbbbo...o.","....oooooo......","................","................","................"])
fishB=full(["................","................","....oooooo......","...obbwbbbo.....","..obhbwbbbbo..oo",".obbbbwbbbbbooob",".obbbbwbbbbbbbbo",".obbbbwbbbbbbbbo",".obbbbwbbbbbbbbo",".obbbbwbbbbbooob","..obbbwbbbbo..oo","...obbwbbbo.....","....oooooo......","................","................","................"])
M["fish"]=dict(name="Pez", colors=dict(b="#FF8C42"), eyes=((3,6),(8,6)), mouth=(3,9), sweat=(13,1), frames=[fish,fishB], anim="float", eye="k")
# --- Humanos (cabeza chibi + hombros) ---
face=mirror(["........","........","........","........","...ooooo","..obbbbb",".obbbbbb",".obbbbbb",".obbbbbb",".obbbbbb","..obbbbb","...oobbb",".....ooo","..ovvvvv",".ovvvvvv",".ovvvvvv"])
def hum(rows_over):
    r=list(face)
    for i,row in rows_over.items(): r[i]=row
    return full(r)
skin="#F2C9A0"
def H(key,name,over,cols,anim="bob",mouth=(6,10)):
    M[key]=dict(name=name, colors=dict(b=skin, **cols), eyes=((5,7),(9,7)), mouth=mouth, sweat=(14,5), frames=[hum(over)], anim=anim, eye="k")
hard={1:"......oooo......",2:"....oaaaaaao....",3:"...oaaaaaaaao...",4:"..oaaaaaaaaaao..",5:"oooooooooooooooo"}
H("builder","Constructor",hard,dict(a="#F5C542",v="#E07A1F"))
eng=dict(hard); eng[14]=".ovvvvwwwwvvvvo."; eng[15]=".ovvvvwwwwvvvvo."
H("engineer","Ingeniero",eng,dict(a="#F2F2F2",v="#F28C28",w="#DADADA"))
H("architect","Arquitecto",{1:"........a.......",2:"....aaaaaaaa....",3:"..aaaaaaaaaaaa..",4:".aaaaaaaaaaaaaa.",14:".ovvvvvvvvvvvvo.",15:".ovvvvvvvvvvvvo."},dict(a="#22222A",v="#3A3A48"))
H("swimmer","Nadador",{3:"...oaaaaaaaao...",4:"..oaaaaaaaaaao..",5:".oaaaaaaaaaaaao.",6:".okllkkkkkkllko.",13:"..obbbbbbbbbbo..",14:".obbbbbbbbbbbbo.",15:".obbbbbbbbbbbbo."},dict(a="#2F80ED",l="#7FE7F2",k="#1E1E24"))
H("fisher","Pescador",{2:"....oaaaaaao....",3:"...oaaaaaaaao...",4:"..oaaaaaaaaaao..",5:"oaaaaaaaaaaaaaao",14:".ovvvvvvvvvvvvo.",15:".ovvvvvvvvvvvvo."},dict(a="#5B8C4A",v="#2D5D7B"))
H("smith","Herrero",{3:"...oaaaaaaaao...",4:"..oaaaaaaaaaao..",5:".oaaaaaaaaaaaao.",11:"...okkbbbbkko...",12:"....okkkkkko....",13:"..ovvvvvvvvvvo..",14:".ovvvvvvvvvvvvo.",15:".ovvvvvvvvvvvvo."},dict(a="#C0392B",k="#4A2E1F",v="#7A5230"))
H("cowboy","Vaquero",{1:".....oaaaao.....",2:"....oaaaaaao....",3:"....oaaaaaao....",4:"oaaaaaaaaaaaaaao",5:".oooooooooooooo.",14:".ovvvvvvvvvvvvo.",15:".ovvvvvvvvvvvvo."},dict(a="#8B5A2B",v="#B03A2E"))
# --- Armas medievales (objetos, sin cara) ---
sw=mirror([".......o","......ow","......ow","......ow","......ow","......ow","......ow","......ow","......ow","...ooooo","...oyyyy","...ooooo","......ok","......ok",".....oyy","......oo"])
sw=setpx(sw,[(8,y) for y in range(1,9)],"g")
M["sword"]=dict(name="Espada", colors=dict(b="#9AA7B3", w="#E8EEF2", g="#9AA7B3", y="#F5C542", k="#6B4226", o="#2B2B33"), eyes=((5,4),(9,4)), mouth=None, sweat=(13,1), frames=[sw], anim="waddle", noface=True)
axe=full(["................",".........oo.....","...ooooookko....","..ohwwwwwkko....",".ohwwwwwwkko....",".ohwwwwwwkko....",".ohwwwwwwkko....","..ohwwwwwkko....","...ooooookko....","........okko....","........okko....","........okko....","........okko....","........okko....","........okko....","........oooo...."])
M["axe"]=dict(name="Hacha", colors=dict(b="#9AA7B3", w="#B9C4CE", h="#EEF3F7", k="#7A4A24", o="#2B2B33"), eyes=((3,4),(6,4)), mouth=None, sweat=(13,1), frames=[axe], anim="waddle", noface=True)
bow=full(["................",".....kw.........","....k.w.........","...k..w.........","...k..w.........","..k...w.........","..k...w......g..","..kryyyyyyyyyggg","..k...w......g..","..k...w.........","...k..w.........","...k..w.........","....k.w.........",".....kw.........","................","................"])
bowB=full(["................",".....k..........","....k.w.........","...k...w........","...k....w.......","..k......w......","..k......w...g..","..kr.....wyyyggg","..k......w...g..","..k......w......","...k....w.......","...k...w........","....k.w.........",".....k..........","................","................"])
M["bow"]=dict(name="Arco y flecha", colors=dict(b="#8B5A2B", k="#8B5A2B", w="#EDEDED", y="#D9B07A", g="#9AA7B3", r="#E84855"), eyes=((3,4),(6,4)), mouth=None, sweat=(13,1), frames=[bow,bowB], anim="bob", noface=True)
# --- Minerales (bloque de piedra con vetas y carita) ---
def ore(key,name,c1,c2):
    rows=[list(".oooooooooooooo.")]+[list(".obbbbbbbbbbbbo.") for _ in range(13)]+[list(".oooooooooooooo."),list("................")]
    for x,y in [(4,3),(5,4),(11,3),(12,4),(3,11),(4,12),(11,12),(12,11),(7,13),(8,2)]: rows[y][x]="x"
    for x,y in [(3,3),(12,3),(3,12),(12,12)]: rows[y][x]="X"
    for x,y in [(2,6),(13,8),(6,12),(10,2),(2,9)]: rows[y][x]="d"
    M[key]=dict(name=name, colors=dict(b="#8E8E8E", d="#6E6E6E", o="#3A3A3A", x=c1, X=c2), eyes=((5,6),(9,6)), mouth=(6,9), sweat=(13,0), frames=[["".join(r) for r in rows]], anim="bob", eye="k")
ore("gold","Mineral de oro","#F5C542","#FFF1A8")
ore("copper","Mineral de cobre","#D9773B","#5FC7A8")
ore("iron","Mineral de hierro","#D8B7A0","#F1E1D4")
ore("bronze","Mineral de bronce","#B07D3A","#D9A75F")
ore("diamond","Mineral de diamante","#4FE3E1","#C9FFFE")
ore("titanium","Mineral de titanio","#B8C4D6","#EEF3FA")
ORDER+=["dog","blackcat","parrot","hamster","fish","builder","engineer","architect","swimmer","fisher","smith","cowboy","sword","axe","bow","gold","copper","iron","bronze","diamond","titanium"]
for k in ORDER:
    for f in M[k]["frames"]:
        assert len(f)==16 and all(len(r)==16 for r in f), (k,[len(r) for r in f])
