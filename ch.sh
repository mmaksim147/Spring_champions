
python3 - <<'PY'
from pathlib import Path
import re

base = Path("fronted-service/src/test/java/org/example/frontendservice/controller")

files = {
    "AdminAccessTest.java": "create",
    "AdminCategoryOperationsTest.java": "create",
    "AdminValidationTest.java": "create",
    "CatalogBuilderControllerTest.java": "saveBuild",
}

import_line = (
    "import org.springframework.web.servlet.mvc.support."
    "RedirectAttributesModelMap;"
)

def find_closing_parenthesis(text, start):
    depth = 0
    in_string = False
    escaped = False

    for i in range(start, len(text)):
        c = text[i]

        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
            continue

        if c == '"':
            in_string = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i

    raise ValueError("Не найдена закрывающая скобка")

def count_arguments(text):
    if not text.strip():
        return 0

    depth = 0
    commas = 0
    in_string = False
    escaped = False

    for c in text:
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
            continue

        if c == '"':
            in_string = True
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            commas += 1

    return commas + 1

for filename, method in files.items():
    path = base / filename

    if not path.exists():
        print(f"НЕ НАЙДЕН: {path}")
        continue

    original = path.read_text(encoding="utf-8")
    content = original

    pattern = re.compile(
    r"\b(?:controller|categoryController|builderController|categories|builder)"
    r"\s*\.\s*" + re.escape(method) + r"\s*\("
)

    matches = list(pattern.finditer(content))
    expected = 2 if method == "create" else 4
    changed = 0

    for match in reversed(matches):
        opening = content.find("(", match.start())
        closing = find_closing_parenthesis(content, opening)

        args = content[opening + 1:closing]

        if count_arguments(args) != expected:
            continue

        replacement = (
            args.rstrip()
            + ", new RedirectAttributesModelMap()"
        )

        content = (
            content[:opening + 1]
            + replacement
            + content[closing:]
        )

        changed += 1

    if changed:
        if import_line not in content:
            package_match = re.search(
                r"^package\s+[\w.]+;\s*",
                content,
                re.MULTILINE
            )

            if not package_match:
                raise ValueError(f"Нет package в {filename}")

            pos = package_match.end()
            content = (
                content[:pos]
                + "\n" + import_line + "\n"
                + content[pos:]
            )

        backup = path.with_suffix(".java.bak")
        backup.write_text(original, encoding="utf-8")
        path.write_text(content, encoding="utf-8")

    print(f"{filename}: исправлено вызовов — {changed}")

print("\nГотово. Резервные копии: *.java.bak")
PY

