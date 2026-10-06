#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 지식베이스 seed 생성기(국립국어원 「한국어 어문 규범」 표준 발음법 원문).

1) --fetch: 공식 페이지(SOURCE_URL)의 HTML을 받아 조문·해설 문단·분류표를 그대로 뽑아 nikl_standard_pronunciation.json에 저장한다.
   (내용을 지어내지 않는다. 원문 문단을 찾지 못하면 실패한다.)
2) 기본 실행: JSON으로 schema.sql의 생성 블록(BEGIN/END 표시 사이)을 다시 쓴다.

- 자료는 원문 그대로(예시 낱말 줄은 쉼표로 이어 붙임), 분류표는 칸 위치(행·열 머리) 그대로 옮긴다.
- 출처·사용 권한 확인 결과는 verification_status=VERIFIED와 verification_note에 남긴다.
  내용 검수(교사·운영자 승인)는 하지 않으므로 review_status=DRAFT로 넣는다. 승인 전에는 AI 검색 대상이 아니다.
- INSERT IGNORE: 이미 있는 행(검수 상태·수정 내용)은 덮어쓰지 않는다.

사용: python3 build_seed.py [--fetch [HTML파일]]
"""
import argparse, html, json, os, re, sys, urllib.request
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(HERE, "nikl_standard_pronunciation.json")
SCHEMA = os.path.join(HERE, "..", "..", "src", "main", "resources", "schema.sql")
BEGIN = "-- BEGIN knowledge-seed: scripts/knowledge-content/build_seed.py 로 다시 만든다(직접 고치지 않는다)."
END = "-- END knowledge-seed"

SOURCE_URL = "https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002"
COPYRIGHT_URL = "https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3"
SOURCE_VERSION = "문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법"
ARTICLES = [2, 3, 4, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 23]
JAMO = re.compile(r"[ㄱ-ㅎㅏ-ㅣ]")

# 해설에서 쓰는 문단(원문 문단의 시작 부분으로 찾는다)
PARAGRAPHS = {
    "place": "국어의 자음은 5개의 조음 위치에서 발음된다.",
    "manner": "국어의 자음은 소리 나는 방식에 따라서",
    "h": "후음인 ‘ㅎ’은 격음이나 평음 등으로 분류하지 않고",
    "monophthong": "단모음의 분류는 크게 혀의 위치, 입술 모양에 따라",
    "diphthong": "‘ㅑ, ㅒ, ㅕ, ㅖ, ㅛ, ㅠ’는 각각 반모음",
}


# ── 1) 원문 추출 ──────────────────────────────────────────────
def page_text(raw):
    t = re.sub(r"<script.*?</script>|<style.*?</style>", "", raw, flags=re.S)
    t = html.unescape(re.sub(r"<[^>]+>", "\n", t))
    return [line.strip() for line in t.split("\n") if line.strip()]


PART2_START = "표준 발음법은 표준어의 실제 발음을 따르되"  # 제2부 표준 발음법 제1항(제1부 표준어 사정 원칙과 항 번호가 겹친다)


def article(lines, no):
    """제2부 표준 발음법의 조문 본문(해설 전까지)의 줄 목록"""
    starts = [i for i, line in enumerate(lines) if line.startswith(PART2_START)]
    if len(starts) != 1: sys.exit("제2부 표준 발음법 시작(제1항)을 하나로 찾지 못함")
    for i in range(starts[0], len(lines)):
        line = lines[i]
        if line == f"제{no}항" and lines[i - 1].endswith(f">제{no}항\"/>"):
            out = []
            for nxt in lines[i + 1:]:
                if nxt == "해설": return out
                out.append(nxt)
    sys.exit(f"원문에서 제{no}항을 찾지 못함")


def table(raw, keyword):
    """keyword가 든 표를 rowspan/colspan을 펼친 격자로"""
    i = raw.find(keyword)
    s, e = raw.rfind("<table", 0, i), raw.find("</table>", i)
    grid, pending = [], {}
    for tr in re.findall(r"<tr.*?</tr>", raw[s:e], flags=re.S):
        row, col = [], 0
        cells = re.findall(r"<t[hd]([^>]*)>(.*?)</t[hd]>", tr, flags=re.S)
        for attrs, body in cells:
            while col in pending:
                text, left = pending[col]; row.append(text)
                if left > 1: pending[col] = (text, left - 1)
                else: del pending[col]
                col += 1
            text = html.unescape(re.sub(r"<[^>]+>", "", body)).strip()
            rs = re.search(r'rowspan\s*=\s*"?(\d+)', attrs); cs = re.search(r'colspan\s*=\s*"?(\d+)', attrs)
            for _ in range(int(cs.group(1)) if cs else 1):
                if rs and int(rs.group(1)) > 1: pending[col] = (text, int(rs.group(1)) - 1)
                row.append(text); col += 1
        while col in pending:
            text, left = pending[col]; row.append(text)
            if left > 1: pending[col] = (text, left - 1)
            else: del pending[col]
            col += 1
        grid.append(row)
    return grid


def fetch(path):
    if path:
        raw = open(path, encoding="utf-8", errors="replace").read()
    else:
        req = urllib.request.Request(SOURCE_URL, headers={"User-Agent": "Mozilla/5.0"})
        raw = urllib.request.urlopen(req, timeout=30).read().decode("utf-8", errors="replace")
    lines = page_text(raw)
    if not any(SOURCE_VERSION.split("(")[0].replace("문화체육관광부 ", "") in l for l in lines):
        sys.exit("고시 번호(제2017-13호)를 원문 페이지에서 확인하지 못함")
    paragraphs = {}
    for key, prefix in PARAGRAPHS.items():
        found = [l for l in lines if l.startswith(prefix)]
        if len(found) != 1: sys.exit(f"해설 문단을 하나로 찾지 못함: {key}")
        paragraphs[key] = found[0]
    data = {
        "sourceUrl": SOURCE_URL, "copyrightPolicyUrl": COPYRIGHT_URL, "sourceVersion": SOURCE_VERSION,
        "retrievedAt": date.today().isoformat(),
        "articles": {str(n): article(lines, n) for n in ARTICLES},
        "paragraphs": paragraphs,
        "consonantTable": table(raw, "양순음"),
        "vowelTable": table(raw, "전설 모음"),
    }
    with open(JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"저장: {JSON_PATH}")


# ── 2) seed SQL ──────────────────────────────────────────────
def is_prose(line):
    return " " in line and (line.endswith(".") or line.endswith(".)")) or line.startswith("[붙임") or line.startswith("다만")


def article_text(lines):
    """원문 줄을 한 문단으로: 문장 줄은 그대로, 예시 낱말 줄은 쉼표로 잇는다."""
    out, examples, marker = [], [], ""
    def flush():
        nonlocal examples, marker
        if examples: out.append((marker + " " if marker else "") + ", ".join(examples))
        examples, marker = [], ""
    for line in lines:
        if re.fullmatch(r"\(\d\)", line):
            flush(); marker = line
        elif is_prose(line):
            flush(); out.append(line)
        else:
            examples.append(line)
    flush()
    return " ".join(out)


def jamo_in(text):
    seen = []
    for c in JAMO.findall(text):
        if c not in seen: seen.append(c)
    return seen


def q(value):
    if value is None: return "NULL"
    if isinstance(value, int): return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


CHAPTER = {2: "제2장", 3: "제2장", 4: "제2장", 5: "제2장", 17: "제5장", 18: "제5장", 19: "제5장", 20: "제5장", 23: "제6장"}
DETECTED_RULES = {8, 9, 13, 17, 18, 19, 20, 23}  # PronunciationRuleDetector가 표기에서 찾는 조항
RULE_SLOTS = {8: ["CODA"], 9: ["CODA"], 13: ["CODA", "ONSET"], 17: ["CODA"], 18: ["CODA"], 19: ["ONSET"], 20: ["CODA", "ONSET"], 23: ["ONSET"]}


def build(data):
    verified = data["retrievedAt"]
    note = (f"{verified} 원문 페이지({data['sourceUrl']})에서 조문·해설을 그대로 추출하고 "
            f"국립국어원 저작권 정책({data['copyrightPolicyUrl']})을 확인함. 내용 검수(승인)는 별도.")
    rule_license = ("국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능"
                    f"(해당 페이지에 별도 공공누리 유형 표시 없음, {verified} 확인). 조문은 정부 고시(저작권법 제7조의 보호받지 못하는 저작물). "
                    "출처 표시: 국립국어원 「한국어 어문 규범」 표준 발음법.")
    commentary_license = ("국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능"
                          f"(해당 페이지에 별도 공공누리 유형 표시 없음, {verified} 확인). 해설은 국립국어원이 작성한 공공저작물이며 출처를 표시한다: "
                          "국립국어원 「한국어 어문 규범」 표준 발음법 해설.")
    scope_rule = "표준어를 말할 때의 표준 발음 규범(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다."
    scope_commentary = "표준 발음법 조항에 대한 국립국어원 해설(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다."
    citation = f"{data['sourceVersion']}. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문."
    docs, chunks = [], []

    def doc(doc_id, title, topic, category, commentary):
        docs.append((doc_id, title + (" 해설" if commentary else ""), citation + (" 해설 부분." if commentary else ""), data["sourceUrl"],
                     "국립국어원", None, 2017, topic, scope_commentary if commentary else scope_rule,
                     commentary_license if commentary else rule_license, category, data["sourceVersion"], verified, note))

    def chunk(doc_id, order, location, content, tags):
        if len(content) > 2000: sys.exit(f"청크가 너무 김: {doc_id}/{order}")
        chunks.append((f"{doc_id}-{order:02d}", doc_id, order, location, content, ",".join(tags)))

    arts = data["articles"]
    # 조문: 자음·모음
    doc("nikl-pron-consonant", "표준 발음법 제2장 자음과 모음(자음 조항)", "자음", "CONSONANT", False)
    chunk("nikl-pron-consonant", 2, "제2장 제2항", article_text(arts["2"]), ["slot:ONSET"] + [f"phoneme:{j}" for j in jamo_in(article_text(arts["2"]))])
    doc("nikl-pron-vowel", "표준 발음법 제2장 자음과 모음(모음 조항)", "모음", "VOWEL", False)
    for n in (3, 4, 5):
        text = article_text(arts[str(n)])
        main = arts[str(n)][1] if n == 3 else arts[str(n)][0]
        chunk("nikl-pron-vowel", n, f"제2장 제{n}항", text, ["slot:NUCLEUS"] + [f"phoneme:{j}" for j in jamo_in(text if n == 3 else main)])
    # 조문: 받침의 발음(제4장)
    doc("nikl-pron-coda", "표준 발음법 제4장 받침의 발음", "받침", "CODA", False)
    for n in (8, 9, 10, 11, 12, 13, 14, 15, 16):
        lines = arts[str(n)]
        rule = [f"rule:{n}"] if n in DETECTED_RULES else []
        slots = RULE_SLOTS.get(n, ["CODA"])
        chunk("nikl-pron-coda", n, f"제4장 제{n}항", article_text(lines), rule + [f"slot:{s}" for s in slots] + [f"phoneme:{j}" for j in jamo_in(lines[0])])
    # 조문: 음의 동화·경음화
    doc("nikl-pron-assimilation", "표준 발음법 제5장 음의 동화·제6장 경음화(일부 조항)", "표준 발음·음운 규칙", "PRONUNCIATION_RULE", False)
    for n in (17, 18, 19, 20, 23):
        lines = arts[str(n)]
        chunk("nikl-pron-assimilation", n, f"{CHAPTER[n]} 제{n}항", article_text(lines),
              [f"rule:{n}"] + [f"slot:{s}" for s in RULE_SLOTS[n]] + [f"phoneme:{j}" for j in jamo_in(lines[0])])

    # 해설: 자음 분류표(조음 위치·방법). 격자: [방법, 계열, 양순음, 치조음, 경구개음, 연구개음, 후음]
    grid = data["consonantTable"]
    places = grid[0][2:]
    by_place = {p: [] for p in places}
    by_manner = {}
    for row in grid[1:]:
        manner, grade = row[0], row[1]
        label = manner if grade == manner else f"{manner} {grade}"
        for place, cell in zip(places, row[2:]):
            if not cell: continue
            if cell not in [c for c, _ in by_place[place]]: by_place[place].append((cell, manner if place == "후음" else label))
            by_manner.setdefault(manner, [])
            if cell not in [c for c, _ in by_manner[manner]]: by_manner[manner].append((cell, place if (place == "후음" or grade == manner) else f"{place}, {grade}"))
    para = data["paragraphs"]
    doc("nikl-pron-place", "표준 발음법 제2항(자음의 조음 위치)", "자음의 조음 위치", "ARTICULATION_PLACE", True)
    for i, place in enumerate(places, start=1):
        row = ", ".join(f"{c}({label})" for c, label in by_place[place])
        chunk("nikl-pron-place", i, "제2장 제2항 해설(자음 분류표)", f"{para['place']} [분류표] {place}: {row}",
              [f"phoneme:{c}" for c, _ in by_place[place]])
    doc("nikl-pron-manner", "표준 발음법 제2항(자음의 조음 방법)", "자음의 조음 방법", "ARTICULATION_MANNER", True)
    for i, (manner, cells) in enumerate(by_manner.items(), start=1):
        row = ", ".join(f"{c}({label})" for c, label in cells)
        chunk("nikl-pron-manner", i, "제2장 제2항 해설(자음 분류표)", f"{para['manner']} [분류표] {manner}: {row}",
              [f"phoneme:{c}" for c, _ in cells])
    chunk("nikl-pron-manner", len(by_manner) + 1, "제2장 제2항 해설", para["h"], ["phoneme:ㅎ"])
    # 해설: 단모음 분류표. 격자: [높이, 전설 평순, 전설 원순, 후설 평순, 후설 원순]
    vgrid = data["vowelTable"]
    fronts, rounds = vgrid[0][1:], vgrid[1][1:]
    doc("nikl-pron-vowel-guide", "표준 발음법 제4항·제5항(모음의 분류)", "모음의 분류", "VOWEL", True)
    order = 1
    for row in vgrid[2:]:
        for front, lip, cell in zip(fronts, rounds, row[1:]):
            if not cell: continue
            chunk("nikl-pron-vowel-guide", order, "제2장 제4항 해설(단모음 분류표)",
                  f"{para['monophthong']} [분류표] {cell}: {front}, {lip}, {row[0]}", [f"phoneme:{cell}"])
            order += 1
    chunk("nikl-pron-vowel-guide", order, "제2장 제5항 해설", para["diphthong"],
          [f"phoneme:{j}" for j in "ㅑ ㅒ ㅕ ㅖ ㅘ ㅙ ㅛ ㅝ ㅞ ㅠ ㅢ".split()])

    out = [BEGIN,
           f"-- 출처: {data['sourceUrl']} ({data['retrievedAt']} 추출). 원문 조문·해설을 그대로 옮겼다.",
           "-- 출처·사용 권한 확인 완료(VERIFIED), 내용 검수 전(DRAFT): 교사·운영자가 승인(review_status=APPROVED, reviewed_by/at)해야 AI 검색 대상이 된다.",
           "INSERT IGNORE INTO knowledge_documents(document_id,title,source_citation,source_url,publisher,author,published_year,topic,scope,license_note,category,source_version,verification_status,verified_at,verification_note,review_status,version) VALUES"]
    out.append(",\n".join("(" + ",".join(q(v) for v in d[:11]) + f",{q(d[11])},'VERIFIED',{q(d[12] + ' 00:00:00')},{q(d[13])},'DRAFT',1)" for d in docs) + ";")
    out.append("INSERT IGNORE INTO knowledge_chunks(chunk_id,document_id,chunk_order,location,content,tags) VALUES")
    out.append(",\n".join("(" + ",".join(q(v) for v in c) + ")" for c in chunks) + ";")
    out.append(END)
    return "\n".join(out), len(docs), len(chunks)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", nargs="?", const="", default=None, help="원문 HTML을 다시 추출(파일 경로를 주면 그 파일, 없으면 내려받기)")
    args = parser.parse_args()
    if args.fetch is not None: fetch(args.fetch or None)
    with open(JSON_PATH, encoding="utf-8") as f: data = json.load(f)
    block, n_docs, n_chunks = build(data)
    schema = open(SCHEMA, encoding="utf-8").read()
    if BEGIN in schema:
        s, e = schema.index(BEGIN), schema.index(END) + len(END)
        schema = schema[:s] + block + schema[e:]
    else:
        sys.exit("schema.sql에 생성 블록 표시(BEGIN/END)가 없음")
    with open(SCHEMA, "w", encoding="utf-8") as f: f.write(schema)
    print(f"schema.sql 갱신: 문서 {n_docs}개, 청크 {n_chunks}개")


if __name__ == "__main__":
    main()
