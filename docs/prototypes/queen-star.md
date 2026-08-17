# Queen Star Visual Prototype

Question: can the Queen of Hatred's flat normal-attack star read clearly in Minecraft without a Blockbench model?

Commands:

```text
/lobotocraft queen_star original
/lobotocraft queen_star clear
```

The prototype is visual-only and uses world depth testing. It does not register an entity, apply damage or healing, or modify the abnormality lifecycle.

The original Unity prefab was traced from `MagicalGirlNormalAttack_Hero`. It consists of a yellow-green `CFX_T_Star Add` core with a 0.5-second `CFXM4_T_SpaceThruster` trail, plus four looping particle systems: warm and cool color ranges, each rendered with filled and outlined star textures. The Minecraft version uses those four extracted textures and adds a thin procedural side wall to the core. The core is world-oriented perpendicular to its flight vector and spins around that axis; only the peripheral particles remain camera-facing.

Verdict: pending user review. Delete or absorb this prototype after the original-style composite and thickness are approved.
