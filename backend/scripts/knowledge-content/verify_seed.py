#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 원문 자료(nikl-pron-*) 검수 보조: schema.sql seed 청크를 국립국어원 원문 페이지와 독립적으로 대조한다.

- 조문 청크: 공백·쉼표를 뺀 글자열이 원문 조항(해설 전까지)과 같아야 한다(글자 추가·누락·순서 변경 없음).
- 해설 청크: 문단이 원문 줄과 정확히 같아야 하고, 분류표 항목은 원문 표의 칸 위치(행·열 머리)와 맞아야 한다.
- 저작권 정책 페이지의 이용 조건 문장이 그대로 있는지도 확인한다.
build_seed.py의 추출 코드를 쓰지 않는다(같은 버그를 함께 갖지 않게). 승인(review_status·reviewed_by)은 하지 않는다. 사람이 결과를 보고 승인한다.

사용: python3 verify_seed.py [원문HTML파일]   (생략하면 내려받는다)
"""
import html, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SCHEMA = os.path.join(HERE, "..", "..", "src", "main", "resources", "schema.sql")
SOURCE_URL = "https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002"
COPYRIGHT_URL = "https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3"
COPYRIGHT_SENTENCE = "국립국어원 누리집(홈페이지)에서 제공하는 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도의 이용허락 없이 이용이 가능합니다."


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=30).read().decode("utf-8", errors="replace")


def seed_chunks():
    s = open(SCHEMA, encoding="utf-8").read()
    block = s[s.index("-- BEGIN knowledge-seed"):s.index("-- END knowledge-seed")]
    rows = re.findall(r"^\('(nikl-pron-[a-z-]+-\d\d)','([^']*)',\d+,'([^']*)','((?:[^']|'')*)','[^']*'\)", block, flags=re.M)
    return [(cid, doc, loc, content.replace("''", "'")) for cid, doc, loc, content in rows]


raw = open(sys.argv[1], encoding="utf-8", errors="replace").read() if len(sys.argv) > 1 else get(SOURCE_URL)
policy = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", re.sub(r"<script.*?</script>|<style.*?</style>", "", get(COPYRIGHT_URL), flags=re.S))))
print("저작권 정책 이용 조건 문장:", "있음" if COPYRIGHT_SENTENCE in policy else "없음(재확인 필요)")
print("고시 번호(제2017-13호):", "있음" if "고시 제2017-13호(2017. 3. 28.)" in raw else "없음(재확인 필요)")
t=re.sub(r'<script.*?</script>|<style.*?</style>','',raw,flags=re.S)
lines=[l.strip() for l in html.unescape(re.sub(r'<[^>]+>','\n',t)).split('\n') if l.strip()]
p2=[i for i,l in enumerate(lines) if l.startswith('표준 발음법은 표준어의 실제 발음을 따르되')]
assert len(p2)==1; P=p2[0]
part2=lines[P:]
def art(k):
    for i,l in enumerate(part2):
        if l==f'제{k}항' and part2[i-1].endswith(f'>제{k}항"/>'):
            out=[]
            for x in part2[i+1:]:
                if x=='해설': return out
                out.append(x)
    raise SystemExit(f'no art {k}')
norm=lambda s: re.sub(r'[\s,]','',s)
# independent table expansion
def grid(keyword):
    i=raw.find(keyword); s=raw.rfind('<table',0,i); e=raw.find('</table>',i)
    rows=re.findall(r'<tr.*?</tr>',raw[s:e],flags=re.S); occupied={}; G=[]
    for r,tr in enumerate(rows):
        c=0; row={}
        for attrs,body in re.findall(r'<t[hd]([^>]*)>(.*?)</t[hd]>',tr,flags=re.S):
            while (r,c) in occupied: row[c]=occupied[(r,c)]; c+=1
            txt=html.unescape(re.sub(r'<[^>]+>','',body)).strip()
            rs=int((re.search(r'rowspan\s*=\s*"?(\d+)',attrs) or [0,1])[1]); cs=int((re.search(r'colspan\s*=\s*"?(\d+)',attrs) or [0,1])[1])
            for dr in range(rs):
                for dc in range(cs):
                    if dr==0: row[c+dc]=txt
                    else: occupied[(r+dr,c+dc)]=txt
            c+=cs
        while (r,c) in occupied: row[c]=occupied[(r,c)]; c+=1
        G.append([row[k] for k in sorted(row)])
    return G
CG=grid('양순음'); VG=grid('전설 모음')
places=CG[0][2:]
cells=[]  # (jamo, manner, grade, place)
for row in CG[1:]:
    for pl,cell in zip(places,row[2:]):
        if cell: cells.append((cell,row[0],row[1],pl))
vcells=[]
for row in VG[2:]:
    for f,lip,cell in zip(VG[0][1:],VG[1][1:],row[1:]):
        if cell: vcells.append((cell,f,lip,row[0]))
ok=fail=0; problems=[]
rows=seed_chunks()
for cid,doc,loc,content in rows:
    m=re.fullmatch(r'제\d장 제(\d+)항',loc)
    try:
        if m:
            assert norm(content)==norm(''.join(art(int(m.group(1))))), 'article text differs'
        else:
            para,_,table=content.partition(' [분류표] ')
            assert para in part2, 'commentary paragraph not an exact official line'
            if table:
                head,_,items=table.partition(': ')
                if doc=='nikl-pron-place':
                    want={(c,mn) for c,mn,gr,pl in cells if pl==head}
                    got=set()
                    pairs=re.findall(r'(\S)\(([^)]*)\)',items)
                    assert norm(items)==norm(''.join(f'{a}({b})' for a,b in pairs)), 'unparsed text in place row'
                    for j,lab in pairs:
                        it=f'{j}({lab})'; got.add((j,lab.split(' ')[0]))
                        assert any(c==j and pl==head and (lab==mn or lab==f'{mn} {gr}') for c,mn,gr,pl in cells), f'{it} not in column {head}'
                    assert got==want, 'place row incomplete'
                elif doc=='nikl-pron-manner':
                    want={c for c,mn,gr,pl in cells if mn==head}
                    got=set()
                    pairs=re.findall(r'(\S)\(([^)]*)\)',items)
                    assert norm(items)==norm(''.join(f'{a}({b})' for a,b in pairs)), 'unparsed text in manner row'
                    for j,lab in pairs:
                        it=f'{j}({lab})'; got.add(j)
                        assert any(c==j and mn==head and (lab==pl or lab==f'{pl}, {gr}') for c,mn,gr,pl in cells), f'{it} not in row {head}'
                    assert got==want, 'manner row incomplete'
                else:
                    f,lip,h=items.split(', ')
                    assert (head,f,lip,h) in vcells, f'vowel {head} mismatch'
        ok+=1
    except AssertionError as e:
        fail+=1; problems.append((cid,str(e)))
print(f'청크 {len(rows)}개 대조: 일치 {ok}, 불일치 {fail}'); [print('  불일치:', p) for p in problems]
sys.exit(1 if fail or len(rows) != 40 else 0)
