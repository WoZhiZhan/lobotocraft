"""Reproducible asset conversion. Reads the authored world/model without modifying them.

Requires nbtlib. Generates only gear_church templates/manifest and gear_follower model assets.
"""
import argparse
import base64
import collections
import copy
import gzip
import hashlib
import io
import json
import pathlib
import struct
import zlib
import nbtlib as n

MIN = (-77, -60, -66)
SIZE = (145, 52, 134)
PART = 32
BASE = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:bedrock", "minecraft:dirt", "minecraft:grass_block"}


def chunks(world):
    for file in sorted((world / "region").glob("*.mca")):
        data = file.read_bytes()
        for slot in range(1024):
            offset = int.from_bytes(data[slot * 4:slot * 4 + 4], "big") >> 8
            if not offset:
                continue
            start = offset * 4096
            size = struct.unpack_from(">I", data, start)[0]
            compression = data[start + 4]
            payload = data[start + 5:start + 4 + size]
            payload = zlib.decompress(payload) if compression == 2 else gzip.decompress(payload) if compression == 1 else payload
            if compression not in (1, 2, 3):
                raise ValueError("Unsupported region compression")
            yield n.File.parse(io.BytesIO(payload))


def export_world(world, repo):
    states = {}
    tags = {}
    tops = {}
    for chunk in chunks(world):
        cx, cz = int(chunk["xPos"]), int(chunk["zPos"])
        if cx < -5 or cx > 4 or cz < -5 or cz > 4:
            continue
        for tag in chunk.get("block_entities", []):
            pos = tuple(int(tag[k]) for k in ("x", "y", "z"))
            tags[pos] = copy.deepcopy(tag)
        for section in chunk.get("sections", []):
            block_states = section.get("block_states")
            if not block_states:
                continue
            palette = block_states["palette"]
            if all(str(p["Name"]) in BASE for p in palette):
                continue
            bits = max(4, (len(palette) - 1).bit_length())
            per_long, mask = 64 // bits, (1 << bits) - 1
            data = [int(v) & ((1 << 64) - 1) for v in block_states.get("data", [])]
            for index in range(4096):
                entry = palette[(data[index // per_long] >> ((index % per_long) * bits)) & mask] if data else palette[0]
                if str(entry["Name"]) in BASE:
                    continue
                pos = (cx * 16 + (index & 15), int(section["Y"]) * 16 + (index >> 8), cz * 16 + ((index >> 4) & 15))
                if not all(MIN[a] <= pos[a] < MIN[a] + SIZE[a] for a in range(3)):
                    raise ValueError(f"Authored block outside declared crop: {pos}")
                states[pos] = copy.deepcopy(entry)
                tops[pos[0], pos[2]] = max(tops.get((pos[0], pos[2]), -1000), pos[1])
    # Indoor only: a source floor and roof overhead, no furniture/fluid/collision at feet/head.
    walkable = []
    for x in range(MIN[0], MIN[0] + SIZE[0]):
        for z in range(MIN[2], MIN[2] + SIZE[2]):
            floor = states.get((x, -59, z))
            if floor is None or str(floor["Name"]) not in {"minecraft:packed_mud", "minecraft:stripped_jungle_wood", "minecraft:polished_granite"}:
                continue
            if (x, -58, z) in states or (x, -57, z) in states or tops.get((x, z), -100) < -32:
                continue
            walkable.append((x, -58, z))
    # Evenly distributed safe authored points, without inventing a room radius at runtime.
    chosen = []
    for desired_z in (-42, -18, 12, 42):
        for desired_x in (-48, -24, 14, 38):
            candidates = [p for p in walkable if all((p[0] - old[0]) ** 2 + (p[2] - old[2]) ** 2 >= 9 for old in chosen)]
            chosen.append(min(candidates, key=lambda p: (p[0] - desired_x) ** 2 + (p[2] - desired_z) ** 2))
    target = repo / "src/main/resources/data/lobotocraft/structures/gear_church"
    target.mkdir(parents=True, exist_ok=True)
    parts = []
    covered = 0
    for ox in range(0, SIZE[0], PART):
        for oz in range(0, SIZE[2], PART):
            palette, by_state, blocks = [], {}, []
            width, depth = min(PART, SIZE[0] - ox), min(PART, SIZE[2] - oz)
            for x in range(width):
                for z in range(depth):
                    wx, wz = MIN[0] + ox + x, MIN[2] + oz + z
                    top = tops.get((wx, wz))
                    if top is None:
                        continue
                    for y in range(top - MIN[1] + 1):
                        pos = (wx, MIN[1] + y, wz)
                        state = states.get(pos, n.Compound({"Name": n.String("minecraft:air")}))
                        key = json.dumps(state.unpack(), sort_keys=True)
                        if key not in by_state:
                            by_state[key] = len(palette)
                            palette.append(state)
                        block = n.Compound({"pos": n.List[n.Int]([x, y, z]), "state": n.Int(by_state[key])})
                        if pos in tags:
                            tag = copy.deepcopy(tags[pos])
                            for k in ("x", "y", "z", "keepPacked"):
                                tag.pop(k, None)
                            block["nbt"] = tag
                        blocks.append(block)
                        if pos in states:
                            covered += 1
            name = f"part_{ox // PART}_{oz // PART}"
            root = n.Compound({"DataVersion": n.Int(3465), "size": n.List[n.Int]([width, SIZE[1], depth]),
                               "palette": n.List[n.Compound](palette), "blocks": n.List[n.Compound](blocks),
                               "entities": n.List[n.Compound]([])})
            raw = io.BytesIO()
            n.File(root).write(raw)
            (target / (name + ".nbt")).write_bytes(gzip.compress(raw.getvalue(), mtime=0))
            parts.append({"template": f"lobotocraft:gear_church/{name}", "offset": [ox, 0, oz], "size": [width, SIZE[1], depth]})
    assert covered == len(states)
    roof = [-1] * (SIZE[0] * SIZE[2])
    foundation = []
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            wx, wz = MIN[0] + x, MIN[2] + z
            roof[z * SIZE[0] + x] = tops.get((wx, wz), MIN[1] - 1) - MIN[1]
            if (wx, MIN[1], wz) in states:
                foundation.append([x, z])
    manifest = {"source": str(world), "sourceMin": MIN, "size": SIZE, "partSize": PART,
                "authoredBlocks": len(states), "sourceRegionSha256": {
                    f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((world / "region").glob("*.mca"))},
                "parts": parts, "spawns": [[p[a] - MIN[a] for a in range(3)] for p in chosen],
                "roofHeights": roof, "foundationColumns": foundation}
    (target / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Templates {len(parts)}, authored blocks {len(states)}, anchors {chosen}")


def export_model(source, repo):
    model = json.loads((source / "齿轮信徒. - Converted.bbmodel").read_text(encoding="utf-8"))
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = {g["uuid"]: g for g in model["groups"]}
    bones = []
    def visit(node, parent=None):
        group = groups[node["uuid"]]
        bone = {"name": group["name"], "pivot": [-group["origin"][0], group["origin"][1], group["origin"][2]]}
        if parent:
            bone["parent"] = parent
        rotation = group.get("rotation", [0, 0, 0])
        if any(rotation):
            bone["rotation"] = [-rotation[0], -rotation[1], rotation[2]]
        cubes = []
        for child in node.get("children", []):
            if isinstance(child, dict):
                visit(child, group["name"])
                continue
            e = elements[child]
            if e.get("export", True) is False:
                continue
            cube = {"origin": [-e["to"][0], e["from"][1], e["from"][2]],
                    "size": [e["to"][a] - e["from"][a] for a in range(3)]}
            if any(e.get("rotation", [0, 0, 0])):
                cube["pivot"] = [-e["origin"][0], e["origin"][1], e["origin"][2]]
                cube["rotation"] = [-e["rotation"][0], -e["rotation"][1], e["rotation"][2]]
            uv = {}
            for side, face in e["faces"].items():
                if face.get("texture") is None:
                    continue
                a, b, c, d = face["uv"]
                uv[side] = {"uv": [a, b], "uv_size": [c - a, d - b]}
            cube["uv"] = uv
            cubes.append(cube)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    for root in model["outliner"]:
        visit(root)
    asset = repo / "src/main/resources/assets/lobotocraft"
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.gear_follower", "texture_width": 128, "texture_height": 128,
        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    (asset / "geo/gear_follower.geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
    (asset / "textures/entities/gear_follower.png").write_bytes(base64.b64decode(model["textures"][0]["source"].split(",", 1)[1]))
    animations = json.loads((source / "chilunxintu.animation.json").read_text(encoding="utf-8"))
    (asset / "animations/gear_follower.animation.json").write_text(json.dumps(animations, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Follower {len(bones)} bones, {sum(len(b.get('cubes', [])) for b in bones)} cubes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--repo", type=pathlib.Path, required=True)
    args = parser.parse_args()
    export_world(args.source / "齿轮教堂", args.repo)
    export_model(args.source / "齿轮教徒", args.repo)
