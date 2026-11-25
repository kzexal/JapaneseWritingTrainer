import sqlite3, zipfile, zstandard

# Thay 'ten_file.apkg' bằng đường dẫn file của bạn
with zipfile.ZipFile("Japanese Kanji.apkg", "r") as z:
  compressed_data = z.read("collection.anki21b")

dctx = zstandard.ZstdDecompressor()
sqlite_bytes = dctx.decompress(compressed_data)

with open("decompressed.sqlite", "wb") as f:
  f.write(sqlite_bytes)

conn = sqlite3.connect("decompressed.sqlite")
cursor = conn.cursor()

print("--- DANH SÁCH DECK ---")
for row in cursor.execute("SELECT id, name FROM decks"):
  # Thay ký tự phân cấp deck của Anki (\x1f) thành '::'
  print(f"ID: {row[0]} | Tên Deck: {row[1].replace(chr(31), '::')}")

print("\n--- CÁC TRƯỜNG (FIELDS) CỦA NOTE TYPE ---")
for row in cursor.execute("SELECT ntid, ord, name FROM fields"):
  print(f"NoteType ID: {row[0]} | Thứ tự: {row[1]} | Tên trường: {row[2]}")