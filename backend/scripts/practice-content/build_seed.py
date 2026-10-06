#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""연습 콘텐츠 seed 생성기.

content_basic.py·content_rules.py(직접 작성한 낱말·문장)를 검증해 backend/src/main/resources/data.sql을 만든다.
- 각 항목이 분류한 발음(목표 자모·표준 발음법 조항)을 표기에서 실제로 포함하는지 확인하고, 아니면 제외한다.
- 같은 문장의 중복을 없앤다. 5개씩 하나의 연습 세트(exercises)로 묶는다.
- INSERT IGNORE + 고정 ID(1001~)라서 여러 번 실행해도 안전하고, 교사·운영자가 바꾼 값을 덮어쓰지 않는다.

사용: python3 build_seed.py [--limit N]   (N: 넣을 항목 수, 단계적 확장용. 생략하면 전체)
규칙 판별은 표기 기반 근사이며(형태소 분석 없음) 학생 발음을 판정하지 않는다.
"""
import argparse, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from content_basic import CONSONANT, VOWEL, CODA
from content_rules import RULES, COMPREHENSIVE, VOWEL_EXTRA

ONSETS = "ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ".split()
NUCLEI = "ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ".split()
CODAS = [""] + "ㄱ ㄲ ㄳ ㄴ ㄵ ㄶ ㄷ ㄹ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅁ ㅂ ㅄ ㅅ ㅆ ㅇ ㅈ ㅊ ㅋ ㅌ ㅍ ㅎ".split()

def syllables(text):
    """어절별 음절 [(어절번호, 초성, 중성, 종성)]"""
    out = []
    for w, word in enumerate(text.split()):
        for ch in word:
            c = ord(ch)
            if 0xAC00 <= c <= 0xD7A3:
                i = c - 0xAC00
                out.append((w, ONSETS[i // 588], NUCLEI[(i % 588) // 28], CODAS[i % 28]))
    return out

OBSTRUENT = set("ㄱ ㄲ ㅋ ㄳ ㄺ ㄷ ㅅ ㅆ ㅈ ㅊ ㅌ ㅂ ㅍ ㄼ ㄿ ㅄ".split())
TENSE_ONSET = set("ㄱ ㄷ ㅂ ㅅ ㅈ".split())

def detect(text):
    """표기로 판별한 표준 발음법 조항 집합(백엔드 PronunciationRuleDetector와 같은 규칙 + 제12항 격음화)"""
    rules, s = set(), syllables(text)
    for i, (w, on, nu, co) in enumerate(s):
        if not co: continue
        nxt = s[i + 1] if i + 1 < len(s) and s[i + 1][0] == w else None
        if nxt is None or nxt[1] != 'ㅇ':
            if co in set("ㄲ ㅋ ㅅ ㅆ ㅈ ㅊ ㅌ ㅍ".split()) and not (nxt and nxt[1] == 'ㅎ'): rules.add(9)
        if nxt is None: continue
        o, n = nxt[1], nxt[2]
        if (co == 'ㄴ' and o == 'ㄹ') or (co == 'ㄹ' and o == 'ㄴ'): rules.add(20)
        if co in ('ㅁ', 'ㅇ') and o == 'ㄹ': rules.add(19)
        if co in OBSTRUENT and o in ('ㄴ', 'ㅁ'): rules.add(18)
        if co in OBSTRUENT and o in TENSE_ONSET: rules.add(23)
        if co in ('ㄷ', 'ㅌ') and o == 'ㅇ' and n == 'ㅣ': rules.add(17)
        elif o == 'ㅇ' and co not in ('ㅇ', 'ㅎ'): rules.add(13)
        if (co in ('ㅎ', 'ㄶ', 'ㅀ') and o in ('ㄱ', 'ㄷ', 'ㅈ')) or (co in set("ㄱ ㄺ ㄷ ㅅ ㅈ ㅊ ㅂ ㄼ ㄵ".split()) and o == 'ㅎ'): rules.add(12)
    return rules

RULE_OF = {'LIAISON': {13}, 'NASALIZATION': {18, 19}, 'TENSIFICATION': {23}, 'PALATALIZATION': {17},
           'ASPIRATION': {12}, 'CONSONANT_ASSIMILATION': {20}}
RULE_LABEL = {'BASIC_CONSONANT': '기본 자음', 'BASIC_VOWEL': '기본 모음', 'CODA': '받침', 'LIAISON': '연음', 'NASALIZATION': '비음화',
              'TENSIFICATION': '된소리', 'PALATALIZATION': '구개음화', 'ASPIRATION': '격음화', 'CONSONANT_ASSIMILATION': '유음화(자음동화)',
              'COMPREHENSIVE': '종합 발음'}
TYPE_LABEL = {'WORD': '낱말', 'SHORT_SENTENCE': '짧은 문장', 'LONG_SENTENCE': '긴 문장'}
LEVEL_LABEL = {'BEGINNER': '초급', 'INTERMEDIATE': '중급', 'ADVANCED': '고급'}

def kind(text):
    if not any(p in text for p in '.?!') and len(text.split()) <= 2: return 'WORD'
    return 'LONG_SENTENCE' if len(text.split()) >= 6 else 'SHORT_SENTENCE'

def level(rule, ctype):
    if ctype == 'LONG_SENTENCE': return 'ADVANCED'
    if ctype == 'SHORT_SENTENCE': return 'INTERMEDIATE'
    return 'BEGINNER' if rule in ('BASIC_CONSONANT', 'BASIC_VOWEL', 'CODA') else 'INTERMEDIATE'

def valid(rule, target, text):
    s = syllables(text)
    if rule == 'BASIC_CONSONANT': return any(on == target for _, on, _, _ in s)
    if rule == 'BASIC_VOWEL': return any(nu == target for _, _, nu, _ in s)
    if rule == 'CODA': return any(co == target for _, _, _, co in s)
    if rule == 'COMPREHENSIVE': return len(detect(text)) >= 2
    return bool(detect(text) & RULE_OF[rule])

def candidates():
    """(규칙, 목표 자모, 텍스트) — 작성 순서 그대로"""
    for t, (words, sents) in CONSONANT.items():
        for x in words + sents: yield 'BASIC_CONSONANT', t, x
    for t, (words, sents) in VOWEL.items():
        for x in words + sents + VOWEL_EXTRA.get(t, []): yield 'BASIC_VOWEL', t, x
    for t, (words, sents, longs) in CODA.items():
        for x in words + sents + longs: yield 'CODA', t, x
    for r, (words, sents, longs) in RULES.items():
        for x in words + sents + longs: yield r, None, x
    for x in COMPREHENSIVE: yield 'COMPREHENSIVE', None, x

def build(limit=None):
    groups, seen, rejected = {}, set(), []
    for rule, target, text in candidates():
        key = (rule, target, text)
        if key in seen: continue
        seen.add(key)
        if not valid(rule, target, text): rejected.append((rule, target, text)); continue
        ctype = kind(text)
        groups.setdefault((rule, target, ctype), []).append(text)
    # 단계적 확장: 작성 순서대로 세트 단위로 자른다(앞 단계에 넣은 세트는 다음 단계에서도 같은 ID).
    exercises, total = [], 0
    for (rule, target, ctype), texts in groups.items():
        for n, start in enumerate(range(0, len(texts), 5), 1):
            exercises.append((rule, target, ctype, n, texts[start:start + 5]))
    if limit is not None:
        picked, count = [], 0
        for ex in exercises:
            if count >= limit: break
            picked.append(ex); count += len(ex[4])
        exercises = picked
    total = sum(len(ex[4]) for ex in exercises)
    return exercises, total, rejected

def sql_text(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"

def write_sql(exercises, path):
    lines = ["-- 자동 생성 파일: backend/scripts/practice-content/build_seed.py 로 다시 만든다(직접 고치지 않는다).",
             "-- 연습 콘텐츠(발음 연습 세트). INSERT IGNORE: 이미 있는 행(교사·운영자가 바꾼 값 포함)은 덮어쓰지 않는다.",
             "-- 발음 분류는 표기 기반 근사이며 학생의 실제 발음을 평가하지 않는다."]
    ex_rows, item_rows = [], []
    for idx, (rule, target, ctype, n, texts) in enumerate(exercises):
        eid = 1001 + idx
        name = (f"{target} " if target else "") + RULE_LABEL[rule] + f" {TYPE_LABEL[ctype]} {n}"
        instruction = {'WORD': '낱말을 또박또박 따라 말해 보세요.', 'SHORT_SENTENCE': '문장을 천천히 소리 내어 읽어 보세요.',
                       'LONG_SENTENCE': '긴 문장을 끊지 않고 끝까지 읽어 보세요.'}[ctype]
        tp = target if rule in ('BASIC_CONSONANT', 'CODA') else None
        ex_rows.append(f"({eid},'articulation',{sql_text(name)},{sql_text(instruction)},'mic',{'NULL' if tp is None else sql_text(tp)},{100 + idx},"
                       f"{sql_text(level(rule, ctype))},{sql_text(ctype)},{sql_text(rule)})")
        for order, text in enumerate(texts, 1):
            item_rows.append(f"({eid},{sql_text(text)},{order})")
    for i in range(0, len(ex_rows), 200):
        lines.append("INSERT IGNORE INTO exercises (exercise_id,category_id,title,instruction,input_type,target_phonemes,sort_order,difficulty,content_type,pronunciation_rule) VALUES\n"
                     + ",\n".join(ex_rows[i:i + 200]) + ";")
    for i in range(0, len(item_rows), 500):
        lines.append("INSERT IGNORE INTO exercise_items (exercise_id,text_value,sort_order) VALUES\n" + ",\n".join(item_rows[i:i + 500]) + ";")
    with open(path, 'w', encoding='utf-8') as f: f.write("\n".join(lines) + "\n")

# ── 발음 외 영역(어휘력·유창성·표현력·이해력)과 발음 추가 세트 ─────────────────────────────
# 영역마다 고정 ID 구간을 쓴다. 기존 발음 세트(1001~)와 겹치지 않으며, 각 목록은 뒤에만 추가한다.
EXTRA_ID_BASE = {'articulation': 2001, 'vocabulary': 3001, 'fluency': 4001, 'expression': 5001, 'comprehension': 6001}
FORBIDDEN = ('문제 1', '문제 2', '연습 문제', 'lorem', 'Lorem', 'placeholder', 'TODO', 'XXX')


def rows(data):
    return [line.split('|') for line in data.strip().splitlines() if line.strip()]


def extra_exercises():
    """[{category, title, instruction, difficulty, content_type, rule, target, items:[(text, emoji)]}] (영역별 작성 순서)"""
    from content_syllables import consonant_syllable_sets, vowel_syllable_sets, MINIMAL_PAIRS
    from content_vocabulary import THEMES
    from content_fluency import TYPES as FLUENCY
    from content_expression import TYPES as EXPRESSION
    from content_comprehension import TYPES as COMPREHENSION
    out = []
    for target, name, items in consonant_syllable_sets():
        out.append(dict(category='articulation', title=f"{target} {name} · {' '.join(items)}", instruction='음절을 하나씩 또박또박 따라 말해 보세요.',
                        difficulty='BEGINNER', content_type='WORD', rule='BASIC_CONSONANT', target=target, items=[(x, None) for x in items]))
    for target, name, items in vowel_syllable_sets():
        out.append(dict(category='articulation', title=f"{target} {name} · {' '.join(items)}", instruction='입 모양에 집중하며 음절을 하나씩 따라 말해 보세요.',
                        difficulty='BEGINNER', content_type='WORD', rule='BASIC_VOWEL', target=target, items=[(x, None) for x in items]))
    for target, name, words in MINIMAL_PAIRS:
        out.append(dict(category='articulation', title=f"{name} · {' '.join(words)}", instruction='비슷한 소리를 구별하며 낱말을 또박또박 말해 보세요.',
                        difficulty='INTERMEDIATE', content_type='WORD', rule='BASIC_CONSONANT', target=target, items=[(x, None) for x in words]))
    for theme, difficulty, data in THEMES:
        for word, emoji, meaning, example in rows(data):
            out.append(dict(category='vocabulary', title=f"{theme} · {word}", instruction=f"뜻: {meaning}\n낱말을 말한 뒤 예문을 읽어 보세요.",
                            difficulty=difficulty, content_type='WORD', rule=None, target=None, items=[(word, emoji or None), (example, None)]))
    for kind_name, instruction, difficulty, data in FLUENCY:
        for title, *sentences in rows(data):
            out.append(dict(category='fluency', title=f"{kind_name} · {title}", instruction=instruction, difficulty=difficulty,
                            content_type=sentence_type(sentences), rule=None, target=None, items=[(x, None) for x in sentences]))
    for kind_name, difficulty, data in EXPRESSION:
        for title, question, *examples in rows(data):
            out.append(dict(category='expression', title=f"{kind_name} · {title}",
                            instruction=f"질문: {question}\n예시 문장을 따라 말하거나 내 생각으로 바꿔 말해 보세요.", difficulty=difficulty,
                            content_type=sentence_type(examples), rule=None, target=None, items=[(x, None) for x in examples]))
    for kind_name, difficulty, data in COMPREHENSION:
        for title, story, question, answer in rows(data):
            out.append(dict(category='comprehension', title=f"{kind_name} · {title}",
                            instruction=f"이야기: {story}\n질문: {question}\n이야기를 읽고 질문에 알맞은 대답을 말해 보세요.", difficulty=difficulty,
                            content_type=sentence_type([answer]), rule=None, target=None, items=[(answer, None)]))
    return out


def sentence_type(sentences):
    """문장 세트의 콘텐츠 유형: 가장 긴 문장 기준(기존 WORD·SHORT_SENTENCE·LONG_SENTENCE만 쓴다)"""
    longest = max(sentences, key=lambda x: len(x.split()))
    return 'LONG_SENTENCE' if len(longest.split()) >= 6 else 'SHORT_SENTENCE'


def check_extra(exercises):
    """품질 검사: 길이 제한, 빈 문항·금지어, 영역 안 제목 중복, 세트 안 문항 중복, 전체 세트 내용 중복, 발음 분류 검증"""
    problems, titles, signatures = [], set(), {}
    for ex in exercises:
        key = (ex['category'], ex['title'])
        if key in titles: problems.append(f"제목 중복: {key}")
        titles.add(key)
        texts = [t for t, _ in ex['items']]
        if len(ex['title']) > 120 or len(ex['instruction']) > 500: problems.append(f"길이 초과: {ex['title']}")
        if any(not t.strip() or len(t) > 500 for t in texts): problems.append(f"빈 문항/길이 초과: {ex['title']}")
        if len(set(texts)) != len(texts): problems.append(f"세트 안 문항 중복: {ex['title']}")
        if any(f in ex['title'] + ex['instruction'] + ''.join(texts) for f in FORBIDDEN): problems.append(f"금지어: {ex['title']}")
        signature = (ex['instruction'], tuple(texts))
        if signature in signatures: problems.append(f"같은 내용 세트: {ex['title']} = {signatures[signature]}")
        signatures[signature] = ex['title']
        if ex['rule'] and not all(valid(ex['rule'], ex['target'], t) for t in texts if ex['rule'] != 'BASIC_CONSONANT') \
                and not any(valid(ex['rule'], ex['target'], t) for t in texts):
            problems.append(f"발음 분류 불일치: {ex['title']}")
        if ex['rule'] == 'BASIC_CONSONANT' and not all(valid('BASIC_CONSONANT', ex['target'], t) for t in texts[:1]):
            problems.append(f"목표 자음 없음: {ex['title']}")
    return problems


def write_extra_sql(exercises, path):
    by_category, ex_rows, item_rows = {}, [], []
    for ex in exercises:
        n = by_category.get(ex['category'], 0); by_category[ex['category']] = n + 1
        eid = EXTRA_ID_BASE[ex['category']] + n
        # 정렬: 발음 추가 세트는 기존 세트(100~) 뒤, 다른 영역은 기존 기본 세트(1) 뒤
        sort = (400 if ex['category'] == 'articulation' else 10) + n
        tp = ex['target'] if ex['rule'] == 'BASIC_CONSONANT' else None
        ex_rows.append(f"({eid},{sql_text(ex['category'])},{sql_text(ex['title'])},{sql_text(ex['instruction'])},'mic',"
                       f"{'NULL' if tp is None else sql_text(tp)},{sort},{sql_text(ex['difficulty'])},{sql_text(ex['content_type'])},"
                       f"{'NULL' if ex['rule'] is None else sql_text(ex['rule'])})")
        for order, (text, emoji) in enumerate(ex['items'], 1):
            item_rows.append(f"({eid},{sql_text(text)},{'NULL' if emoji is None else sql_text(emoji)},{order})")
    lines = ["", "-- 발음 추가 세트(음절·모음·비슷한 소리 구별)와 어휘력·유창성·표현력·이해력 세트. 영역별 고정 ID(2001~/3001~/4001~/5001~/6001~).",
             "-- INSERT IGNORE: 이미 있는 행은 덮어쓰지 않고, 여러 번 실행해도 중복되지 않는다."]
    for i in range(0, len(ex_rows), 200):
        lines.append("INSERT IGNORE INTO exercises (exercise_id,category_id,title,instruction,input_type,target_phonemes,sort_order,difficulty,content_type,pronunciation_rule) VALUES\n"
                     + ",\n".join(ex_rows[i:i + 200]) + ";")
    for i in range(0, len(item_rows), 500):
        lines.append("INSERT IGNORE INTO exercise_items (exercise_id,text_value,emoji,sort_order) VALUES\n" + ",\n".join(item_rows[i:i + 500]) + ";")
    with open(path, 'a', encoding='utf-8') as f: f.write("\n".join(lines) + "\n")
    return by_category


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--limit', type=int)
    parser.add_argument('--out', default=os.path.join(os.path.dirname(__file__), '../../src/main/resources/data.sql'))
    args = parser.parse_args()
    exercises, total, rejected = build(args.limit)
    write_sql(exercises, args.out)
    from collections import Counter
    by_rule = Counter(); by_type = Counter(); by_level = Counter()
    for rule, target, ctype, n, texts in exercises:
        by_rule[RULE_LABEL[rule]] += len(texts); by_type[TYPE_LABEL[ctype]] += len(texts); by_level[LEVEL_LABEL[level(rule, ctype)]] += len(texts)
    print(f"연습 세트 {len(exercises)}개, 항목 {total}개 → {os.path.normpath(args.out)}")
    print("발음:", dict(by_rule)); print("유형:", dict(by_type)); print("난이도:", dict(by_level))
    print(f"검증 실패로 제외 {len(rejected)}개:", rejected)
    extra = extra_exercises()
    problems = check_extra(extra)
    if problems:
        print("추가 세트 검사 실패:", *problems, sep="\n  ")
        sys.exit(1)
    counts = write_extra_sql(extra, args.out)
    print("추가 세트(영역별):", counts, "합계", sum(counts.values()))
