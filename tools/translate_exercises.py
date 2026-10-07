#!/usr/bin/env python3
"""Одноразовый скрипт: собирает app/src/main/assets/exercises_ru.json.

Источник — Free Exercise DB (yuhonas/free-exercise-db, Unlicense):
    app/src/main/assets/exercises.json

Переводы названий и инструкций лежат в tools/translations/*.txt (сделаны
пачками; формат: «@id», затем строка названия, затем строки «- шаг»).
Группы мышц, оборудование, уровни, категории переводятся словарями ниже.
Исходные id сохраняются, чтобы история пользователя не ломалась при обновлении.

Запуск: python3 tools/translate_exercises.py
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/assets/exercises.json")
DST = os.path.join(ROOT, "app/src/main/assets/exercises_ru.json")
TR_DIR = os.path.join(ROOT, "tools/translations")

MUSCLES = {
    "abdominals": "пресс",
    "abductors": "отводящие мышцы бедра",
    "adductors": "приводящие мышцы бедра",
    "biceps": "бицепс",
    "calves": "икры",
    "chest": "грудь",
    "forearms": "предплечья",
    "glutes": "ягодицы",
    "hamstrings": "задняя поверхность бедра",
    "lats": "широчайшие",
    "lower back": "поясница",
    "middle back": "середина спины",
    "neck": "шея",
    "quadriceps": "квадрицепс",
    "shoulders": "плечи",
    "traps": "трапеции",
    "triceps": "трицепс",
}

EQUIPMENT = {
    "bands": "резина",
    "barbell": "штанга",
    "body only": "собственный вес",
    "cable": "блок",
    "dumbbell": "гантели",
    "e-z curl bar": "EZ-гриф",
    "exercise ball": "фитбол",
    "foam roll": "массажный ролик",
    "kettlebells": "гири",
    "machine": "тренажёр",
    "medicine ball": "медбол",
    "other": "другое",
}

LEVELS = {"beginner": "новичок", "intermediate": "средний", "expert": "продвинутый"}
FORCE = {"pull": "тяга", "push": "жим", "static": "статика"}
MECHANIC = {"compound": "базовое", "isolation": "изолирующее"}
CATEGORY = {
    "strength": "силовые",
    "powerlifting": "пауэрлифтинг",
    "olympic weightlifting": "тяжёлая атлетика",
    "strongman": "стронгмен",
    "cardio": "кардио",
    "plyometrics": "плиометрика",
    "stretching": "растяжка",
}
# Категория в приложении
APP_CATEGORY = {
    "strength": "STRENGTH",
    "powerlifting": "STRENGTH",
    "olympic weightlifting": "STRENGTH",
    "strongman": "STRENGTH",
    "cardio": "CARDIO",
    "plyometrics": "CARDIO",
    "stretching": "STRETCHING",
}


def load_translations():
    result = {}
    for path in sorted(glob.glob(os.path.join(TR_DIR, "*.txt"))):
        current = None
        with open(path, encoding="utf-8") as fh:
            for raw in fh:
                line = raw.rstrip("\n")
                if not line.strip():
                    continue
                if line.startswith("@"):
                    current = {"name": None, "instructions": []}
                    result[line[1:].strip()] = current
                elif line.startswith("- "):
                    current["instructions"].append(line[2:].strip())
                elif current is not None and current["name"] is None:
                    current["name"] = line.strip()
                else:
                    raise ValueError(f"{path}: непонятная строка: {line!r}")
    return result


def tr(mapping, value):
    if value is None:
        return None
    if value not in mapping:
        raise KeyError(f"нет перевода для {value!r}")
    return mapping[value]


def main():
    src = json.load(open(SRC, encoding="utf-8"))
    translations = load_translations()
    out = []
    errors = []
    for ex in src:
        t = translations.get(ex["id"])
        if not t or not t["name"]:
            errors.append(f"нет перевода: {ex['id']}")
            continue
        if ex["instructions"] and not t["instructions"]:
            errors.append(f"нет инструкций: {ex['id']}")
        out.append({
            "id": ex["id"],
            "name": t["name"],
            "name_en": ex["name"],
            "force": ex.get("force"),
            "force_ru": tr(FORCE, ex.get("force")),
            "level": ex.get("level"),
            "level_ru": tr(LEVELS, ex.get("level")),
            "mechanic": ex.get("mechanic"),
            "mechanic_ru": tr(MECHANIC, ex.get("mechanic")),
            "equipment": ex.get("equipment"),
            "equipment_ru": tr(EQUIPMENT, ex.get("equipment")),
            "primaryMuscles": ex["primaryMuscles"],
            "primaryMuscles_ru": [tr(MUSCLES, m) for m in ex["primaryMuscles"]],
            "secondaryMuscles": ex["secondaryMuscles"],
            "secondaryMuscles_ru": [tr(MUSCLES, m) for m in ex["secondaryMuscles"]],
            "instructions": t["instructions"],
            "category": ex["category"],
            "category_ru": tr(CATEGORY, ex["category"]),
            "app_category": APP_CATEGORY[ex["category"]],
            "images": ex["images"],
        })
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    with open(DST, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"Готово: {len(out)} упражнений → {os.path.relpath(DST, ROOT)}")


if __name__ == "__main__":
    main()
