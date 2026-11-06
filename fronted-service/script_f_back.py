cd /home/maksim147/MPT/3/jspring/pract9 

REPORT="$HOME/Downloads/backend-test-report.txt" python3 - <<'PY'
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys

ROOT = Path.cwd().resolve()
FRONT = ROOT / "frontend-service"
REPORT_RAW = os.environ.get("REPORT", "").strip()

if not FRONT.is_dir():
    sys.exit("Не найдена папка frontend-service. Запусти команду из корня репозитория.")
if not REPORT_RAW:
    sys.exit('Укажи путь к отчёту, например: REPORT="$HOME/Downloads/backend-test-report.pdf"')

REPORT = Path(REPORT_RAW).expanduser()
if not REPORT.is_absolute():
    REPORT = ROOT / REPORT
if not REPORT.is_file():
    sys.exit(f"Не найден файл отчёта: {REPORT}")

OUT = ROOT / "colizant-audit-export"
OUT.mkdir(exist_ok=True)

SKIP_DIRS = {
    ".git", ".idea", ".vscode", "target", "build", "dist",
    "node_modules", ".gradle", "coverage", "out"
}
SKIP_SUFFIXES = {
    ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore",
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp",
    ".woff", ".woff2", ".ttf", ".otf", ".eot",
    ".mp3", ".mp4", ".zip", ".gz", ".jar", ".war", ".class",
    ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf"
}

SECRET_LINE = re.compile(
    r"""(?im)^(\s*["']?[\w.\-]*(?:password|passwd|secret|token|api[-_]?key|client[-_]?secret|access[-_]?key)[\w.\-]*["']?\s*[:=]\s*).*$"""
)

def ignored(path, base):
    rel = path.relative_to(base)
    if any(part.lower() in SKIP_DIRS for part in rel.parts[:-1]):
        return True
    name = path.name.lower()
    if name.startswith(".env") or name in {"id_rsa", "id_ed25519"}:
        return True
    if path.suffix.lower() in SKIP_SUFFIXES:
        return True
    return False

def read_text(path):
    if path.suffix.lower() in SKIP_SUFFIXES:
        return None
    try:
        raw = path.read_bytes()
    except OSError:
        return None
    if b"\x00" in raw:
        return None
    for encoding in ("utf-8-sig", "cp1251"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            pass
    return None

def redact(text):
    text = SECRET_LINE.sub(r"\1<REDACTED>", text)
    text = re.sub(r"(?i)\bBearer\s+\S+", "Bearer <REDACTED>", text)
    return text

def split_text(text, limit=10000):
    parts, current = [], ""
    for line in text.splitlines(keepends=True):
        if len(line) > limit:
            if current:
                parts.append(current)
                current = ""
            while line:
                parts.append(line[:limit])
                line = line[limit:]
        elif len(current) + len(line) > limit:
            parts.append(current)
            current = line
        else:
            current += line
    if current:
        parts.append(current)
    return parts or [""]

def write_parts(prefix, text):
    for old in OUT.glob(f"{prefix}-part-*.txt"):
        old.unlink()
    parts = split_text(text)
    for i, part in enumerate(parts, 1):
        header = f"=== {prefix} PART {i}/{len(parts)} ===\n\n"
        (OUT / f"{prefix}-part-{i:03}.txt").write_text(
            header + part, encoding="utf-8"
        )
    return len(parts)

# Собираем список файлов frontend-service, исключая сборки и секреты.
frontend_files = sorted(
    (
        p for p in FRONT.rglob("*")
        if p.is_file() and not ignored(p, FRONT)
    ),
    key=lambda p: p.as_posix()
)

# Добавляем корневые Maven-файлы и README, если проект многомодульный.
root_files = []
for pattern in ("pom.xml", "README*", "mvnw", "mvnw.cmd", ".gitignore"):
    root_files.extend(
        p for p in ROOT.glob(pattern)
        if p.is_file() and not ignored(p, ROOT)
    )
wrapper_properties = ROOT / ".mvn" / "wrapper" / "maven-wrapper.properties"
if wrapper_properties.is_file():
    root_files.append(wrapper_properties)
root_files = sorted(set(root_files), key=lambda p: p.as_posix())

tree = "\n".join(
    f"  {p.relative_to(ROOT).as_posix()}"
    for p in frontend_files
)

sections = [
    f"# Colizant frontend audit bundle — repository: {ROOT.name}",
    "# Бинарные ресурсы перечислены в дереве, но не включены в текст.",
    "# Значения похожих на секреты настроек автоматически замаскированы.",
    "\n=== FRONTEND FILE TREE ===\n" + tree,
]

for path in root_files + frontend_files:
    text = read_text(path)
    if text is None:
        continue
    sections.append(
        f"\n\n===== FILE: {path.relative_to(ROOT).as_posix()} =====\n"
        + redact(text)
    )

source_bundle = "\n".join(sections)
(OUT / "frontend-source-bundle.txt").write_text(
    source_bundle, encoding="utf-8"
)

# Извлекаем текст отчёта; PDF читается через pdftotext.
if REPORT.suffix.lower() == ".pdf":
    if not shutil.which("pdftotext"):
        sys.exit(
            "Для PDF установи pdftotext (Ubuntu/Debian: "
            "sudo apt install poppler-utils) и запусти команду снова."
        )
    result = subprocess.run(
        ["pdftotext", "-layout", str(REPORT), "-"],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        sys.exit("Не удалось прочитать PDF-отчёт: " + result.stderr)
    report_text = result.stdout
else:
    report_text = read_text(REPORT)
    if report_text is None:
        sys.exit(
            "Отчёт должен быть текстовым файлом или PDF. "
            "Для PDF укажи путь к самому PDF."
        )

report_bundle = (
    f"=== BACKEND TEST REPORT: {REPORT.name} ===\n\n"
    + redact(report_text)
)
(OUT / "backend-report-for-chat.txt").write_text(
    report_bundle, encoding="utf-8"
)

source_parts = write_parts("frontend-source", source_bundle)
report_parts = write_parts("backend-report", report_bundle)

print(f"Готово. Результаты: {OUT}")
print(f"Отчёт разбит на частей: {report_parts}")
print(f"Frontend разбит на частей: {source_parts}")
print("Отправляй части с номерами по порядку.")
PY
