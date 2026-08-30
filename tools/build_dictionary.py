#!/usr/bin/env python3
"""
Bilingual Dictionary Database Builder
Builds a compact, indexed SQLite database for English-Chinese and Malay-Chinese dictionary lookups,
including reverse lookups and metadata tables, and compresses it with gzip.
"""

import gzip
import json
import os
import re
import sqlite3
import urllib.request
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "app", "src", "main", "assets")
OUTPUT_DB = os.path.join(BASE_DIR, "dictionary.db")
OUTPUT_GZ = os.path.join(ASSETS_DIR, "dictionary.db.gz")

os.makedirs(ASSETS_DIR, exist_ok=True)
sys.path.insert(0, os.path.join(BASE_DIR, "tools"))


def download_file(url, dest_path, description=""):
    print(f"[*] Downloading {description} from {url}...")
    opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")]
    urllib.request.install_opener(opener)
    try:
        urllib.request.urlretrieve(url, dest_path)
        print(f"[+] Downloaded: {dest_path} ({os.path.getsize(dest_path)} bytes)")
        return True
    except Exception as e:
        print(f"[-] Failed to download from {url}: {e}")
        return False


def get_core_english_data():
    temp_dir = os.path.join(BASE_DIR, "tools", "data")
    os.makedirs(temp_dir, exist_ok=True)
    ecdic_csv = os.path.join(temp_dir, "ecdic.csv")

    ecdic_url = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
    if not os.path.exists(ecdic_csv) or os.path.getsize(ecdic_csv) < 1000:
        success = download_file(ecdic_url, ecdic_csv, "ECDICT core dictionary")
        if not success:
            print("[*] Falling back to built-in curated English dictionary dataset...")
            return None

    return ecdic_csv


def create_schema(cursor):
    cursor.executescript("""
    DROP TABLE IF EXISTS words;
    DROP TABLE IF EXISTS reverse_index;
    DROP TABLE IF EXISTS user_history;
    DROP TABLE IF EXISTS user_favorites;
    DROP TABLE IF EXISTS online_cache;

    CREATE TABLE words (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        word TEXT NOT NULL,          -- lowercased for fast indexed searching
        display_word TEXT NOT NULL,  -- original capitalization
        lang TEXT NOT NULL,          -- 'en' (English) or 'ms' (Malay)
        phonetic TEXT,               -- phonetic transcription / pronunciation
        pos TEXT,                    -- part of speech (n., v., adj., etc.)
        definition TEXT NOT NULL,    -- Chinese explanation
        example TEXT                 -- example sentence / phrases
    );

    CREATE INDEX idx_words_lookup ON words(word, lang);
    CREATE INDEX idx_words_word ON words(word);
    CREATE INDEX idx_words_lang ON words(lang);

    CREATE TABLE reverse_index (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        zh_keyword TEXT NOT NULL,
        target_word TEXT NOT NULL,
        target_lang TEXT NOT NULL,
        definition TEXT NOT NULL
    );

    CREATE INDEX idx_rev_zh ON reverse_index(zh_keyword);
    CREATE INDEX idx_rev_zh_lang ON reverse_index(zh_keyword, target_lang);

    CREATE TABLE user_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        query TEXT NOT NULL,
        lang TEXT NOT NULL,
        timestamp INTEGER NOT NULL
    );

    CREATE TABLE user_favorites (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        word TEXT NOT NULL,
        lang TEXT NOT NULL,
        phonetic TEXT,
        pos TEXT,
        definition TEXT NOT NULL,
        timestamp INTEGER NOT NULL
    );
    CREATE UNIQUE INDEX idx_fav_unique ON user_favorites(word, lang);

    CREATE TABLE online_cache (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        query TEXT NOT NULL,
        source_lang TEXT NOT NULL,
        target_lang TEXT NOT NULL,
        result_text TEXT NOT NULL,
        timestamp INTEGER NOT NULL
    );
    CREATE INDEX idx_cache_query ON online_cache(query, source_lang, target_lang);
    """)


def parse_ecdic_and_insert(conn, cursor, ecdic_path):
    import csv
    print("[*] Processing ECDIC dataset...")
    count = 0
    batch = []
    rev_batch = []
    
    with open(ecdic_path, mode='r', encoding='utf-8', errors='ignore') as f:
        reader = csv.DictReader(f)
        for row in reader:
            word = row.get('word', '').strip()
            if not word or len(word) > 40:
                continue
            
            # Filter to keep core vocabulary with good definitions
            translation = row.get('translation', '').strip()
            if not translation:
                continue
            
            # Check frequency rank (frq or bnc or collins) to keep app small & essential
            # ECDIC collins 1-5 or bnc/frq > 0
            collins = row.get('collins', '0')
            bnc = row.get('bnc', '0')
            frq = row.get('frq', '0')
            oxford = row.get('oxford', '0')
            
            # If word is in common ranges (up to ~45k words)
            is_common = (
                oxford == '1' or 
                collins in ('1','2','3','4','5') or 
                (bnc and bnc != '0' and int(bnc) < 35000) or
                (frq and frq != '0' and int(frq) < 35000)
            )
            
            if not is_common and count > 35000:
                continue

            phonetic = row.get('phonetic', '').strip()
            pos = row.get('pos', '').strip()
            
            # Clean translation (replace \n with real newline or semicolon)
            translation = translation.replace('\\n', '\n')
            
            lower_word = word.lower()
            batch.append((lower_word, word, 'en', phonetic, pos, translation, ''))
            count += 1
            
            # Extract keywords for reverse index (Chinese -> English)
            zh_words = set(re.findall(r'[\u4e00-\u9fa5]{1,6}', translation))
            for zh in zh_words:
                if len(zh) >= 1:
                    rev_batch.append((zh, word, 'en', translation[:100]))
            
            if len(batch) >= 5000:
                cursor.executemany(
                    "INSERT INTO words (word, display_word, lang, phonetic, pos, definition, example) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    batch
                )
                cursor.executemany(
                    "INSERT INTO reverse_index (zh_keyword, target_word, target_lang, definition) VALUES (?, ?, ?, ?)",
                    rev_batch
                )
                conn.commit()
                batch.clear()
                rev_batch.clear()
                
    if batch:
        cursor.executemany(
            "INSERT INTO words (word, display_word, lang, phonetic, pos, definition, example) VALUES (?, ?, ?, ?, ?, ?, ?)",
            batch
        )
        cursor.executemany(
            "INSERT INTO reverse_index (zh_keyword, target_word, target_lang, definition) VALUES (?, ?, ?, ?)",
            rev_batch
        )
        conn.commit()
    print(f"[+] Loaded {count} English-Chinese entries from ECDIC.")


def load_curated_data(conn, cursor):
    from curated_lexicon import get_curated_en_words, get_curated_ms_words
    print("[*] Loading curated bilingual English and Malay dictionaries...")
    
    en_words = get_curated_en_words()
    ms_words = get_curated_ms_words()
    
    batch = []
    rev_batch = []
    
    for item in en_words:
        w = item['word'].strip()
        disp = item.get('display', w)
        pho = item.get('phonetic', '')
        pos = item.get('pos', '')
        defn = item['definition'].strip()
        ex = item.get('example', '')
        batch.append((w.lower(), disp, 'en', pho, pos, defn, ex))
        
        zh_words = set(re.findall(r'[\u4e00-\u9fa5]{1,6}', defn))
        for zh in zh_words:
            rev_batch.append((zh, disp, 'en', defn))

    for item in ms_words:
        w = item['word'].strip()
        disp = item.get('display', w)
        pho = item.get('phonetic', '')
        pos = item.get('pos', '')
        defn = item['definition'].strip()
        ex = item.get('example', '')
        batch.append((w.lower(), disp, 'ms', pho, pos, defn, ex))
        
        zh_words = set(re.findall(r'[\u4e00-\u9fa5]{1,6}', defn))
        for zh in zh_words:
            rev_batch.append((zh, disp, 'ms', defn))
            
    cursor.executemany(
        "INSERT INTO words (word, display_word, lang, phonetic, pos, definition, example) VALUES (?, ?, ?, ?, ?, ?, ?)",
        batch
    )
    cursor.executemany(
        "INSERT INTO reverse_index (zh_keyword, target_word, target_lang, definition) VALUES (?, ?, ?, ?)",
        rev_batch
    )
    conn.commit()
    print(f"[+] Loaded curated entries: {len(en_words)} EN, {len(ms_words)} MS.")


def compress_database():
    print(f"[*] Compressing {OUTPUT_DB} to {OUTPUT_GZ}...")
    with open(OUTPUT_DB, 'rb') as f_in:
        with gzip.open(OUTPUT_GZ, 'wb', compresslevel=9) as f_out:
            while chunk := f_in.read(65536):
                f_out.write(chunk)
    
    orig_size = os.path.getsize(OUTPUT_DB) / (1024 * 1024)
    gz_size = os.path.getsize(OUTPUT_GZ) / (1024 * 1024)
    print(f"[+] Compression complete! Original: {orig_size:.2f} MB -> Gzip: {gz_size:.2f} MB")


def main():
    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)
        
    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()
    create_schema(cursor)
    
    ecdic_path = get_core_english_data()
    if ecdic_path and os.path.exists(ecdic_path) and os.path.getsize(ecdic_path) > 10000:
        parse_ecdic_and_insert(conn, cursor, ecdic_path)
    
    # Always load curated MS and complementary EN words
    load_curated_data(conn, cursor)
    
    print("[*] Optimizing SQLite database (VACUUM & ANALYZE)...")
    cursor.execute("ANALYZE;")
    conn.commit()
    conn.close()
    
    # Re-open for vacuum
    conn = sqlite3.connect(OUTPUT_DB)
    conn.execute("VACUUM;")
    conn.close()
    
    compress_database()
    print("[+] Dictionary asset is ready in app/src/main/assets/dictionary.db.gz")


if __name__ == "__main__":
    main()
