#!/usr/bin/env python3
"""samples/student/data.sql を生成する。

使い方:
    python3 gen_data.py                       # 既定: students=5000, enrolls=5000
    python3 gen_data.py --students 100000 --enrolls 200000
    python3 gen_data.py --out -               # 標準出力へ
    python3 gen_data.py --seed 7              # 再現性のあるシード変更

スキーマは samples/student/schema.sql に対応。出力ファイルはそのまま
`./start-cli.sh studentdb < samples/student/data.sql` でロード可能。
"""
from __future__ import annotations

import argparse
import random
import sys

DEPTS = [(10, "compsci"), (20, "math"), (30, "drama")]
COURSES = [
    (12, "db systems", 10),
    (22, "compilers", 10),
    (32, "calculus", 20),
    (42, "algebra", 20),
    (52, "acting", 30),
    (62, "elocution", 30),
]
SECTIONS = [
    (13, 12, "turing", 2018),
    (23, 12, "turing", 2019),
    (33, 32, "newton", 2019),
    (43, 32, "einstein", 2017),
    (53, 52, "brando", 2018),
]
NAMES = [
    "joe", "amy", "max", "sue", "bob", "kim", "art", "pat",
    "lee", "dan", "eve", "tim", "liz", "ann", "tom", "meg",
]
GRADES = ["A", "A-", "B+", "B", "B-", "C+", "C", "D", "F"]


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--students", type=int, default=5000, help="student テーブル件数 (default: 5000)")
    p.add_argument("--enrolls", type=int, default=5000, help="enroll テーブル件数 (default: 5000)")
    p.add_argument("--seed", type=int, default=42, help="乱数シード (default: 42)")
    p.add_argument("--out", default="data.sql", help="出力先。'-' で stdout")
    args = p.parse_args()

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
