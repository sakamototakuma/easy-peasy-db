#!/usr/bin/env python3
"""samples/student/data.sql を生成する。

使い方:
    python3 gen_data.py                            # 既定: students=5000, enrolls=5000
    python3 gen_data.py --profile bench-medium     # プリセット選択
    python3 gen_data.py --students 100000 --enrolls 500000
    python3 gen_data.py --out -                    # 標準出力へ
    python3 gen_data.py --seed 7                   # 再現性のあるシード変更

プロファイル一覧（buffer pool = 4 MiB の前提）:
    smoke         : students=  5,000  enrolls=  5,000   (動作確認)
    bench-small   : students= 50,000  enrolls=100,000   (バッファプール内)
    bench-medium  : students=200,000  enrolls=500,000   (バッファプール超え:推奨)
    bench-large   : students=500,000  enrolls=2,000,000 (本格ベンチ)

スキーマは samples/student/schema.sql に対応。出力ファイルはそのまま
`./start-cli.sh studentdb < samples/student/data.sql` でロード可能。
"""
from __future__ import annotations

import argparse
import random
import sys

# dept.dname は varchar(8) なので 8 文字以内
DEPTS = [
    (10, "compsci"),
    (20, "math"),
    (30, "drama"),
    (40, "physics"),
    (50, "biology"),
    (60, "chem"),
    (70, "history"),
    (80, "art"),
    (90, "music"),
    (100, "econ"),
    (110, "stats"),
    (120, "philos"),
]

# course.title は varchar(20) 以内 / 各 dept につき 3 講義
_COURSES_PER_DEPT = {
    10: ["db systems", "compilers", "graphics"],
    20: ["calculus", "algebra", "topology"],
    30: ["acting", "directing", "stagecraft"],
    40: ["mechanics", "optics", "quantum"],
    50: ["genetics", "ecology", "anatomy"],
    60: ["organic", "inorganic", "biochem"],
    70: ["world hist", "us hist", "ancient hist"],
    80: ["painting", "sculpture", "drawing"],
    90: ["theory", "harmony", "composition"],
    100: ["micro econ", "macro econ", "game theory"],
    110: ["probability", "regression", "bayesian"],
    120: ["logic", "ethics", "metaphysics"],
}
COURSES = []
_cid = 1000
for _did, _titles in _COURSES_PER_DEPT.items():
    for _t in _titles:
        COURSES.append((_cid, _t, _did))
        _cid += 1
# COURSES: 12 dept × 3 = 36 件

# sect.prof は varchar(8) 以内
PROFS = [
    "turing", "newton", "einstein", "brando", "curie", "darwin",
    "tesla", "bohr", "hawking", "pasteur", "kepler", "faraday",
    "mozart", "vivaldi", "picasso", "dali", "monet", "plato",
    "aristotl", "kant", "nash", "feynman", "fermi", "gauss",
]
YEARS = [2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024]

# 各 course につき 3 sect = 36 × 3 = 108 件
SECTIONS = []
_sid = 2000
_rng = random.Random(0)
for _c in COURSES:
    _course_id = _c[0]
    for _ in range(3):
        SECTIONS.append((_sid, _course_id, _rng.choice(PROFS), _rng.choice(YEARS)))
        _sid += 1

# student.sname は varchar(10) 以内
NAMES = [
    "joe", "amy", "max", "sue", "bob", "kim", "art", "pat",
    "lee", "dan", "eve", "tim", "liz", "ann", "tom", "meg",
    "sam", "ali", "jan", "ben", "ron", "ray", "jim", "ken",
    "leo", "ned", "hal", "gia", "mia", "ivy", "jay", "kai",
    "pam", "rob", "tina", "val", "wes", "zoe", "ed", "al",
    "may", "kit", "pia", "rex", "sid", "tia", "von", "yui",
    "noa", "uma", "fay", "gus", "hugo", "ira", "jules", "lana",
    "milo", "nina", "otis", "remy",
]
# enroll.grade は varchar(2) 以内
GRADES = ["A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F"]


PROFILES = {
    "smoke":        (5_000,    5_000),
    "bench-small":  (50_000,   100_000),
    "bench-medium": (200_000,  500_000),
    "bench-large":  (500_000,  2_000_000),
}


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--profile", choices=PROFILES.keys(), help="プリセット (--students/--enrolls より弱い)")
    p.add_argument("--students", type=int, help="student テーブル件数")
    p.add_argument("--enrolls", type=int, help="enroll テーブル件数")
    p.add_argument("--seed", type=int, default=42, help="乱数シード (default: 42)")
    p.add_argument("--out", default="data.sql", help="出力先。'-' で stdout")
    args = p.parse_args()

    if args.profile:
        ps, pe = PROFILES[args.profile]
    else:
        ps, pe = 5000, 5000
    if args.students is not None:
        ps = args.students
    if args.enrolls is not None:
        pe = args.enrolls
    args.students = ps
    args.enrolls = pe

    rng = random.Random(args.seed)
    out = sys.stdout if args.out == "-" else open(args.out, "w")

    try:
        for did, dname in DEPTS:
            out.write(f"insert into dept (did, dname) values ({did}, '{dname}');\n")
        for cid, title, deptid in COURSES:
            out.write(f"insert into course (cid, title, deptid) values ({cid}, '{title}', {deptid});\n")
        for sectid, courseid, prof, year in SECTIONS:
            out.write(
                f"insert into sect (sectid, courseid, prof, yearoffered) "
                f"values ({sectid}, {courseid}, '{prof}', {year});\n"
            )

        dept_ids = [d[0] for d in DEPTS]
        section_ids = [s[0] for s in SECTIONS]

        for sid in range(1, args.students + 1):
            sname = rng.choice(NAMES)
            majorid = rng.choice(dept_ids)
            gradyear = rng.randint(2018, 2025)
            out.write(
                f"insert into student (sid, sname, majorid, gradyear) "
                f"values ({sid}, '{sname}', {majorid}, {gradyear});\n"
            )

        for eid in range(1, args.enrolls + 1):
            studentid = rng.randint(1, args.students)
            sectionid = rng.choice(section_ids)
            grade = rng.choice(GRADES)
            out.write(
                f"insert into enroll (eid, studentid, sectionid, grade) "
                f"values ({eid}, {studentid}, {sectionid}, '{grade}');\n"
            )
    finally:
        if out is not sys.stdout:
            out.close()


if __name__ == "__main__":
    main()
