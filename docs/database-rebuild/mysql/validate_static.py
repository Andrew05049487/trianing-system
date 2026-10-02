"""Read-only DDL/Entity audit; NOT a MySQL parser or execution test.

Python 3, standard library only. Run from any directory. --emit-validation prints
the reproducible information_schema validation SQL (never connects or writes).
"""
from pathlib import Path
import json
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
COLLATION = "utf8mb4_0900_as_cs"
MAIN = "users exercise exercise_result user_bindings friend_requests friendships password_reset_requests chat_conversations chat_messages custom_rehab_exercises custom_exercise_assignments exercise_assignments rehab_plans rehab_plan_items training_history training_history_video training_session_results user_avatars".split()
RESEARCH = "research_consents research_samples research_annotations research_annotation_revisions research_audit research_grants research_review_requests research_grant_audit research_export_audit research_retention_policies research_retention_events".split()
# Independent graph reviewed against Round 1/02 and the original migrations.
FK_GRAPH = {
    "exercise_result": "user_id:users exercise_id:exercise",
    "user_bindings": "patient_id:users linked_user_id:users",
    "friend_requests": "sender_id:users receiver_id:users",
    "friendships": "user_low_id:users user_high_id:users",
    "password_reset_requests": "user_id:users",
    "chat_conversations": "participant_one_id:users participant_two_id:users",
    "chat_messages": "conversation_id:chat_conversations sender_id:users",
    "custom_rehab_exercises": "created_by_therapist_id:users",
    "custom_exercise_assignments": "custom_exercise_id:custom_rehab_exercises patient_id:users assigned_by_therapist_id:users",
    "exercise_assignments": "exercise_id:exercise patient_id:users assigned_by_therapist_id:users",
    "rehab_plans": "patient_id:users", "rehab_plan_items": "rehab_plan_id:rehab_plans",
    "training_history": "user_id:users", "training_history_video": "history_id:training_history",
    "training_session_results": "patient_id:users", "user_avatars": "user_id:users",
    "research_consents": "user_id:users", "research_samples": "participant_user_id:users",
    "research_annotations": "sample_id:research_samples therapist_user_id:users",
    "research_annotation_revisions": "sample_id:research_samples",
    "research_grants": "user_id:users", "research_review_requests": "user_id:users",
}


def parts(text):
    """Split commas outside parentheses/SQL literals."""
    result, start, depth, quoted = [], 0, 0, False
    for i, char in enumerate(text):
        if char == "'":
            quoted = not quoted
        elif not quoted:
            depth += (char == "(") - (char == ")")
            if char == "," and depth == 0:
                result.append(text[start:i].strip())
                start = i + 1
        assert depth >= 0, "Unbalanced parentheses"
    assert not quoted and depth == 0, "Unterminated literal/parentheses"
    return result + [text[start:].strip()]


def parse():
    tables, columns, indexes, fks, checks = [], [], [], [], []
    for filename, names, stage in [("V001__main_schema.sql", MAIN, 1), ("V002__research_schema.sql", RESEARCH, 2)]:
        sql = re.sub(r"--[^\n]*", "", (HERE / filename).read_text(encoding="utf-8"))
        assert not re.search(r"\b(IDENTITY|NVARCHAR|VARBINARY|DATETIME2|PERSISTED|OBJECT_ID|COL_LENGTH|GO|DROP|INSERT|ALTER)\b|\bdbo\.", sql, re.I), "Unexpected T-SQL/data/destructive statement"
        found = re.findall(r"CREATE TABLE (\w+)\s*\((.*?)\)\s*ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=(\w+);", sql, re.S)
        assert [row[0] for row in found] == names, f"Table count/order differs in {filename}"
        for table, body, collation in found:
            assert collation == COLLATION
            tables.append(dict(t=table, s=stage))
            for declaration in parts(body):
                fk = re.fullmatch(r"CONSTRAINT (\w+) FOREIGN KEY \((\w+)\) REFERENCES (\w+)\((\w+)\)(?: ON DELETE (RESTRICT|CASCADE))?(?: ON UPDATE (RESTRICT))?", declaration)
                check = re.fullmatch(r"CONSTRAINT (\w+) CHECK \((.*)\)", declaration, re.S)
                idx = re.fullmatch(r"(PRIMARY KEY|UNIQUE KEY|KEY)(?: (\w+))? \((.*)\)", declaration)
                if fk:
                    name, col, target, ref, delete, update = fk.groups()
                    assert target in [t["t"] for t in tables], f"FK target not yet created: {target}"
                    fks.append(dict(t=table, s=stage, n=name, c=col, rt=target, rc=ref, d=delete or "RESTRICT", u=update or "RESTRICT"))
                elif check:
                    checks.append(dict(t=table, s=stage, n=check[1], e=check[2]))
                elif idx:
                    kind, name, cols = idx.groups()
                    indexes.append(dict(t=table, s=stage, n=name or "PRIMARY", unique=int(kind != "KEY"), c=",".join(c.strip() if c.strip().endswith(" DESC") else c.strip() + " ASC" for c in parts(cols))))
                else:
                    col = re.fullmatch(r"(\w+) ((?:VARCHAR|DECIMAL|DATETIME|TINYINT)\([\d,]+\)|BIGINT|INT|DOUBLE|DATE|LONGTEXT|LONGBLOB)(.*)", declaration, re.S)
                    assert col, f"Unrecognized declaration: {declaration}"
                    name, typ, rest = col.groups()
                    gen = re.search(r"GENERATED ALWAYS AS\s*\((.*)\) (STORED|VIRTUAL)", rest, re.S)
                    default = re.search(r"DEFAULT (\(UTC_TIMESTAMP\(6\)\)|0)", rest)
                    coll = re.search(r"COLLATE (\w+)", rest)
                    columns.append(dict(t=table, s=stage, n=name, typ=typ.lower(), nullable="NO" if "NOT NULL" in rest else "YES", d=default[1] if default else None, auto=int("AUTO_INCREMENT" in rest), gen=gen[1] if gen else "", storage=gen[2] if gen else "", coll=(coll[1] if coll else COLLATION) if typ.startswith("VARCHAR") or typ == "LONGTEXT" else None))
    assert len(tables) == 29
    for table in tables:
        cs = [c["n"] for c in columns if c["t"] == table["t"]]
        assert len(cs) == len(set(cs)), "Duplicate column"
        assert sum(i["n"] == "PRIMARY" and i["t"] == table["t"] for i in indexes) == 1
        for idx in [i for i in indexes if i["t"] == table["t"]]:
            assert all(c.split()[0] in cs for c in idx["c"].split(","))
            assert sum(next(x["typ"].startswith("varchar") and int(re.search(r"\d+", x["typ"])[0]) * 4 or 8 for x in columns if x["t"] == idx["t"] and x["n"] == c.split()[0]) for c in idx["c"].split(",")) <= 3072, "Index byte budget exceeded"
    assert len({f["n"] for f in fks}) == len(fks)
    assert len({c["n"] for c in checks}) == len(checks)
    assert {(f["t"], f["c"], f["rt"]) for f in fks} == {(t, item.split(":")[0], item.split(":")[1]) for t, value in FK_GRAPH.items() for item in value.split()}, "FK graph differs from reviewed source"
    assert len(checks) == 16 and len(indexes) == 68, "Reviewed constraints/index count differs"
    expected_defaults = {
        ("exercise_result", "created_at"): "(UTC_TIMESTAMP(6))",
        ("password_reset_requests", "failed_attempts"): "0",
        ("rehab_plans", "created_at"): "(UTC_TIMESTAMP(6))",
        ("rehab_plans", "updated_at"): "(UTC_TIMESTAMP(6))",
        ("rehab_plan_items", "done"): "0", ("training_history", "completed_reps"): "0",
        ("training_history", "template_valid_rep_count"): "0",
        ("user_avatars", "updated_at"): "(UTC_TIMESTAMP(6))",
        ("research_annotations", "revision"): "0",
    }
    assert {(c["t"], c["n"]): c["d"] for c in columns if c["d"] is not None} == expected_defaults, "SQL defaults differ (Java initial values are not defaults)"
    for fk in fks:
        a = next(c for c in columns if c["t"] == fk["t"] and c["n"] == fk["c"])
        b = next(c for c in columns if c["t"] == fk["rt"] and c["n"] == fk["rc"])
        assert (a["typ"], a["coll"]) == (b["typ"], b["coll"]), "Incompatible FK types/collation"
        assert any(i["t"] == fk["rt"] and i["unique"] and i["c"] == fk["rc"] + " ASC" for i in indexes)
    assert {f["t"] for f in fks if f["d"] == "CASCADE"} == {"password_reset_requests", "rehab_plan_items", "training_history_video"}
    assert not any(f["t"] in {"research_audit", "research_grant_audit", "research_export_audit", "research_retention_policies", "research_retention_events"} for f in fks)
    assert not any(f["t"] in {"rehab_plan_items", "training_session_results"} and f["c"] == "exercise_id" for f in fks)
    active = next(c for c in columns if c["n"] == "active_user_id")
    assert active["storage"] == "VIRTUAL" and "consumed_at IS NULL" in active["gen"]
    rev = next(i for i in indexes if i["n"] == "idx_research_annotation_revision_sample")
    assert not rev["unique"]
    return tables, columns, indexes, fks, checks


def audit_entities(columns, indexes):
    """Compare every current @Entity field against DDL; no Hibernate startup."""
    seen = set()
    count = 0
    for path in sorted((ROOT / "src/main/java").rglob("*.java")):
        java = path.read_text(encoding="utf-8")
        if not re.search(r"^@Entity\b", java, re.M):
            continue
        table = re.search(r"@Table\(\s*name\s*=\s*\"(\w+)\"", java)[1]
        for unique in re.finditer(r"@UniqueConstraint\((.*?)\)", java, re.S):
            name = re.search(r'name\s*=\s*"([^\"]+)"', unique[1])[1]
            cols = re.search(r'columnNames\s*=\s*(\{[^}]+\}|"[^\"]+")', unique[1])[1]
            expected = ",".join(c + " ASC" for c in re.findall(r'"([^\"]+)"', cols))
            assert any(i["t"] == table and i["n"] == name and i["unique"] and i["c"] == expected for i in indexes), f"Missing Entity UNIQUE: {table}.{name}"
        for index in re.finditer(r"@Index\((.*?)\)", java, re.S):
            name = re.search(r'name\s*=\s*"([^\"]+)"', index[1])[1]
            cols = re.search(r'columnList\s*=\s*"([^\"]+)"', index[1])[1]
            expected = ",".join(c.strip() + " ASC" for c in cols.split(","))
            assert any(i["t"] == table and i["n"] == name and i["c"] == expected for i in indexes), f"Missing Entity INDEX: {table}.{name}"
        fields = re.split(r"public class \w+\s*\{", java)[1]
        previous = 0
        for field in re.finditer(r"private ([\w\[\]]+) (\w+)(?:\s*=.*?)?;", fields):
            annotations = fields[previous:field.start()]
            previous = field.end()
            if "@Transient" in annotations or field[1] == "static":
                continue
            name = re.search(r"@(?:Column|JoinColumn)\(\s*name\s*=\s*\"(\w+)\"", annotations)
            name = name[1] if name else re.sub(r"(?<!^)([A-Z])", r"_\1", field[2]).lower()
            seen.add((table, name))
            actual = next((c for c in columns if (c["t"], c["n"]) == (table, name)), None)
            assert actual, f"Missing Entity field {path.name}:{name}"
            if re.search(r"unique\s*=\s*true", annotations):
                assert any(i["t"] == table and i["unique"] and i["c"] == name + " ASC" for i in indexes)
            typ = field[1]
            declared = re.search(r'columnDefinition\s*=\s*"([^\"]+)"', annotations)
            size = re.search(r"length\s*=\s*(\d+)", annotations)
            if typ == "String" or "@Enumerated" in annotations:
                expected = "varchar(" + (size[1] if size else "255") + ")"
                if declared:
                    expected = "longtext" if "MAX" in declared[1].upper() else "varchar(" + re.search(r"\d+", declared[1])[0] + ")"
                assert actual["typ"] == expected, f"String type/length {table}.{name}: {actual['typ']} != {expected}"
            elif typ in {"Long", "long", "Integer", "int", "Double", "double", "Boolean", "boolean", "Instant", "LocalDateTime", "LocalDate", "byte[]"}:
                expected = {"Long": "bigint", "long": "bigint", "Integer": "int", "int": "int", "Double": "double", "double": "double", "Boolean": "tinyint(1)", "boolean": "tinyint(1)", "Instant": "datetime(6)", "LocalDateTime": "datetime(6)", "LocalDate": "date", "byte[]": "longblob"}[typ]
                assert actual["typ"] == expected, f"Type differs: {table}.{name}"
            elif typ == "BigDecimal":
                precision = re.search(r"precision\s*=\s*(\d+)", annotations)[1]
                scale = re.search(r"scale\s*=\s*(\d+)", annotations)[1]
                assert actual["typ"] == f"decimal({precision},{scale})"
            else:
                # Relation type is checked against the referenced PK by parse().
                assert "@JoinColumn" in annotations, f"Unreviewed field type {typ}"
            nullable = "NO" if "@Id" in annotations or re.search(r"nullable\s*=\s*false", annotations) else "YES"
            # Fresh rebuild decision: DB-owned timestamp must always exist.
            if (table, name) != ("exercise_result", "created_at"):
                assert actual["nullable"] == nullable, f"Nullability differs {table}.{name}: Entity={nullable}, DDL={actual['nullable']}"
            count += 1
    assert set((c["t"], c["n"]) for c in columns) - seen == {("users", "account_id_normalized"), ("password_reset_requests", "active_user_id")}
    return count


def validation_sql(manifests):
    header = """-- Generated from V001/V002 by validate_static.py --emit-validation.
-- READ ONLY apart from session variables. Select the isolated schema first.
-- SET @r2_include_research=0 for 18 tables, =1 for 29; default is main only.
-- Empty *_DRIFT result sets are required. Counters/context alone are not PASS.
SET @r2_include_research=COALESCE(@r2_include_research,0);
SET @r2_stage=IF(@r2_include_research=1,2,1);
SET SESSION group_concat_max_len=8192;
SELECT DATABASE() AS selected_schema, VERSION() AS server_version,
       @@session.time_zone AS session_time_zone, @@sql_mode AS sql_mode,
       @@max_allowed_packet AS max_allowed_packet;
"""
    declarations = []
    specs = [
        ("tables", "t VARCHAR(64) PATH '$.t', s INT PATH '$.s'"),
        ("columns", "t VARCHAR(64) PATH '$.t', s INT PATH '$.s', n VARCHAR(64) PATH '$.n', typ VARCHAR(64) PATH '$.typ', nullable VARCHAR(3) PATH '$.nullable', d VARCHAR(100) PATH '$.d', auto INT PATH '$.auto', gen VARCHAR(300) PATH '$.gen', storage VARCHAR(10) PATH '$.storage', coll VARCHAR(64) PATH '$.coll'"),
        ("indexes", "t VARCHAR(64) PATH '$.t', s INT PATH '$.s', n VARCHAR(64) PATH '$.n', uq INT PATH '$.unique', c VARCHAR(512) PATH '$.c'"),
        ("fks", "t VARCHAR(64) PATH '$.t', s INT PATH '$.s', n VARCHAR(64) PATH '$.n', c VARCHAR(64) PATH '$.c', rt VARCHAR(64) PATH '$.rt', rc VARCHAR(64) PATH '$.rc', d VARCHAR(16) PATH '$.d', u VARCHAR(16) PATH '$.u'"),
        ("checks", "t VARCHAR(64) PATH '$.t', s INT PATH '$.s', n VARCHAR(64) PATH '$.n', e VARCHAR(512) PATH '$.e'")]
    for (name, spec), manifest in zip(specs, manifests):
        # SQL literals escape apostrophes by doubling (independent of backslash mode).
        payload = json.dumps(manifest, ensure_ascii=True, separators=(",", ":")).replace("'", "''")
        declarations.append(f"SET @r2_{name}='{payload}';\n")
    norm = lambda expr: f"LOWER(REGEXP_REPLACE(REPLACE(COALESCE({expr},''),'_utf8mb4',''),'[[:space:]`()]+',''))"
    # Preserve CHECK string literal case (case-sensitive role/source constraints).
    check_norm = lambda expr: f"REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(COALESCE({expr},''),'_utf8mb4',''),'[[:space:]]+IN[[:space:]]+',' in ',1,0,'i'),'[[:space:]`()]+','')"
    cte = lambda name: f"WITH expected AS (SELECT * FROM JSON_TABLE(@r2_{name}, '$[*]' COLUMNS ({dict(specs)[name]})) j WHERE s<=@r2_stage)\n"
    queries = [cte("tables") + """SELECT 'TABLE_DRIFT' AS audit, e.t, a.ENGINE, a.TABLE_COLLATION
FROM expected e LEFT JOIN information_schema.TABLES a ON a.TABLE_SCHEMA=DATABASE() AND a.TABLE_NAME=e.t
WHERE a.TABLE_NAME IS NULL OR a.ENGINE<>'InnoDB' OR a.TABLE_COLLATION<>'utf8mb4_0900_as_cs'
UNION ALL SELECT 'EXTRA_TABLE', a.TABLE_NAME, a.ENGINE, a.TABLE_COLLATION
FROM information_schema.TABLES a LEFT JOIN expected e ON e.t=a.TABLE_NAME
WHERE a.TABLE_SCHEMA=DATABASE() AND e.t IS NULL;
""",
        cte("columns") + f"""SELECT 'COLUMN_DRIFT' AS audit, e.t, e.n, e.typ AS expected_type, a.COLUMN_TYPE AS actual_type,
e.nullable AS expected_nullable, a.IS_NULLABLE AS actual_nullable, e.d AS expected_default, a.COLUMN_DEFAULT AS actual_default,
a.EXTRA, a.GENERATION_EXPRESSION, a.COLLATION_NAME
FROM expected e LEFT JOIN information_schema.COLUMNS a ON a.TABLE_SCHEMA=DATABASE() AND a.TABLE_NAME=e.t AND a.COLUMN_NAME=e.n
WHERE a.COLUMN_NAME IS NULL OR a.COLUMN_TYPE<>e.typ OR a.IS_NULLABLE<>e.nullable
OR NOT ({norm('a.COLUMN_DEFAULT')} <=> {norm('e.d')})
OR (LOCATE('auto_increment',a.EXTRA)>0)<>e.auto
OR {norm('a.GENERATION_EXPRESSION')}<>{norm('e.gen')}
OR (e.storage<>'' AND LOCATE(CONCAT(e.storage,' GENERATED'),UPPER(a.EXTRA))=0)
OR (e.storage='' AND LOCATE('GENERATED',UPPER(a.EXTRA))>0 AND LOCATE('DEFAULT_GENERATED',UPPER(a.EXTRA))=0)
OR NOT (a.COLLATION_NAME <=> e.coll);
""",
        cte("columns") + """SELECT 'EXTRA_COLUMN' AS audit, a.TABLE_NAME, a.COLUMN_NAME
FROM information_schema.COLUMNS a LEFT JOIN expected e ON a.TABLE_NAME=e.t AND a.COLUMN_NAME=e.n
WHERE a.TABLE_SCHEMA=DATABASE() AND e.n IS NULL;
""",
        cte("indexes") + """, actual AS (
SELECT TABLE_NAME t, INDEX_NAME n, MIN(NON_UNIQUE) non_unique,
GROUP_CONCAT(CONCAT(COLUMN_NAME,IF(COLLATION='D',' DESC',' ASC')) ORDER BY SEQ_IN_INDEX SEPARATOR ',') c,
SUM(SUB_PART IS NOT NULL) prefixes, MIN(IS_VISIBLE) visible, MIN(INDEX_TYPE) kind
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() GROUP BY TABLE_NAME,INDEX_NAME)
SELECT 'INDEX_DRIFT' AS audit, e.t, e.n, e.c AS expected_columns, a.c AS actual_columns
FROM expected e LEFT JOIN actual a ON a.t=e.t AND a.n=e.n
WHERE a.n IS NULL OR a.non_unique<>1-e.uq OR a.c<>e.c OR a.prefixes<>0 OR a.visible<>'YES' OR a.kind<>'BTREE';
""",
        cte("fks") + """SELECT 'FK_DRIFT' AS audit, e.t, e.n, e.c, e.rt, e.rc, r.DELETE_RULE, r.UPDATE_RULE
FROM expected e LEFT JOIN information_schema.KEY_COLUMN_USAGE k
ON k.CONSTRAINT_SCHEMA=DATABASE() AND k.TABLE_NAME=e.t AND k.CONSTRAINT_NAME=e.n
LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=DATABASE() AND r.CONSTRAINT_NAME=e.n AND r.TABLE_NAME=e.t
WHERE k.CONSTRAINT_NAME IS NULL OR k.COLUMN_NAME<>e.c OR k.REFERENCED_TABLE_SCHEMA<>DATABASE()
OR k.REFERENCED_TABLE_NAME<>e.rt OR k.REFERENCED_COLUMN_NAME<>e.rc
OR REPLACE(r.DELETE_RULE,'NO ACTION','RESTRICT')<>e.d OR REPLACE(r.UPDATE_RULE,'NO ACTION','RESTRICT')<>e.u;
""",
        cte("fks") + """SELECT 'EXTRA_FK' AS audit, a.TABLE_NAME, a.CONSTRAINT_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS a LEFT JOIN expected e ON a.TABLE_NAME=e.t AND a.CONSTRAINT_NAME=e.n
WHERE a.CONSTRAINT_SCHEMA=DATABASE() AND e.n IS NULL;
""",
        cte("checks") + f"""SELECT 'CHECK_DRIFT' AS audit, e.t, e.n, e.e AS expected_clause, a.CHECK_CLAUSE AS actual_clause, tc.ENFORCED
FROM expected e LEFT JOIN information_schema.CHECK_CONSTRAINTS a ON a.CONSTRAINT_SCHEMA=DATABASE() AND a.CONSTRAINT_NAME=e.n
LEFT JOIN information_schema.TABLE_CONSTRAINTS tc ON tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME=e.t AND tc.CONSTRAINT_NAME=e.n
WHERE a.CONSTRAINT_NAME IS NULL OR tc.ENFORCED<>'YES' OR {check_norm('a.CHECK_CLAUSE')}<>{check_norm('e.e')};
""",
        cte("checks") + """SELECT 'EXTRA_CHECK' AS audit, a.TABLE_NAME, a.CONSTRAINT_NAME
FROM information_schema.TABLE_CONSTRAINTS a LEFT JOIN expected e ON a.TABLE_NAME=e.t AND a.CONSTRAINT_NAME=e.n
WHERE a.CONSTRAINT_SCHEMA=DATABASE() AND a.CONSTRAINT_TYPE='CHECK' AND e.n IS NULL;
""",
        # InnoDB creates supporting FK indexes automatically. List unnamed extras
        # for human review rather than incorrectly marking valid ones as failure.
        cte("indexes") + """SELECT 'UNEXPECTED_INDEX_REVIEW' AS audit, a.TABLE_NAME, a.INDEX_NAME, a.NON_UNIQUE, a.COLUMN_NAME
FROM information_schema.STATISTICS a LEFT JOIN expected e ON a.TABLE_NAME=e.t AND a.INDEX_NAME=e.n
WHERE a.TABLE_SCHEMA=DATABASE() AND e.n IS NULL ORDER BY a.TABLE_NAME,a.INDEX_NAME,a.SEQ_IN_INDEX;
SELECT COUNT(*) AS actual_base_tables, IF(@r2_stage=1,18,29) AS expected_base_tables
FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';
"""]
    return header + "\n".join(declarations) + "\n".join(queries)


if __name__ == "__main__":
    manifests = parse()
    entity_fields = audit_entities(manifests[1], manifests[2])
    output = validation_sql(manifests)
    if "--emit-validation" in sys.argv:
        print(output, end="")
    else:
        assert (HERE / "validate_schema.sql").read_text(encoding="utf-8") == output, "Validation manifest out of date"
        print(f"PASS static: main=18 research=11 columns={len(manifests[1])} Entity_fields={entity_fields} indexes={len(manifests[2])} FK={len(manifests[3])} CHECK={len(manifests[4])}")
        print("NOT TESTED: real MySQL parsing, execution, constraints, integration")
