#!/usr/bin/env python3
"""음성 분석 처리 시간 로그(speech.timing)를 요약해 PROCESSING 고착 기준(SPEECH_STALE_PROCESSING_AFTER)을 검증한다.

사용:
  python3 scripts/speech-timing-report.py 서버로그.log [...]      # 운영·스테이징 로그
  cat 서버로그.log | python3 scripts/speech-timing-report.py -     # 표준입력
  python3 scripts/speech-timing-report.py --junit build/test-results/integrationTest/*.xml   # 테스트 실행 로그(합성 데이터)

로그에는 단계별 시간·결과 코드만 있고 음성·인식 내용·파일 경로·사용자 정보는 없다.
표본이 적으면(기본 200건 미만) 기준값의 적절성을 판단하지 않는다고 표시한다.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

LINE = re.compile(r"speech\.timing stage=(\w+) (.*)$")
FIELD = re.compile(r"(\w+)=(\S+)")


def percentile(values, p):
    """nearest-rank 백분위수"""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, -(-len(ordered) * p // 100))  # ceil(n*p/100)
    return ordered[int(rank) - 1]


def read_lines(paths, junit):
    for path in paths:
        if path == "-":
            yield from sys.stdin
        elif junit:
            root = ET.parse(path).getroot()
            for node in root.iter("system-out"):
                yield from (node.text or "").splitlines()
        else:
            with open(path, encoding="utf-8", errors="replace") as handle:
                yield from handle


def parse(lines):
    records = []
    for line in lines:
        match = LINE.search(line)
        if not match:
            continue
        fields = dict(FIELD.findall(match.group(2)))
        fields["stage"] = match.group(1)
        records.append(fields)
    return records


def numbers(records, key):
    return [int(r[key]) for r in records if r.get(key, "-").isdigit()]


def row(label, values):
    if not values:
        return f"  {label:<12} 표본 없음"
    return (f"  {label:<12} n={len(values):<5} p50={percentile(values, 50):>7}ms  p95={percentile(values, 95):>7}ms  "
            f"p99={percentile(values, 99):>7}ms  max={max(values):>7}ms")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="+", help="로그 파일(또는 -)")
    parser.add_argument("--junit", action="store_true", help="Gradle JUnit XML의 system-out에서 읽는다(테스트 실행 = 합성 데이터)")
    parser.add_argument("--min-samples", type=int, default=200, help="기준값 판단에 필요한 최소 완료 건수(기본 200)")
    args = parser.parse_args()

    records = parse(read_lines(args.paths, args.junit))
    recognize = [r for r in records if r["stage"] == "recognize"]
    analysis = [r for r in records if r["stage"] == "analysis"]
    source = "테스트 실행 로그(합성 음성·테스트 환경)" if args.junit else "입력 로그"
    print(f"출처: {source}  /  recognize {len(recognize)}건, analysis {len(analysis)}건")

    if recognize:
        outcomes = Counter(r.get("outcome", "?") for r in recognize)
        total = len(recognize)
        print("\n[인식 단계] 결과:", ", ".join(f"{k} {v}건" for k, v in outcomes.most_common()))
        print(f"  Whisper 시간 초과(504) 비율 {outcomes.get('504', 0) / total:.1%}, 슬롯 대기 초과·중단(503) 비율 {outcomes.get('503', 0) / total:.1%}")
        vad = Counter(r.get("vad", "?") for r in recognize)
        print("  VAD:", ", ".join(f"{k} {v}건" for k, v in vad.most_common()))
        ok = [r for r in recognize if r.get("outcome") == "OK"]
        print("  (정상 처리 건 기준)")
        for key, label in (("totalMs", "전체"), ("queueMs", "슬롯 대기"), ("vadMs", "VAD"), ("whisperMs", "Whisper"), ("audioMs", "녹음 길이")):
            print(row(label, numbers(ok, key)))
        timeouts = sorted(set(numbers(recognize, "timeoutMs")))
        if timeouts:
            print(f"  단계별 시간 제한(WHISPER_TIMEOUT): {', '.join(f'{t}ms' for t in timeouts)}")

    if analysis:
        outcomes = Counter(r.get("outcome", "?") for r in analysis)
        print("\n[분석 전체(PROCESSING 행 생성 → 최종 상태)] 결과:", ", ".join(f"{k} {v}건" for k, v in outcomes.most_common()))
        completed = numbers([r for r in analysis if r.get("outcome") == "COMPLETED"], "totalMs")
        everything = numbers(analysis, "totalMs")
        print(row("완료", completed))
        print(row("전체(실패 포함)", everything))
        late = outcomes.get("LATE_AFTER_STALE", 0)
        print(f"  기준 시간 초과로 정리된 뒤 끝난 작업(LATE_AFTER_STALE): {late}건  ← 0이 아니면 정상 작업을 실패로 오판한 것")
        thresholds = sorted(set(numbers(analysis, "staleAfterMs")))
        if thresholds and everything:
            threshold = thresholds[-1]
            p99 = percentile(everything, 99)
            print(f"  현재 고착 기준 {threshold // 1000}초, 전체 p99 {p99}ms (기준의 {p99 / threshold:.2%}), 최대 {max(everything)}ms (기준의 {max(everything) / threshold:.2%})")
        if len(completed) < args.min_samples:
            print(f"  ※ 완료 표본 {len(completed)}건 < {args.min_samples}건: 이 데이터로 기준 시간의 적절성을 판단하지 않는다.")
        if args.junit:
            print("  ※ 테스트 실행 로그다. 운영 환경(실제 아동 음성·서버 사양·동시 사용량)의 처리 시간을 대신하지 않는다.")
    if not records:
        print("speech.timing 로그가 없습니다. (로그 수준 INFO 이상, LocalWhisperRecognitionService·SpeechAnalysisServiceImpl 로그 포함 필요)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
