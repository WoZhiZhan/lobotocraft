from __future__ import annotations

import csv
import hashlib
import re
import subprocess
from pathlib import Path

import UnityPy


GAME_ASSET = Path(
    r"F:\SteamLibrary\steamapps\common\LobotomyCorp\LobotomyCorp_Data\resources.assets"
)
OUTPUT_ROOT = Path(__file__).resolve().parent

BGM = {
    "chesed": ["1_Theme_-_Blues_Man", "2_Battle_-_Urgent_Encounter"],
    "tiphereth": ["1_Eternal", "2_Dark Fantasy Scene"],
    "hokma": [
        "1_Tilarids - 090909090",
        "2_Tilarids - circle-rombed oxygen",
    ],
    "binah": ["1_Jukai", "2_Haunted Streets_1"],
}

# These path IDs were traced from the four CoreAnim prefab roots through their
# renderer/material dependencies. They are the actual in-fight model/effect textures.
CORE_TEXTURE_PATH_IDS = {
    "chesed": {3359},
    "tiphereth": {4661},
    "hokma": {1392},
    "binah": {1249, 2106, 3494, 3515, 4769},
}

UI_TEXTURE_NAMES = {
    "chesed": {
        "Chesed_Frame",
        "ChesedFilter",
        "ChesedTemp",
        "ChesedTempFrame",
        *(f"Chesed_background_{index}" for index in range(1, 6)),
    },
    "tiphereth": {
        "Tiphereth_Frame",
        "TipherethFillter",
        "TipherethCurtain",
        *(f"Tiphereth_background_{index}" for index in range(1, 6)),
    },
    "hokma": {
        "Chokhmah_Frame",
        "Chokhmah_Main",
        "Chokmah_Clock",
        *(f"Chokma_background_{index}" for index in range(1, 6)),
    },
    "binah": {
        "Binah_Frame",
        "Binah_Main",
        "BinahOverload",
        *(f"Binah_background_{index}" for index in range(1, 6)),
    },
}


def safe_name(value: str) -> str:
    return re.sub(r'[<>:"/\\|?*]+', "_", value).strip(" .")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def export_audio(objects, rows: list[dict[str, object]]) -> None:
    by_name: dict[str, list] = {}
    for obj in objects:
        if obj.type.name != "AudioClip":
            continue
        name = obj.peek_name()
        by_name.setdefault(name, []).append(obj)

    for department, names in BGM.items():
        target = OUTPUT_ROOT / department / "audio"
        target.mkdir(parents=True, exist_ok=True)
        for order, name in enumerate(names, start=1):
            candidates = sorted(by_name.get(name, []), key=lambda obj: obj.path_id)
            if not candidates:
                raise RuntimeError(f"AudioClip not found: {name}")
            obj = candidates[0]
            clip = obj.read()
            samples = clip.samples
            if not samples:
                raise RuntimeError(f"AudioClip has no samples: {name}")
            sample_name, sample_data = next(iter(samples.items()))
            wav_path = target / f"{order}_{safe_name(name)}.wav"
            wav_path.write_bytes(sample_data)
            ogg_path = wav_path.with_suffix(".ogg")
            subprocess.run(
                [
                    "ffmpeg",
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    str(wav_path),
                    "-c:a",
                    "libvorbis",
                    "-q:a",
                    "7",
                    str(ogg_path),
                ],
                check=True,
            )
            for kind, path in (("原始PCM WAV", wav_path), ("模组可用OGG", ogg_path)):
                rows.append(
                    {
                        "department": department,
                        "category": "bgm",
                        "kind": kind,
                        "unity_type": obj.type.name,
                        "unity_name": name,
                        "path_id": obj.path_id,
                        "duplicate_path_ids": ",".join(str(item.path_id) for item in candidates[1:]),
                        "file": path.relative_to(OUTPUT_ROOT).as_posix(),
                        "width": "",
                        "height": "",
                        "duration_seconds": f"{clip.m_Length:.3f}",
                        "sha256": sha256(path),
                    }
                )


def export_image(obj, department: str, category: str, rows: list[dict[str, object]]) -> None:
    data = obj.read()
    image = data.image
    name = obj.peek_name() or f"unnamed_{obj.path_id}"
    target = OUTPUT_ROOT / department / "textures" / category
    target.mkdir(parents=True, exist_ok=True)
    path = target / f"{safe_name(name)}__{obj.type.name}_{obj.path_id}.png"
    image.save(path)
    rows.append(
        {
            "department": department,
            "category": category,
            "kind": "PNG",
            "unity_type": obj.type.name,
            "unity_name": name,
            "path_id": obj.path_id,
            "duplicate_path_ids": "",
            "file": path.relative_to(OUTPUT_ROOT).as_posix(),
            "width": image.width,
            "height": image.height,
            "duration_seconds": "",
            "sha256": sha256(path),
        }
    )


def export_images(objects, rows: list[dict[str, object]]) -> None:
    core_owner = {
        path_id: department
        for department, path_ids in CORE_TEXTURE_PATH_IDS.items()
        for path_id in path_ids
    }
    for obj in objects:
        if obj.type.name == "Texture2D" and obj.path_id in core_owner:
            export_image(obj, core_owner[obj.path_id], "core", rows)

        if obj.type.name not in {"Texture2D", "Sprite"}:
            continue
        name = obj.peek_name()
        for department, names in UI_TEXTURE_NAMES.items():
            if name in names:
                export_image(obj, department, "ui_candidates", rows)


def write_index(rows: list[dict[str, object]]) -> None:
    fields = [
        "department",
        "category",
        "kind",
        "unity_type",
        "unity_name",
        "path_id",
        "duplicate_path_ids",
        "file",
        "width",
        "height",
        "duration_seconds",
        "sha256",
    ]
    with (OUTPUT_ROOT / "EXPORT_INDEX.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda row: (str(row["department"]), str(row["file"]))))


def main() -> None:
    environment = UnityPy.load(str(GAME_ASSET))
    rows: list[dict[str, object]] = []
    export_audio(environment.objects, rows)
    export_images(environment.objects, rows)
    write_index(rows)
    print(f"Exported {len(rows)} indexed files to {OUTPUT_ROOT}")


if __name__ == "__main__":
    main()
