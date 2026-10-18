import sys,glob,os
d=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T={}
def add(es,en,it,fr,de,ja,zh): T[es]=(en,it,fr,de,ja,zh)
for f in sorted(glob.glob(d+'/t*.py')):
    src=open(f,encoding='utf-8').read()
    g={'add':add,'T':T}
    exec(compile(src.replace('T = {}','').replace('def add(es, en, it, fr, de, ja, zh): T[es] = (en, it, fr, de, ja, zh)',''),f,'exec'),g)
langs=['en','it','fr','de','ja','zh']
head=open(sys.argv[1],encoding='utf-8').read().split('    private static Map<String, String> en() {')[0]
out=[head.rstrip('\n')+'\n']
for i,l in enumerate(langs):
    out.append('\n    private static Map<String, String> %s() {\n        Map<String, String> m = new HashMap<String, String>();\n'%l)
    for k,v in T.items():
        out.append('        m.put("%s", "%s");\n'%(k,v[i]))
    out.append('        return m;\n    }\n')
out.append('}\n')
open(sys.argv[2],'w',encoding='utf-8').write(''.join(out))
