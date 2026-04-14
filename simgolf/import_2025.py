#!/usr/bin/env python3
"""
Import 2025 SIM Golf League data from Excel into PostgreSQL.
Reads: SIM League 2025.xlsx (in same directory)
Connects to: 192.168.86.36:5432/app_simgolf_db

Does NOT overwrite 2026 season data. Adds:
  - Jordan as a new player (replacing Michael for 2025)
  - 2025 season with 3 tournaments
  - 7 new courses (reuses Paynes Valley, TPC Scottsdale, Cabot Cliff from 2026)
  - All rounds, scores, handicaps, CTP data, and prize winnings
"""

import os
import sys
import zipfile
import xml.etree.ElementTree as ET
import re
import psycopg2

# ─── DB connection ────────────────────────────────────────────────────────────
conn = psycopg2.connect(
    host=os.environ.get('DB_HOST', '192.168.86.36'),
    port=5432,
    dbname=os.environ.get('DB_NAME', 'app_simgolf_db'),
    user=os.environ.get('DB_USER', 'simgolf_usr'),
    password=os.environ['DB_PASS'],
)
conn.autocommit = True
cur = conn.cursor()

def sql(query, params=None):
    try:
        cur.execute(query, params)
    except Exception as e:
        print(f"SQL ERROR: {e}")
        print(f"Query: {query[:300]}")
        sys.exit(1)

def fetch_one(query, params=None):
    sql(query, params)
    row = cur.fetchone()
    return row[0] if row else None

def fetch_all(query, params=None):
    sql(query, params)
    return cur.fetchall()


# ─── Parse Excel using stdlib (no openpyxl needed) ───────────────────────────
XLSX_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'SIM League 2025.xlsx')
z = zipfile.ZipFile(XLSX_PATH)
ns_xl = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'

# Load shared strings
ss_xml = z.read('xl/sharedStrings.xml')
ss_root = ET.fromstring(ss_xml)
shared_strings = []
for si in ss_root.findall(f'{{{ns_xl}}}si'):
    parts = []
    for t in si.iter(f'{{{ns_xl}}}t'):
        if t.text:
            parts.append(t.text)
    shared_strings.append(''.join(parts))

def col_to_idx(col_str):
    result = 0
    for c in col_str:
        result = result * 26 + (ord(c) - ord('A') + 1)
    return result - 1

def parse_sheet(sheet_file):
    xml_data = z.read(sheet_file)
    root = ET.fromstring(xml_data)
    rows = {}
    max_col = 0
    for row_el in root.findall(f'.//{{{ns_xl}}}row'):
        row_num = int(row_el.get('r'))
        cells = {}
        for cell in row_el.findall(f'{{{ns_xl}}}c'):
            ref = cell.get('r')
            col_str = re.match(r'([A-Z]+)', ref).group(1)
            col_idx = col_to_idx(col_str)
            max_col = max(max_col, col_idx)
            cell_type = cell.get('t', '')
            val_el = cell.find(f'{{{ns_xl}}}v')
            if val_el is not None and val_el.text is not None:
                if cell_type == 's':
                    val = shared_strings[int(val_el.text)]
                else:
                    try:
                        val = float(val_el.text)
                        if val == int(val):
                            val = int(val)
                    except ValueError:
                        val = val_el.text
                cells[col_idx] = val
            else:
                cells[col_idx] = None
        rows[row_num] = cells
    if not rows:
        return []
    max_row = max(rows.keys())
    result = []
    for r in range(1, max_row + 1):
        row_data = []
        for c in range(max_col + 1):
            row_data.append(rows.get(r, {}).get(c))
        result.append(row_data)
    return result

# Parse all needed sheets
wk12_rows = parse_sheet('xl/worksheets/sheet12.xml')    # Wk1and2 (practice)
t1_rows = parse_sheet('xl/worksheets/sheet6.xml')       # Tournament 1 - Table 1
t2_rows = parse_sheet('xl/worksheets/sheet8.xml')       # Tournament 2 - Table 1
t3_rows = parse_sheet('xl/worksheets/sheet10.xml')      # Tournament 3 - Table 1

print("=" * 60)
print("SIM Golf League 2025 Import")
print("=" * 60)

# ─── 1. Add Jordan player ────────────────────────────────────────────────────
print("\n[1] Adding Jordan player...")
existing_jordan = fetch_one("SELECT id FROM players WHERE name = 'Jordan'")
if existing_jordan:
    print(f"  Jordan already exists (id={existing_jordan})")
else:
    sql("INSERT INTO players (name) VALUES ('Jordan')")
    print("  Created Jordan player")

# Build player name → id map
rows_db = fetch_all("SELECT id, name FROM players ORDER BY id")
player_map = {name: pid for pid, name in rows_db}
print(f"  Players: {player_map}")

# 2025 players
PLAYERS_2025 = ['Cody', 'Jordan', 'Lindsay', 'Lance', 'Shea', 'Tim', 'Sonat', 'Jeremy']

# ─── 2. Create 2025 season ──────────────────────────────────────────────────
print("\n[2] Creating 2025 season...")
existing_season = fetch_one("SELECT id FROM seasons WHERE year = 2025")
if existing_season:
    print(f"  2025 season already exists (id={existing_season}), aborting to avoid duplicates!")
    print("  Delete the existing 2025 season first if you want to re-import.")
    conn.close()
    sys.exit(1)

sql("INSERT INTO seasons (year, name) VALUES (2025, '2025 Season')")
season_id = fetch_one("SELECT id FROM seasons WHERE year = 2025")
print(f"  Season id: {season_id}")

# Create 3 tournaments
sql("INSERT INTO tournaments (season_id, number, name) VALUES (%s, 1, 'Tournament 1 — Grove XXIII')", (season_id,))
sql("INSERT INTO tournaments (season_id, number, name) VALUES (%s, 2, 'Tournament 2 — Cypress Point')", (season_id,))
sql("INSERT INTO tournaments (season_id, number, name) VALUES (%s, 3, 'Tournament 3 — Mattaponi Springs')", (season_id,))

rows_db = fetch_all("SELECT id, number FROM tournaments WHERE season_id = %s ORDER BY number", (season_id,))
tournament_map = {num: tid for tid, num in rows_db}
print(f"  Tournaments: {tournament_map}")

# ─── 3. Create/reuse courses ────────────────────────────────────────────────
print("\n[3] Setting up courses...")

# Course definitions: name → {front_par, back_par, front_yds, back_yds}
# Yardages are for par-3 holes (from CTP yardage rows in the spreadsheet)
courses_data = {
    'Grove XXIII': {
        'front': [4, 4, 3, 5, 4, 4, 3, 5, 4],
        'back':  [4, 5, 4, 3, 4, 3, 4, 5, 4],
        'front_yds': [0, 0, 210, 0, 0, 0, 155, 0, 0],
        'back_yds':  [0, 0, 0, 193, 0, 126, 0, 0, 0],
    },
    'TPC Scottsdale': {
        'front': [4, 4, 5, 3, 4, 4, 3, 4, 4],
        'back':  [4, 4, 3, 5, 4, 5, 3, 4, 4],
        'front_yds': [0, 0, 0, 147, 0, 0, 0, 0, 0],
        'back_yds':  [0, 0, 0, 0, 0, 0, 114, 0, 0],
    },
    'Wynn': {
        'front': [4, 3, 5, 4, 3, 4, 3, 5, 4],
        'back':  [3, 5, 3, 5, 4, 4, 4, 4, 3],
        'front_yds': [0, 0, 0, 0, 116, 0, 0, 0, 0],
        'back_yds':  [0, 0, 0, 0, 0, 0, 0, 0, 0],
    },
    'Cypress Point': {
        'front': [4, 5, 3, 4, 5, 5, 3, 4, 4],
        'back':  [5, 4, 4, 4, 4, 3, 3, 4, 4],
        'front_yds': [0, 0, 156, 0, 0, 0, 166, 0, 0],
        'back_yds':  [0, 0, 0, 0, 0, 121, 217, 0, 0],
    },
    'The Dunes': {
        'front': [4, 4, 4, 5, 3, 4, 4, 5, 3],
        'back':  [4, 4, 3, 5, 4, 5, 4, 3, 4],
        'front_yds': [0, 0, 0, 0, 131, 0, 0, 0, 119],
        'back_yds':  [0, 0, 144, 0, 0, 0, 0, 139, 0],
    },
    'Whisler Nickalaus': {
        'front': [4, 3, 5, 4, 4, 3, 4, 5, 4],
        'back':  [3, 5, 3, 5, 4, 4, 4, 3, 4],
        'front_yds': [0, 162, 0, 0, 0, 130, 0, 0, 0],
        'back_yds':  [137, 0, 167, 0, 0, 0, 0, 186, 0],
    },
    'Mattaponi Springs': {
        'front': [4, 5, 3, 4, 4, 5, 3, 4, 4],
        'back':  [4, 4, 5, 4, 3, 4, 5, 3, 4],
        'front_yds': [0, 0, 115, 0, 0, 0, 158, 0, 0],
        'back_yds':  [0, 0, 0, 0, 189, 0, 0, 136, 0],
    },
    'Augusta': {
        'front': [4, 5, 4, 3, 4, 3, 4, 5, 4],
        'back':  [4, 4, 3, 5, 4, 5, 3, 4, 4],
        'front_yds': [0, 0, 0, 226, 0, 173, 0, 0, 0],
        'back_yds':  [0, 0, 144, 0, 0, 0, 155, 0, 0],
    },
    'Cabot Cliff': {
        'front': [5, 4, 4, 3, 4, 3, 5, 5, 3],
        'back':  [5, 4, 3, 4, 3, 5, 3, 4, 5],
        'front_yds': [0, 0, 0, 0, 0, 153, 0, 0, 109],
        'back_yds':  [0, 0, 200, 0, 0, 0, 125, 0, 0],
    },
}

# Also need Paynes Valley for practice (reuse from 2026)
# Paynes Valley pars: front [4,3,4,5,3,4,4,5,4], back [3,4,4,5,4,4,3,4,5]

course_map = {}

def get_or_create_course(name, data):
    """Reuse existing course if found, otherwise create new one."""
    existing_id = fetch_one("SELECT id FROM courses WHERE name = %s", (name,))
    if existing_id:
        print(f"  Reusing {name} (id={existing_id})")
        return existing_id

    sql("INSERT INTO courses (name) VALUES (%s)", (name,))
    cid = fetch_one("SELECT id FROM courses WHERE name = %s", (name,))

    # Front 9 (holes 1-9)
    for i, par in enumerate(data['front']):
        yds = data['front_yds'][i]
        sql("INSERT INTO holes (course_id, hole_number, par, yardage) VALUES (%s, %s, %s, %s)",
            (cid, i + 1, par, yds))
    # Back 9 (holes 10-18)
    for i, par in enumerate(data['back']):
        yds = data['back_yds'][i]
        sql("INSERT INTO holes (course_id, hole_number, par, yardage) VALUES (%s, %s, %s, %s)",
            (cid, i + 10, par, yds))
    print(f"  Created {name} (id={cid})")
    return cid

for name, data in courses_data.items():
    course_map[name] = get_or_create_course(name, data)

# Also ensure Paynes Valley exists (for practice rounds)
pv_id = fetch_one("SELECT id FROM courses WHERE name = 'Paynes Valley'")
if pv_id:
    course_map['Paynes Valley'] = pv_id
    print(f"  Reusing Paynes Valley (id={pv_id})")
else:
    print("  WARNING: Paynes Valley not found! Creating it...")
    pv_data = {
        'front': [4, 3, 4, 5, 3, 4, 4, 5, 4],
        'back':  [3, 4, 4, 5, 4, 4, 3, 4, 5],
        'front_yds': [0] * 9,
        'back_yds':  [0] * 9,
    }
    course_map['Paynes Valley'] = get_or_create_course('Paynes Valley', pv_data)


# ─── 4. Parse scores from Excel ─────────────────────────────────────────────
print("\n[4] Parsing Excel data...")

# Column layout for tournament sheets:
# Col 0: Player/course name
# Cols 1-9: holes 1-9 (front)
# Col 10: Total (front)
# Col 11: Handicap
# Col 12: Score (adjusted)
# Cols 13-21: holes 10-18 (back)
# Col 22: Total (back)
# Col 23: Handicap
# Col 24: Score (adjusted)

def parse_practice(rows):
    """Parse Wk1and2 sheet: practice rounds on Paynes Valley."""
    # Row 0: header (course, holes 1-9, Total, HC, holes 10-18, Total, HC)
    # Row 1: Par
    # Row 2: Player header
    # Rows 3+: alternating player scores and Adjusted HC rows
    players_data = {}
    i = 3
    while i < len(rows):
        row = rows[i]
        if row[0] is None:
            break
        name = row[0]
        if name == 'Adjusted HC':
            i += 1
            continue
        if name in PLAYERS_2025:
            front = [row[j] for j in range(1, 10)]
            back = [row[j] for j in range(12, 21)]
            players_data[name] = {'front': front, 'back': back}
        i += 1
    return players_data


def parse_tournament_sheet(rows):
    """Parse a tournament Table 1 sheet.

    Returns list of:
      (course_name, nine, player_scores, ctp_yardage_row)
    where ctp_yardage_row is the raw row after scores with yardage strings.
    """
    rounds = []
    i = 0
    while i < len(rows):
        row = rows[i]
        # Detect course header: col 0 is string, col 1=1, col 2=2
        if (row[0] and isinstance(row[0], str) and
            row[1] == 1 and row[2] == 2):

            course_name = row[0]

            # Skip Par row and Player header
            j = i + 2
            while j < len(rows) and rows[j][0] in ('Player', 'Par'):
                j += 1

            front_scores = {}
            back_scores = {}
            last_player_j = j

            while j < len(rows):
                prow = rows[j]
                # Check for next course header (col 1=1, col 2=2)
                if (prow[0] and isinstance(prow[0], str) and
                    prow[1] == 1 and prow[2] == 2):
                    break
                pname = prow[0]
                if pname is None or pname == '':
                    j += 1
                    continue
                if not isinstance(pname, str):
                    j += 1
                    continue
                if pname in ('Adjusted HC', 'Closest Too'):
                    j += 1
                    continue
                if pname in PLAYERS_2025:
                    front = [prow[k] for k in range(1, 10)]
                    back = [prow[k] for k in range(13, 22)]
                    front_scores[pname] = front
                    back_scores[pname] = back
                    last_player_j = j
                j += 1

            # Look for CTP yardage row between last player and next course/end
            ctp_row = None
            for k in range(last_player_j + 1, j):
                if k < len(rows):
                    candidate = rows[k]
                    if any(isinstance(v, str) and 'yrd' in v for v in candidate if v):
                        ctp_row = candidate
                        break

            if front_scores:
                rounds.append((course_name, 'front', front_scores, ctp_row))
            if back_scores and any(
                any(v is not None and v != 0 for v in scores)
                for scores in back_scores.values()
            ):
                rounds.append((course_name, 'back', back_scores, ctp_row))

            i = j  # Continue at next course header (don't skip it)
        else:
            i += 1

    return rounds


def extract_ctp_hole(ctp_row, nine):
    """Extract CTP hole number and yardage from the CTP yardage row."""
    if not ctp_row:
        return None, None

    if nine == 'front':
        # Check cols 1-9 for yardage strings
        for col in range(1, 10):
            if col < len(ctp_row) and isinstance(ctp_row[col], str) and 'yrd' in ctp_row[col]:
                yds = int(ctp_row[col].replace(' yrd', '').strip())
                return col, yds  # col index = hole number
    else:
        # Check cols 13-21 for yardage strings (hole 10-18)
        for col in range(13, 22):
            if col < len(ctp_row) and isinstance(ctp_row[col], str) and 'yrd' in ctp_row[col]:
                yds = int(ctp_row[col].replace(' yrd', '').strip())
                hole_num = col - 3  # col 13 = hole 10, col 14 = hole 11, etc.
                return hole_num, yds

    return None, None


def parse_ctp_section(rows, start_row):
    """Parse the CTP section from tournament summary (rows 12-20, cols 19-25).

    Returns dict: week_num (1-6) → {player_name: distance_feet}
    """
    ctp_data = {}
    # Row at start_row has headers: Player, Week 1, ..., Week 6
    for i in range(start_row + 1, start_row + 9):
        if i >= len(rows):
            break
        row = rows[i]
        pname = row[19] if len(row) > 19 else None
        if not pname or pname not in PLAYERS_2025:
            continue
        for wk in range(1, 7):
            col = 19 + wk  # col 20 = wk1, col 21 = wk2, etc.
            if col < len(row):
                val = row[col]
                if val is None or val == '-':
                    continue
                if val == 'winner':
                    # CTP winner with no distance recorded
                    ctp_data.setdefault(wk, {})[pname] = 'winner'
                elif isinstance(val, str):
                    # Handle European decimal like "0,9"
                    try:
                        dist = float(val.replace(',', '.'))
                        ctp_data.setdefault(wk, {})[pname] = dist
                    except ValueError:
                        pass
                elif isinstance(val, (int, float)):
                    ctp_data.setdefault(wk, {})[pname] = float(val)
    return ctp_data


# Parse practice rounds
practice_scores = parse_practice(wk12_rows)
print(f"  Practice players: {list(practice_scores.keys())}")

# Parse tournament rounds
t1_parsed = parse_tournament_sheet(t1_rows)
t2_parsed = parse_tournament_sheet(t2_rows)
t3_parsed = parse_tournament_sheet(t3_rows)

print(f"  T1 rounds: {[(r[0], r[1]) for r in t1_parsed]}")
print(f"  T2 rounds: {[(r[0], r[1]) for r in t2_parsed]}")
print(f"  T3 rounds: {[(r[0], r[1]) for r in t3_parsed]}")

# Parse CTP sections from tournament summary areas (row 12 has headers)
t1_ctp = parse_ctp_section(t1_rows, 12)
t2_ctp = parse_ctp_section(t2_rows, 12)
t3_ctp = parse_ctp_section(t3_rows, 12)

print(f"  T1 CTP weeks with data: {list(t1_ctp.keys())}")
print(f"  T2 CTP weeks with data: {list(t2_ctp.keys())}")
print(f"  T3 CTP weeks with data: {list(t3_ctp.keys())}")

# ─── Course name normalization ────────────────────────────────────────────────
COURSE_NAME_FIX = {
    'TPC Scottdale': 'TPC Scottsdale',
    'TPC Scottdale ': 'TPC Scottsdale',
}

def normalize_course(name):
    return COURSE_NAME_FIX.get(name, name)


# ─── 5. Create rounds and insert scores ─────────────────────────────────────
print("\n[5] Creating rounds and inserting scores...")

from datetime import date, timedelta

start_date = date(2025, 1, 4)
week_num = 0

def next_date():
    global week_num
    d = start_date + timedelta(weeks=week_num)
    week_num += 1
    return d.isoformat()


def create_round(season_id, tournament_id, round_number, course_name, nine, played_date, is_practice,
                 ctp_hole=None, ctp_yardage=None):
    course_name = normalize_course(course_name)
    cid = course_map[course_name]
    sql("""INSERT INTO rounds (season_id, tournament_id, round_number, course_id, nine, played_date,
            is_practice, ctp_hole, ctp_yardage, ctp_prize_amount, chip_in_pot)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 20.00, 0.00)""",
        (season_id, tournament_id, round_number, cid, nine, played_date, is_practice, ctp_hole, ctp_yardage))
    rid = fetch_one(
        "SELECT id FROM rounds WHERE season_id=%s AND round_number=%s AND nine=%s AND course_id=%s",
        (season_id, round_number, nine, cid)
    )
    return rid


def insert_scores(round_id, nine, player_scores):
    """Insert per-hole scores. nine='front' → holes 1-9, 'back' → holes 10-18."""
    offset = 0 if nine == 'front' else 9
    for pname, hole_scores in player_scores.items():
        if pname not in player_map:
            continue
        pid = player_map[pname]
        for i, strokes in enumerate(hole_scores):
            if strokes is not None and strokes != 0 and isinstance(strokes, (int, float)):
                hnum = i + 1 + offset
                s = int(strokes)
                sql("""INSERT INTO scores (round_id, player_id, hole_number, strokes)
                        VALUES (%s, %s, %s, %s)
                        ON CONFLICT (round_id, player_id, hole_number) DO UPDATE SET strokes = EXCLUDED.strokes""",
                    (round_id, pid, hnum, s))


round_number = 1
round_id_map = {}  # (tournament_num_or_'practice', week_num) → round_id

# ── Practice rounds (Paynes Valley) ──
print("  Practice rounds (Paynes Valley)...")
rid = create_round(season_id, None, round_number, 'Paynes Valley', 'front', next_date(), True)
insert_scores(rid, 'front', {k: v['front'] for k, v in practice_scores.items()})
round_id_map[('practice', 1)] = rid
print(f"    Rd{round_number} Paynes Valley front (id={rid})")
round_number += 1

rid = create_round(season_id, None, round_number, 'Paynes Valley', 'back', next_date(), True)
insert_scores(rid, 'back', {k: v['back'] for k, v in practice_scores.items()})
round_id_map[('practice', 2)] = rid
print(f"    Rd{round_number} Paynes Valley back (id={rid})")
round_number += 1

# ── Tournament 1 rounds ──
t1_id = tournament_map[1]
print("  T1 rounds...")
t1_week = 0
for course_name, nine, player_scores, ctp_row in t1_parsed:
    t1_week += 1
    ctp_h, ctp_y = extract_ctp_hole(ctp_row, nine)
    cn = normalize_course(course_name)
    rid = create_round(season_id, t1_id, round_number, course_name, nine, next_date(), False, ctp_h, ctp_y)
    insert_scores(rid, nine, player_scores)
    round_id_map[(1, t1_week)] = rid
    print(f"    Rd{round_number} {cn} {nine} (id={rid}) CTP: hole {ctp_h} @ {ctp_y}yrd")
    round_number += 1

# ── Tournament 2 rounds ──
t2_id = tournament_map[2]
print("  T2 rounds...")
t2_week = 0
for course_name, nine, player_scores, ctp_row in t2_parsed:
    t2_week += 1
    ctp_h, ctp_y = extract_ctp_hole(ctp_row, nine)
    cn = normalize_course(course_name)
    rid = create_round(season_id, t2_id, round_number, course_name, nine, next_date(), False, ctp_h, ctp_y)
    insert_scores(rid, nine, player_scores)
    round_id_map[(2, t2_week)] = rid
    print(f"    Rd{round_number} {cn} {nine} (id={rid}) CTP: hole {ctp_h} @ {ctp_y}yrd")
    round_number += 1

# ── Tournament 3 rounds ──
t3_id = tournament_map[3]
print("  T3 rounds...")
t3_week = 0
for course_name, nine, player_scores, ctp_row in t3_parsed:
    t3_week += 1
    ctp_h, ctp_y = extract_ctp_hole(ctp_row, nine)
    cn = normalize_course(course_name)
    rid = create_round(season_id, t3_id, round_number, course_name, nine, next_date(), False, ctp_h, ctp_y)
    insert_scores(rid, nine, player_scores)
    round_id_map[(3, t3_week)] = rid
    print(f"    Rd{round_number} {cn} {nine} (id={rid}) CTP: hole {ctp_h} @ {ctp_y}yrd")
    round_number += 1


# ─── 6. Insert handicaps ────────────────────────────────────────────────────
print("\n[6] Inserting handicaps...")

# T1 handicaps from Handicap Summary sheet
handicaps = {
    1: {'Cody': 25.5, 'Jordan': 10.5, 'Lindsay': 7, 'Lance': 22, 'Shea': 12.5, 'Tim': 12, 'Sonat': 12.5, 'Jeremy': 23},
    # T2 handicaps from Cypress Point scores (first T2 course)
    2: {'Cody': 24, 'Jordan': 14, 'Lindsay': 6, 'Lance': 24, 'Shea': 8, 'Tim': 9, 'Sonat': 11, 'Jeremy': 25},
    # T3 handicaps from Mattaponi Springs scores (first T3 course)
    3: {'Cody': 24.29, 'Jordan': 12.68, 'Lindsay': 7.47, 'Lance': 21.56, 'Shea': 7.38, 'Tim': 7.76, 'Sonat': 10.23, 'Jeremy': 24.96},
}

for t_num, hcaps in handicaps.items():
    t_id = tournament_map[t_num]
    for pname, hval in hcaps.items():
        if pname not in player_map:
            continue
        pid = player_map[pname]
        sql("""INSERT INTO handicaps (player_id, season_id, tournament_number, value)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (player_id, season_id, tournament_number) DO UPDATE SET value = EXCLUDED.value""",
            (pid, season_id, t_num, hval))
    print(f"  T{t_num}: {hcaps}")


# ─── 7. Insert CTP (closest to pin) entries ─────────────────────────────────
print("\n[7] Inserting closest-to-pin entries...")

def insert_ctp_entries(tournament_num, ctp_data):
    """Insert CTP entries for a tournament.
    ctp_data: {week_num: {player_name: distance_or_'winner'}}
    """
    for wk, entries in sorted(ctp_data.items()):
        rid = round_id_map.get((tournament_num, wk))
        if not rid:
            print(f"    WARNING: No round found for T{tournament_num} Wk{wk}")
            continue

        # Determine winner (smallest distance)
        distances = {}
        has_winner_tag = False
        winner_name = None
        for pname, val in entries.items():
            if val == 'winner':
                has_winner_tag = True
                winner_name = pname
            else:
                distances[pname] = val

        if has_winner_tag and winner_name:
            # Player tagged as 'winner' - insert with distance 0 and won=true
            pid = player_map.get(winner_name)
            if pid:
                sql("""INSERT INTO closest_to_pin (round_id, player_id, distance_feet, won)
                        VALUES (%s, %s, %s, %s)""", (rid, pid, 0, True))
                print(f"    T{tournament_num}W{wk}: {winner_name} = winner (tagged)")

            # Insert other entries as non-winners
            for pname, dist in distances.items():
                pid = player_map.get(pname)
                if pid:
                    sql("""INSERT INTO closest_to_pin (round_id, player_id, distance_feet, won)
                            VALUES (%s, %s, %s, %s)""", (rid, pid, dist, False))
        elif distances:
            # Find closest
            min_dist = min(distances.values())
            for pname, dist in distances.items():
                pid = player_map.get(pname)
                if pid:
                    won = (dist == min_dist)
                    sql("""INSERT INTO closest_to_pin (round_id, player_id, distance_feet, won)
                            VALUES (%s, %s, %s, %s)""", (rid, pid, dist, won))
                    if won:
                        print(f"    T{tournament_num}W{wk}: {pname} = {dist}ft (WINNER)")
                    else:
                        print(f"    T{tournament_num}W{wk}: {pname} = {dist}ft")


insert_ctp_entries(1, t1_ctp)
insert_ctp_entries(2, t2_ctp)
insert_ctp_entries(3, t3_ctp)


# ─── 8. Insert prize winnings ────────────────────────────────────────────────
print("\n[8] Inserting prize winnings...")

# Tournament placement prizes (from leaderboard totals)
# T1: Shea 1st (40pts), Tim 2nd (33pts)
# T2: Shea 1st (32.5pts), Tim 2nd (31.5pts)
# T3: Jeremy 1st (41.5pts), Sonat 2nd (31.5pts)
tournament_prizes = [
    (1, 'Shea',   't1st', 200.00, 'Tournament 1 1st place'),
    (1, 'Tim',    't2nd',  50.00, 'Tournament 1 2nd place'),
    (2, 'Shea',   't1st', 200.00, 'Tournament 2 1st place'),
    (2, 'Tim',    't2nd',  50.00, 'Tournament 2 2nd place'),
    (3, 'Jeremy', 't1st', 200.00, 'Tournament 3 1st place'),
    (3, 'Sonat',  't2nd',  50.00, 'Tournament 3 2nd place'),
]

for t_num, pname, ptype, amount, desc in tournament_prizes:
    if pname not in player_map:
        print(f"  SKIP {pname} not in player_map")
        continue
    pid = player_map[pname]
    t_id = tournament_map[t_num]
    sql("""INSERT INTO prize_winnings (player_id, tournament_id, season_id, type, amount, description)
            VALUES (%s, %s, %s, %s, %s, %s)""",
        (pid, t_id, season_id, ptype, amount, desc))
    print(f"  T{t_num} {ptype}: {pname} ${amount}")

# CTP prize winnings - insert for each CTP winner
print("\n  CTP prizes...")
ctp_winners = fetch_all(
    """SELECT ctp.round_id, ctp.player_id, p.name, r.tournament_id
       FROM closest_to_pin ctp
       JOIN players p ON p.id = ctp.player_id
       JOIN rounds r ON r.id = ctp.round_id
       WHERE r.season_id = %s AND ctp.won = true""",
    (season_id,)
)
for rid, pid, pname, tid in ctp_winners:
    sql("""INSERT INTO prize_winnings (player_id, round_id, tournament_id, season_id, type, amount, description)
            VALUES (%s, %s, %s, %s, 'ctp', 20.00, %s)""",
        (pid, rid, tid, season_id, f'Closest to pin - {pname}'))
    print(f"  CTP: {pname} (round {rid})")


# ─── 9. Verification ────────────────────────────────────────────────────────
print("\n" + "=" * 60)
print("Import complete!")
print("=" * 60)

# 2025 stats
cur.execute("SELECT COUNT(*) FROM scores s JOIN rounds r ON r.id = s.round_id WHERE r.season_id = %s", (season_id,))
print(f"\n2025 scores: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM rounds WHERE season_id = %s", (season_id,))
print(f"2025 rounds: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM handicaps WHERE season_id = %s", (season_id,))
print(f"2025 handicaps: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM closest_to_pin ctp JOIN rounds r ON r.id = ctp.round_id WHERE r.season_id = %s", (season_id,))
print(f"2025 CTP entries: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM prize_winnings WHERE season_id = %s", (season_id,))
print(f"2025 prize winnings: {cur.fetchone()[0]}")

# Total stats (all seasons)
cur.execute("SELECT COUNT(*) FROM scores")
print(f"\nTotal scores (all seasons): {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM rounds")
print(f"Total rounds (all seasons): {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM courses")
print(f"Total courses: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM players")
print(f"Total players: {cur.fetchone()[0]}")

conn.close()
