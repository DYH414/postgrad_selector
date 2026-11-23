#!/usr/bin/env python3

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal
import getpass
from hashlib import sha256
import json
from pathlib import Path
import re
import tempfile

import pymysql


YEAR = 2026
SOURCE_TITLE = "Dai408 录取数据库（非官方，汇总自拟录取名单）"
SOURCE_OWNER = "Dai408 (awarer.top)，Laz8Noy/kaoyan408-share 转载"
SOURCE_URL = "https://laz8noy.github.io/kaoyan408-share/06-%E9%99%A2%E6%A0%A1%E6%95%B0%E6%8D%AE%E5%BA%93/data/dai408_scores.json"
FIELDS = {
    "admission_result": {
        "min_admitted_score": "smin",
        "max_admitted_score": "smax",
        "avg_admitted_score": "savg",
        "admitted_count": "adm",
    },
    "admission_plan": {"total_plan": "plan", "retest_count": "retest"},
}
QUALITY_SCRIPT = Path(__file__).resolve().parents[1] / "sql/90_data_quality_maintenance.sql"
LOCK_NAME = "postgrad:import-dai408-2026"
EXAM_408 = re.compile(r"(?:^[1-4]{2}408$|(?<![0-9])408(?![0-9]))")

PROGRAMS_SQL = """
    SELECT p.id, s.name AS school, c.name AS college, p.program_code AS code,
           p.program_name AS name, p.research_direction AS direction
    FROM program p
    JOIN college c ON c.id = p.college_id
    JOIN school s ON s.id = c.school_id
    WHERE p.is_408 = 1 AND p.study_mode = 'full_time' AND p.status = 'active'
    ORDER BY p.id
"""


@dataclass
class Write:
    table: str
    program_id: int
    insert: bool
    values: dict


def eligible_source_rows(document):
    if document["schema"] != "dai408-scores/v1":
        raise ValueError("Expected dai408-scores/v1")
    return [
        row for row in document["items"]
        if row["y"] == YEAR and EXAM_408.search(row.get("ex") or "")
        and all(row.get(field) for field in ("s", "c", "dc"))
        and row.get("sm") in (None, "全日制", "full_time")
    ]


def exact_matches(programs, source_rows):
    local_by_key = defaultdict(list)
    source_by_key = defaultdict(list)
    for program in programs:
        local_by_key[program["school"], program["college"], program["code"]].append(program)
    for row in source_rows:
        source_by_key[row["s"], row["c"], row["dc"]].append(row)

    matches = []
    review = []
    for key in sorted(local_by_key.keys() & source_by_key.keys()):
        local = local_by_key[key]
        source = source_by_key[key]
        if len(local) == len(source) == 1:
            matches.append((local[0], source[0]))
        else:
            review.append({
                "reason": "ambiguous", "key": key, "programs": local,
                "source_rows": source,
            })
    return matches, review


def mapped_value(column, value):
    if value is None:
        return None
    if not isinstance(value, (Decimal, int)) or isinstance(value, bool):
        raise ValueError(f"Invalid numeric value for {column}")
    if column == "avg_admitted_score":
        if 0 < value <= 500 and value == round(value, 1):
            return value
    elif value == int(value):
        if column.endswith("_score") and 0 < value <= 500:
            return int(value)
        if not column.endswith("_score") and 0 <= value <= 65535:
            return int(value)
    raise ValueError(f"Out of range or unsupported precision for {column}")


def plan_writes(matches, facts, source_id, review):
    writes = []
    for program, source in matches:
        program_id = program["id"]
        for table, columns in FIELDS.items():
            current = facts[table].get(program_id)
            fills = {}
            for column, source_key in columns.items():
                incoming = mapped_value(column, source.get(source_key))
                if incoming is None:
                    continue
                existing = current[column] if current else None
                if existing is None:
                    fills[column] = incoming
                elif existing != incoming:
                    review.append({
                        "reason": "field_conflict", "program": program, "table": table,
                        "column": column, "local_value": existing, "dai408_value": incoming,
                    })

            if not fills:
                continue
            if current and (current["source_id"] not in (None, source_id)
                            or current["verify_status"] != "THIRD_PARTY"):
                review.append({
                    "reason": "existing_provenance", "program": program, "table": table,
                    "source_id": current["source_id"], "verify_status": current["verify_status"],
                    "proposed_fills": fills,
                })
                continue
            writes.append(Write(table, program_id, current is None, fills))
    return writes


def write_fact(cursor, write, source_id):
    names = list(write.values)
    values = list(write.values.values())
    if write.insert:
        columns = ["program_id", "year", *names, "source_id", "verify_status"]
        placeholders = ", ".join(["%s"] * len(columns))
        cursor.execute(f"INSERT INTO {write.table} ({', '.join(columns)}) VALUES ({placeholders})",
                       [write.program_id, YEAR, *values, source_id, "THIRD_PARTY"])
    else:
        assignments = ", ".join(f"{name} = %s" for name in names)
        missing = " AND ".join(f"{name} IS NULL" for name in names)
        cursor.execute(f"""
            UPDATE {write.table} SET {assignments}, source_id = COALESCE(source_id, %s)
            WHERE program_id = %s AND year = %s AND {missing}
              AND (source_id IS NULL OR source_id = %s) AND verify_status = 'THIRD_PARTY'
        """, [*values, source_id, write.program_id, YEAR, source_id])
    if cursor.rowcount != 1:
        raise RuntimeError(f"Concurrent change in {write.table}, program {write.program_id}")


def run_import(connection, source_rows, apply, review_path, dataset_sha256):
    with connection.cursor() as cursor:
        if apply:
            cursor.execute("SELECT GET_LOCK(%s, 10) AS acquired", (LOCK_NAME,))
            if cursor.fetchone()["acquired"] != 1:
                raise RuntimeError("Another Dai408 import is running")
        cursor.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
        cursor.execute("START TRANSACTION" if apply else "START TRANSACTION READ ONLY")
        lock = " FOR UPDATE" if apply else ""
        cursor.execute(PROGRAMS_SQL + lock)
        matches, review = exact_matches(cursor.fetchall(), source_rows)

        metadata = (SOURCE_URL, SOURCE_TITLE, SOURCE_OWNER)
        cursor.execute("""
            SELECT id FROM data_source WHERE source_type = 'THIRD_PARTY'
              AND url = %s AND title = %s AND source_owner = %s ORDER BY id LIMIT 1
        """ + lock, metadata)
        existing_source = cursor.fetchone()
        source_id = existing_source["id"] if existing_source else None

        facts = {}
        for table, columns in FIELDS.items():
            facts[table] = {}
            if matches:
                ids = tuple(program["id"] for program, _ in matches)
                cursor.execute(f"""
                    SELECT program_id, source_id, verify_status, {', '.join(columns)}
                    FROM {table} WHERE year = %s AND program_id IN %s
                """ + lock, (YEAR, ids))
                facts[table] = {row["program_id"]: row for row in cursor.fetchall()}

        writes = plan_writes(matches, facts, source_id, review)
        source_insert = bool(writes and source_id is None)
        write_review(review_path, review, dataset_sha256)
        if apply and writes:
            if source_insert:
                cursor.execute("""
                    INSERT INTO data_source (source_type, url, title, source_owner)
                    VALUES ('THIRD_PARTY', %s, %s, %s)
                """, metadata)
                source_id = cursor.lastrowid
            for write in writes:
                write_fact(cursor, write, source_id)
            cursor.execute(QUALITY_SCRIPT.read_text(encoding="utf-8"))
            connection.commit()
        else:
            connection.rollback()

    return {
        "mode": "apply" if apply else "dry_run",
        "year": YEAR,
        "eligible_source_rows": len(source_rows),
        "unique_matches": len(matches),
        "programs_improved": sorted({write.program_id for write in writes}),
        "source_id": source_id,
        "rows": {
            "data_source": {"insert": int(source_insert), "update": 0},
            **{
                table: {
                    "insert": sum(write.table == table and write.insert for write in writes),
                    "update": sum(write.table == table and not write.insert for write in writes),
                }
                for table in FIELDS
            },
        },
        "review_counts": dict(Counter(item["reason"] for item in review)),
    }


def write_review(path, review, dataset_sha256):
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     delete=False) as output:
        temp = Path(output.name)
        try:
            json.dump({"year": YEAR, "dataset_sha256": dataset_sha256, "entries": review},
                      output, ensure_ascii=False, indent=2, default=str)
            output.write("\n")
            output.flush()
            temp.replace(path)
        finally:
            temp.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(
        description="Fill 2026 Dai408 facts in a local MariaDB catalog (requires PyMySQL).")
    parser.add_argument("dataset", type=Path, help="Local dai408_scores.json")
    parser.add_argument("--socket", type=Path, required=True, help="Local MariaDB Unix socket")
    parser.add_argument("--database", default="postgrad_selector")
    parser.add_argument("--user", default=getpass.getuser(), help="MariaDB socket-authenticated user")
    parser.add_argument("--review", type=Path, required=True, help="Output file for manual review")
    parser.add_argument("--apply", action="store_true", help="Commit fills and refresh quality grades")
    args = parser.parse_args()
    if args.review.resolve() == args.dataset.resolve():
        parser.error("The review output must be different from the dataset")
    data = args.dataset.read_bytes()
    rows = eligible_source_rows(json.loads(data, parse_float=Decimal))
    dataset_sha256 = sha256(data).hexdigest()
    with pymysql.connect(unix_socket=str(args.socket), user=args.user, database=args.database,
                         cursorclass=pymysql.cursors.DictCursor, charset="utf8mb4") as connection:
        summary = run_import(connection, rows, args.apply, args.review, dataset_sha256)
    print(json.dumps({**summary, "dataset_sha256": dataset_sha256, "review_file": str(args.review)},
                     indent=2))


if __name__ == "__main__":
    main()
