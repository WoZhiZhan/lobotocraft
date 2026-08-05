# Deferred Technical Debt Markers

This is the index for deferred issue comments left in source. These items need
compatible subsystem migrations and are intentionally separate from local
gameplay, security, and crash fixes.

## EGO-05: cooldown representations

- Source marker: BaseEgoWeapon#getCooldownTicks.
- Current state: player game time, item-NBT countdowns, and vanilla cooldowns
  are all used by different weapons.
- Player-visible risk: swapping an item, keeping it in inventory, or using a
  second copy can produce inconsistent cooldown behavior.
- Planned direction: define one cooldown service with explicit owner scope
  (player ability versus individual item instance), then migrate weapons by
  family.

## EGO-06: complex weapon state ownership

- Source marker: EndBirdWeapon.
- Current state: one weapon class owns persisted state, animations, hit timing,
  damage, and synchronization.
- Player-visible risk: a future animation or reconnect fix can accidentally
  change damage timing or cooldown state.
- Planned direction: introduce an EgoActionState and adapter layer, then
  migrate complex weapons only after their state contracts are documented.

## EGO-07: fragmented numerical sources

- Source marker: BaseEgoWeapon constructor and individual weapon classes.
- Current state: combat values can appear in constructors, active abilities,
  tooltips, catalogs, and set handlers.
- Player-visible risk: display values and live damage can diverge when only
  one location is changed.
- Planned direction: move per-weapon values into a typed specification that
  drives combat, tooltips, and research data. No balance numbers are changed
  by this tracking item.

## R-EGO-03: self-inflicted damage and hit rewards

- Source marker: ForgeModEvent#onLivingHurt.
- Current state: legacy reward handlers often key only on a player-attributed
  damage source.
- Player-visible risk: a player-targeted punishment source could accidentally
  count as a weapon hit and trigger healing, stacks, or other rewards.
- Planned direction: audit each self-damage source and each proc contract;
  generic handling must distinguish attacker and victim without removing
  effects that are intentionally self-targeted.

## RES-01: two resource namespaces

- Source marker: ResourceUtil#createInstance.
- Current state: the main mod and integrated facility module retain separate
  resource namespaces.
- Player-visible risk: an incorrectly constructed new resource key can show a
  missing texture or fail to load data.
- Planned direction: add typed namespace-specific helpers and asset existence
  tests before any migration. Existing valid resource keys remain unchanged.
