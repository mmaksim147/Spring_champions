cd /home/maksim147/MPT/3/jspring/pract9 || exit 1

python3 - <<'PY'
from pathlib import Path
import re
import sys

ROOT = Path.cwd()
FRONT = ROOT / "fronted-service"
OUT = Path("/tmp/colizant-audit-export-v2")

if not FRONT.is_dir():
    sys.exit(f"Не найдена папка: {FRONT}")

SKIP_DIRS = {
    ".git", ".idea", ".vscode", "target", "build", "dist",
    "node_modules", ".gradle", "coverage", "out"
}
SKIP_NAMES = {
    "project-source.txt", "project_dump.txt", "script_f_back.py"
}
TEXT_SUFFIXES = {
    ".java", ".html", ".htm", ".xhtml", ".css", ".scss", ".js",
    ".ts", ".tsx", ".vue", ".json", ".xml", ".properties",
    ".yml", ".yaml", ".md", ".sql", ".txt", ".py", ".sh",
    ".svg", ".conf", ".ftl", ".gradle", ".toml"
}
SPECIAL_NAMES = {
    ".gitignore", ".dockerignore", "Dockerfile"
}

def is_safe_path(path, base):
    rel = path.relative_to(base)
    if any(part in SKIP_DIRS for part in rel.parts[:-1]):
        return False
    name = path.name.lower()
    if name in SKIP_NAMES or name.startswith(".env"):
        return False
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in SPECIAL_NAMES

def read_text(path):
    try:
        data = path.read_bytes()
    except OSError:
        return None
    if b"\x00" in data:
        return None
    for encoding in ("utf-8-sig", "cp1251"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            pass
    return None

secret_line = re.compile(
    r"""(?im)^(\s*["']?[\w.\-]*(?:password|passwd|secret|token|api[-_]?key|client[-_]?secret|access[-_]?key)[\w.\-]*["']?\s*[:=]\s*).*$"""
)

def redact(text):
    text = secret_line.sub(r"\1<REDACTED>", text)
    return re.sub(r"(?i)\bBearer\s+\S+", "Bearer <REDACTED>", text)

frontend_files = sorted(
    (
        p for p in FRONT.rglob("*")
        if p.is_file() and is_safe_path(p, FRONT)
    ),
    key=lambda p: p.as_posix()
)

root_files = []
for p in ROOT.iterdir():
    if p.is_file() and (
        p.name in {"pom.xml", ".gitignore", ".dockerignore"}
        or p.name.lower().startswith("readme")
    ):
        root_files.append(p)

# Сами скрипты показывают проверяемые API-запросы, но не их результаты.
root_files.extend(p for p in ROOT.glob("test*.sh") if p.is_file())
root_files = sorted(set(root_files), key=lambda p: p.as_posix())

sections = [
    "=== FRONTEND FILE LIST ===\n"
    + "\n".join(p.relative_to(ROOT).as_posix() for p in frontend_files),
    "\n=== ROOT CONFIGURATION AND BACKEND TEST SCRIPTS ==="
]

for path in root_files + frontend_files:
    text = read_text(path)
    if text is None:
        continue
    sections.append(
        f"\n\n===== FILE: {path.relative_to(ROOT).as_posix()} =====\n"
        + redact(text)
    )

bundle = "\n".join(sections)

# Делим примерно по 9000 символов, стараясь не разрывать строки.
max_chars = 9000
chunks = []
start = 0
while start < len(bundle):
    end = min(start + max_chars, len(bundle))
    if end < len(bundle):
        newline = bundle.rfind("\n", start + max_chars // 2, end)
        if newline > start:
            end = newline + 1
    chunks.append(bundle[start:end])
    start = end

OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("part-*.txt"):
    old.unlink()

for index, chunk in enumerate(chunks, 1):
    path = OUT / f"part-{index:03}.txt"
    path.write_text(
        f"=== ЧАСТЬ {index}/{len(chunks)} ===\n\n{chunk}",
        encoding="utf-8"
    )

print(f"Собрано файлов frontend: {len(frontend_files)}")
print(f"Файлов конфигурации и тестовых скриптов: {len(root_files)}")
print(f"Частей: {len(chunks)}")
print(f"Результат: {OUT}")
for path in sorted(OUT.glob("part-*.txt")):
    print(f"  {path}")
PY
