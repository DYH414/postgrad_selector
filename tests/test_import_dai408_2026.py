import getpass
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from uuid import uuid4

import pymysql


ROOT = Path(__file__).resolve().parents[1]
SOCKET = os.environ.get("DAI408_TEST_SOCKET")
USER = getpass.getuser()
TABLES = ("admission_score", "admission_plan", "admission_result", "data_source",
          "program_year_data_quality")


@unittest.skipUnless(SOCKET, "Set DAI408_TEST_SOCKET to run the MariaDB integration test")
class Dai408ImportTest(unittest.TestCase):
    def test_dry_run_apply_and_repeat(self):
        database = "dai408_test_" + uuid4().hex
        with pymysql.connect(unix_socket=SOCKET, user=USER, autocommit=True) as server:
            with server.cursor() as cursor:
                self.addCleanup(self.drop_database, database)
                cursor.execute(f"CREATE DATABASE {database} CHARACTER SET utf8mb4")
        schema = (ROOT / "sql/10_postgrad_core_schema.sql").read_text()
        subprocess.run(
            ["mariadb", "--no-defaults", "--default-character-set=utf8mb4", "--socket", SOCKET,
             "--user", USER],
            input=schema.replace("postgrad_selector", database), text=True, check=True,
        )
        with (tempfile.TemporaryDirectory() as directory,
              pymysql.connect(unix_socket=SOCKET, user=USER, database=database,
                              autocommit=True, charset="utf8mb4") as connection):
            work = Path(directory)
            self.seed(connection)
            data = work / "dai408_scores.json"
            review = work / "review.json"
            data.write_text(json.dumps({
                "schema": "dai408-scores/v1",
                "items": [
                    self.source("001"), self.source("002"), self.source("003"),
                    self.source("004"), self.source("005"), self.source("006", y=2025),
                    self.source("007"), self.source("007", smin=302),
                    self.source("008"), self.source("009", c="College "),
                    self.source("010", ex="22409"), self.source("011", sm="非全日制"),
                    self.source("012"),
                ],
            }))

            command = [sys.executable, str(ROOT / "tools/import_dai408_2026.py"), str(data),
                       "--socket", SOCKET, "--database", database, "--review", str(review)]
            before = self.snapshot(connection)
            dry = json.loads(subprocess.check_output(command))
            self.assertEqual(dry["mode"], "dry_run")
            self.assertEqual(self.snapshot(connection), before)

            applied = json.loads(subprocess.check_output([*command, "--apply"]))
            self.assertEqual(applied["programs_improved"], [1, 2, 13])
            self.assertEqual(applied["rows"], {
                "data_source": {"insert": 1, "update": 0},
                "admission_result": {"insert": 1, "update": 1},
                "admission_plan": {"insert": 2, "update": 1},
            })
            entries = json.loads(review.read_text())["entries"]
            ambiguous = [e for e in entries if e["reason"] == "ambiguous"]
            self.assertEqual(
                [([p["id"] for p in e["programs"]], [s["smin"] for s in e["source_rows"]])
                 for e in ambiguous],
                [([7], [300, 302]), ([8, 9], [300])],
            )
            conflicts = [e for e in entries if e["reason"] == "field_conflict"]
            self.assertEqual([(e["program"]["id"], e["column"], e["local_value"], e["dai408_value"])
                              for e in conflicts],
                             [(1, "min_admitted_score", 310, 300), (1, "total_plan", 0, 32)])
            self.assertEqual([e["proposed_fills"] for e in entries
                              if e["reason"] == "existing_provenance"],
                             [{"max_admitted_score": 355, "avg_admitted_score": "326.5",
                               "admitted_count": 30}])

            with connection.cursor() as cursor:
                cursor.execute("""
                    SELECT program_id, year, min_admitted_score, max_admitted_score,
                           avg_admitted_score, admitted_count, source_id, verify_status
                    FROM admission_result ORDER BY program_id, year
                """)
                result = [(pid, year, low, high, None if avg is None else float(avg), count, sid, status)
                          for pid, year, low, high, avg, count, sid, status in cursor.fetchall()]
                source_id = applied["source_id"]
                self.assertEqual(result, [
                    (1, 2025, 275, None, None, None, None, "THIRD_PARTY"),
                    (1, 2026, 310, 355, 326.5, 30, source_id, "THIRD_PARTY"),
                    (2, 2026, 300, 355, 326.5, 30, source_id, "THIRD_PARTY"),
                    (13, 2026, 300, None, None, None, 1, "OFFICIAL_VERIFIED"),
                ])
                cursor.execute("""
                    SELECT program_id, total_plan, retest_count, unified_exam_quota, source_id
                    FROM admission_plan ORDER BY program_id, year
                """)
                self.assertEqual(cursor.fetchall(), (
                    (1, 0, 41, 12, source_id), (2, 32, 41, None, source_id),
                    (13, 32, 41, None, source_id),
                ))
                cursor.execute("""
                    SELECT source_type, title, source_owner FROM data_source WHERE id = %s
                """, (source_id,))
                kind, title, owner = cursor.fetchone()
                self.assertEqual(kind, "THIRD_PARTY")
                self.assertIn("非官方", title)
                self.assertIn("拟录取名单", title)
                self.assertIn("awarer.top", owner)
                cursor.execute("""
                    SELECT program_id, completeness_level FROM program_year_data_quality
                    WHERE year = 2026 ORDER BY program_id
                """)
                self.assertEqual(cursor.fetchall(), ((1, "A"), (2, "D"), (13, "D")))
            after = self.snapshot(connection)
            self.assertEqual(after["admission_score"], before["admission_score"])
            repeat = json.loads(subprocess.check_output([*command, "--apply"]))
            self.assertEqual(repeat["programs_improved"], [])
            self.assertTrue(all(n == 0 for table in repeat["rows"].values() for n in table.values()))
            self.assertEqual(self.snapshot(connection), after)

    def drop_database(self, database):
        with pymysql.connect(unix_socket=SOCKET, user=USER, autocommit=True) as connection:
            with connection.cursor() as cursor:
                cursor.execute(f"DROP DATABASE {database}")

    @staticmethod
    def source(code, **overrides):
        return {"s": "School", "c": "College", "dc": code, "y": 2026, "ex": "22408",
                "smin": 300, "smax": 355, "savg": 326.5, "adm": 30,
                "plan": 32, "retest": 41, "line": [51, 52, 80, 81], **overrides}

    @staticmethod
    def snapshot(connection):
        with connection.cursor() as cursor:
            snapshot = {}
            for table in TABLES:
                cursor.execute(f"SELECT * FROM {table} ORDER BY id")
                snapshot[table] = cursor.fetchall()
            return snapshot

    @staticmethod
    def seed(connection):
        with connection.cursor() as cursor:
            cursor.execute("INSERT INTO school (id, name, province, city) VALUES (1, 'School', 'P', 'C')")
            cursor.execute("INSERT INTO college (id, school_id, name) VALUES (1, 1, 'College')")
            cursor.executemany("""
                INSERT INTO program
                    (id, college_id, program_code, program_name, study_mode, degree_type, is_408, status)
                VALUES (%s, 1, %s, 'Program', %s, 'professional', %s, %s)
            """, [(i, code, "part_time" if i == 3 else "full_time", int(i != 4),
                   "suspended" if i == 5 else "active")
                  for i, code in enumerate(
                      ("001", "002", "003", "004", "005", "006", "007",
                       "008", "008", "009", "010", "011", "012"), 1)])
            cursor.execute("INSERT INTO admission_score (program_id, year, score_line) VALUES (1, 2026, 290)")
            cursor.execute("INSERT INTO data_source (id, source_type, title) VALUES (1, 'OFFICIAL', 'Official')")
            cursor.execute("""
                INSERT INTO admission_result (program_id, year, min_admitted_score, source_id, verify_status)
                VALUES (1, 2025, 275, NULL, 'THIRD_PARTY'), (1, 2026, 310, NULL, 'THIRD_PARTY'),
                       (13, 2026, 300, 1, 'OFFICIAL_VERIFIED')
            """)
            cursor.execute("""
                INSERT INTO admission_plan (program_id, year, total_plan, unified_exam_quota)
                VALUES (1, 2026, 0, 12)
            """)


if __name__ == "__main__":
    unittest.main()
