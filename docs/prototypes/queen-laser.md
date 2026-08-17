# Queen Laser Visual Prototype

Question: how far can a procedural, asset-free Queen of Hatred ultimate laser be pushed in the current renderer?

Commands:

```text
/lobotocraft queen_laser preview
/lobotocraft queen_laser max
/lobotocraft queen_laser clear
```

This is deliberately isolated throwaway code. It is visual-only and is not part of the Queen of Hatred entity, damage logic, AI, or animation lifecycle. After review, delete it or rewrite the selected ideas as production effects.

Second-pass findings:

- The original pre-cast effect is a layered Spine setup (`Casting_circle_1` through `Casting_circle_4`), not a generic procedural ring. The prototype uses cropped regions from the original atlas and keeps procedural geometry only as supporting motion.
- Beam geometry uses the world depth buffer and the endpoint is block-clipped, so walls occlude the beam and its glow.
- Screen exposure is driven by camera-to-beam visibility samples. Occluded beam sections contribute no exposure.
- The source `1_Casting` animation is 13.4 seconds; this prototype intentionally uses 0.5 duration (6.7 seconds) while preserving the source-relative layer timing. Main circle attachments appear together, but their bone scales expand in four staggered windows. The complete circle then persists through the firing loop and fades only during the ending transition.
- Exposure adaptation uses one isolated mask pass. It drives up to 88% environment darkening while cutting a soft, screen-space opening around the visible laser pixels. The original beam remains bright without adding post-process bloom, replacing the scene, or occupying Minecraft's single post-chain slot.
