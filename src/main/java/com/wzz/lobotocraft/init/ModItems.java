package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.item.*;
import com.wzz.lobotocraft.item.base.TextSpawnEggItem;
import com.wzz.lobotocraft.item.block.ElevatorBlockItem;
import com.wzz.lobotocraft.item.block.EscapeBlockItem;
import com.wzz.lobotocraft.item.block.PunishingBirdBlockItem;
import com.wzz.lobotocraft.item.block.RegenerationReactorBlockItem;
import com.wzz.lobotocraft.item.debug.*;
import com.wzz.lobotocraft.item.ego.OttoItem;
import com.wzz.lobotocraft.item.ego.abandoned_murderer.*;
import com.wzz.lobotocraft.item.ego.approval_birds.*;
import com.wzz.lobotocraft.item.ego.end_bird.*;
import com.wzz.lobotocraft.item.ego.fourth_match_flame.*;
import com.wzz.lobotocraft.item.ego.largebird.*;
import com.wzz.lobotocraft.item.ego.punishing_bird.*;
import com.wzz.lobotocraft.item.ego.repentance.*;
import com.wzz.lobotocraft.item.ego.thorn_bus.*;
import com.wzz.lobotocraft.item.ego.wingbeat.*;
import com.wzz.lobotocraft.item.linkage.StrangeBadgeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MODID);

    public static final RegistryObject<Item> ONEBAD_SPAWN_EGG = REGISTRY.register("onebad_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.onebad, ExtendedColor.DIM_GRAY.getRGB(), ExtendedColor.DARK_RED.getRGB(), new Item.Properties(),
                    "§c它以言语中浮现的“罪孽”为食。"));

    public static final RegistryObject<Item> HAPPY_TEDDY_SPAWN_EGG = REGISTRY.register("happy_teddy_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.happy_teddy, ExtendedColor.DARK_GREEN.getRGB(), ExtendedColor.EARTH.getRGB(), new Item.Properties(),
                    "§c它的记忆始于温暖的怀抱。"));

    public static final RegistryObject<Item> IRON_MAIDEN_SPAWN_EGG = REGISTRY.register("iron_maiden_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.iron_maiden, ExtendedColor.SILVER.getRGB(), ExtendedColor.DARK_RED.getRGB(), new Item.Properties(),
                    "§c现在，一切都会好起来哒！"));

    public static final RegistryObject<Item> MEAT_IDOL_SPAWN_EGG = REGISTRY.register("meat_idol_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.meat_idol, 0x8B0000, 0x1a1a1a, new Item.Properties(),
                    "§c纯洁的颂唱止于永恒而绝望的嘶喊。"));

    public static final RegistryObject<Item> ABANDONED_MURDERER_SPAWN_EGG = REGISTRY.register("abandoned_murderer_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.abandoned_murderer, ExtendedColor.SADDLE_BROWN.getRGB(), ExtendedColor.BURLYWOOD.getRGB(), new Item.Properties(),
                    "§c真正可悲的，是那些死在我手里的人，那些像你一样的人。"));

    public static final RegistryObject<Item> LARGE_BIRD_SPAWN_EGG = REGISTRY.register("large_bird_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.large_bird, ExtendedColor.BLACK.getRGB(), ExtendedColor.YELLOW.getRGB(), new Item.Properties(),
                    "§c一个月后我们得出了结论：那些所谓的怪物根本就不存在。"));

    public static final RegistryObject<Item> PUNISHING_BIRD_SPAWN_EGG = REGISTRY.register("punishing_bird_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.punishing_bird, ExtendedColor.WHITE_SMOKE.getRGB(), ExtendedColor.LASER_RED.getRGB(), new Item.Properties(),
                    "§c人们从很久以前就开始不停地犯下罪恶。", "§c“为什么他们要做这种事儿？即使他们知道那是充满罪恶的？"));

    public static final RegistryObject<Item> APPROVAL_BIRD_SPAWN_EGG = REGISTRY.register("approval_bird_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.approval_bird, ExtendedColor.ONYX.getRGB(), ExtendedColor.MC_GOLD.getRGB(), new Item.Properties(),
                    "§c它的天平能够绝对公正地衡量任何罪恶。"));

    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_SPAWN_EGG = REGISTRY.register("fourth_match_flame_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.fourth_match_flame, ExtendedColor.MC_OBSIDIAN.getRGB(), ExtendedColor.MC_IRON.getRGB(), new Item.Properties(),
                    "§c我来...找你了...你会...化为...灰烬...就和我...一样..."));

    public static final RegistryObject<Item> WINGBEAT_SPAWN_EGG = REGISTRY.register("wingbeat_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.wingbeat, ExtendedColor.EMERALD_GEM.getRGB(), ExtendedColor.CHARCOAL.getRGB(), new Item.Properties(),
                    "§c只要有了小精灵们的祝福，一切争端都会平息下来的。"));

    public static final RegistryObject<Item> SNOWQUEEN_SPAWN_EGG = REGISTRY.register("snowqueen_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.snowqueen, ExtendedColor.NEON_CYAN.getRGB(), ExtendedColor.LAVENDER_BLUSH.getRGB(), new Item.Properties(),
                    "§c冰雪正在消融...是因为宫殿正在崩塌吗？又或许是因为春天即将来临？"));

    public static final RegistryObject<Item> PPODAE_SPAWN_EGG = REGISTRY.register("ppodae_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.ppodae, ExtendedColor.PLATINUM.getRGB(), ExtendedColor.TITANIUM.getRGB(), new Item.Properties(),
                    "§c但是...前辈！？您的“小心肝儿”嘴里嚼着的可是我们的同事啊..."));

    public static final RegistryObject<Item> CHILDREN_GALAXY_SPAWN_EGG = REGISTRY.register("children_galaxy_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.children_galaxy, ExtendedColor.MATERIAL_DEEP_PURPLE.getRGB(), ExtendedColor.FUCHSIA.getRGB(), new Item.Properties(),
                    "§c泪水一滴滴落下，就仿佛从云端坠落的繁星。那晚，世界陷入了酣睡，好似沉醉在了迷人的摇篮曲中。"));

    public static final RegistryObject<Item> BLUE_STAR_SPAWN_EGG = REGISTRY.register("blue_star_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.blue_star, ExtendedColor.MIDNIGHT_BLUE.getRGB(), ExtendedColor.MATERIAL_BLUE_GREY.getRGB(), new Item.Properties(),
                    "§c\"我们还会再见面的。到那时，你我都将化成明星！\""));

    public static final RegistryObject<Item> THORN_BUS_SPAWN_EGG = REGISTRY.register("thorn_bus_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.thorn_bus, ExtendedColor.NEON_GREEN.getRGB(), ExtendedColor.MATERIAL_BLUE_GREY.getRGB(), new Item.Properties(),
                    "§c\"我的脑袋就要爆炸了，再见咯~\""));

    public static final RegistryObject<Item> END_BIRD_SPAWN_EGG = REGISTRY.register("end_bird_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.end_bird, ExtendedColor.DARK.getRGB(), ExtendedColor.BLOOD_RED.getRGB(), new Item.Properties(),
                    "§c待世界充满了罪孽，这只众生畏惧着的怪鸟就会降临..."));

    public static final RegistryObject<Item> ISHARMLA_SPAWN_EGG = REGISTRY.register("isharmla_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.isharmla, 0x3a1a4a, 0x7fd8ff, new Item.Properties(),
                    "§c深海的歌声将一切引向沉眠……"));

    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_SPAWN_EGG = REGISTRY.register("butterfly_funeral_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.butterfly_funeral , 0xE8E8FF, 0x6a6a8a, new Item.Properties(),
                    "§c人死后会去向何方？"));

    public static final RegistryObject<Item> REDHAT_MERCENARY_SPAWN_EGG = REGISTRY.register("redhat_mercenary_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.redhat_mercenary, 0xb01010, 0x202020, new Item.Properties(),
                    "§c我要把那杂种的脑袋挂在我的床头，只有这样我才能安心睡个好觉。"));

    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_SPAWN_EGG = REGISTRY.register("fragment_of_the_universe_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.fragment_of_the_universe, 0x6a2a8a, 0xc080ff, new Item.Properties(),
                    "§c眼前响起了悦耳的歌声，它正慢慢朝你靠近..."));

    public static final RegistryObject<Item> BIGBADWOLF_SPAWN_EGG = REGISTRY.register("bigbadwolf_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.bigbadwolf, 0x4a3a2a, 0x901010, new Item.Properties(),
                    "§c说实话我根本就不在乎，因为我必须是一只又大又坏的狼..."));

    public static final RegistryObject<Item> HELPER_SPAWN_EGG = REGISTRY.register("helper_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.helper, 0xe8e8e8, 0x901010, new Item.Properties(),
                    "§c地板上满是鲜血，人们四散而逃..."));

    public static final RegistryObject<Item> SKADI_SPAWN_EGG = REGISTRY.register("skadi_corrupted_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.skadi_corrupted, 0x1A2A4A, 0xAEE6FF, new Item.Properties(),
                    "§c快走吧，博士......逃走吧。"));
    public static final RegistryObject<Item> CLEANER_SPAWN_EGG = REGISTRY.register("cleaner_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.cleaner, 0x2b2b2b, 0x8b0000, new Item.Properties(),
                    "§c夜幕降临之际，他们悄悄地来到后巷……"));
    public static final RegistryObject<Item> SHELL_SEA_RUNNER_SPAWN_EGG = REGISTRY.register("shell_sea_runner_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.shell_sea_runner, 0x2e5a6b, 0x9ed0d8, new Item.Properties(),
                    "§c长出四肢的恐鱼。"));
    public static final RegistryObject<Item> DEEPSEA_SLIDER_SPAWN_EGG = REGISTRY.register("deepsea_slider_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.deepsea_slider, 0x1f3f4f, 0x7fb0c0, new Item.Properties(),
                    "§c爬上海岸的恐鱼。"));
    public static final RegistryObject<Item> RIDGESEA_SPITTER_SPAWN_EGG = REGISTRY.register("ridgesea_spitter_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.ridgesea_spitter, 0x2a4a5a, 0x6fa0b0, new Item.Properties(),
                    "§c神秘的恐鱼。"));
    public static final RegistryObject<Item> PRIMALSEA_PIERCER_SPAWN_EGG = REGISTRY.register("primalsea_piercer_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.primalsea_piercer, 0x3a2a4a, 0xc09ed8, new Item.Properties(),
                    "§c生长出固态器官的恐鱼。"));
    public static final RegistryObject<Item> NUCLEIC_MALEFICENT_SPAWN_EGG = REGISTRY.register("nucleic_maleficent_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.nucleic_maleficent, 0x4a3a2a, 0xd8b89e, new Item.Properties(),
                    "§c意外发育成独立个体的细胞。"));
    public static final RegistryObject<Item> BASINSEA_REAPER_SPAWN_EGG = REGISTRY.register("basinsea_reaper_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.basinsea_reaper, 0x4a2a3a, 0xd89eb8, new Item.Properties(),
                    "§c发出尖锐声响的恐鱼。"));

    public static final RegistryObject<Item> PE_BOX = REGISTRY.register("pe_box", PEBoxItem::new);
    public static final RegistryObject<Item> STRANGE_BADGE = REGISTRY.register("strange_badge", StrangeBadgeItem::new);
    public static final RegistryObject<Item> STRUCTURE_TOOL_SAFE = REGISTRY.register("structure_tool_save", StructureToolSaveItem::new);
    public static final RegistryObject<Item> ICON = REGISTRY.register("icon", IconItem::new);
    public static final RegistryObject<Item> CONVEYER = REGISTRY.register("conveyer", ConveyerItem::new);
    public static final RegistryObject<Item> REGENERATION_REACTOR = REGISTRY.register("regeneration_reactor", RegenerationReactorBlockItem::new);
    public static final RegistryObject<Item> ARMOR_LOCK = REGISTRY.register("armor_lock", ArmorLockItem::new);
    public static final RegistryObject<Item> SPECIAL_RECORD = REGISTRY.register("special_record", SpecialRecordItem::new);
    public static final RegistryObject<Item> TARGET_MARKER = REGISTRY.register("target_marker", TargetMarkerItem::new);
    /** 又大又可能很坏的狼的特殊E.G.O饰品"羊皮"(不显示在图鉴内,不受观察等级影响) */
    public static final RegistryObject<Item> SHEEPSKIN_CURIO = REGISTRY.register("sheepskin_curio",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
                        java.util.List<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(net.minecraft.network.chat.Component.literal("§7从狼腹中取回的羊皮。"));
                    tooltip.add(net.minecraft.network.chat.Component.literal("§8持有者完成对小红帽雇佣兵的工作后，其计数器会减少。"));
                    super.appendHoverText(stack, level, tooltip, flag);
                }
            });

    public static final RegistryObject<Item> REPENTANCE_CHESTPLATE = REGISTRY.register("repentance_chestplate", RepentanceChestplate::new);
    public static final RegistryObject<Item> REPENTANCE_LEGGINGS = REGISTRY.register("repentance_leggings", RepentanceLeggings::new);
    public static final RegistryObject<Item> REPENTANCE_BOOTS = REGISTRY.register("repentance_boots", RepentanceBoots::new);
    public static final RegistryObject<Item> REPENTANCE_WEAPON = REGISTRY.register("repentance_weapon", RepentanceWeapon::new);
    public static final RegistryObject<Item> REPENTANCE_CURIO = REGISTRY.register("repentance_curio", RepentanceCurio::new);

    // ===== 新星之声 套装(O-03-93,ALEPH) =====
    public static final RegistryObject<Item> BLUE_STAR_CHESTPLATE = REGISTRY.register("blue_star_chestplate", com.wzz.lobotocraft.item.ego.blue_star.BlueStarChestplate::new);
    public static final RegistryObject<Item> BLUE_STAR_LEGGINGS = REGISTRY.register("blue_star_leggings", com.wzz.lobotocraft.item.ego.blue_star.BlueStarLeggings::new);
    public static final RegistryObject<Item> BLUE_STAR_BOOTS = REGISTRY.register("blue_star_boots", com.wzz.lobotocraft.item.ego.blue_star.BlueStarBoots::new);
    public static final RegistryObject<Item> BLUE_STAR_WEAPON = REGISTRY.register("blue_star_weapon", com.wzz.lobotocraft.item.ego.blue_star.BlueStarWeapon::new);
    public static final RegistryObject<Item> BLUE_STAR_CURIO = REGISTRY.register("blue_star_curio", com.wzz.lobotocraft.item.ego.blue_star.BlueStarCurio::new);
    public static final RegistryObject<Item> OTTO = REGISTRY.register("otto", OttoItem::new);
    public static final RegistryObject<Item> ESCAPE = REGISTRY.register("escape", EscapeBlockItem::new);
    public static final RegistryObject<Item> ELEVATOR = REGISTRY.register("elevator", ElevatorBlockItem::new);
    public static final RegistryObject<Item> LARGEBIRD_WEAPON = REGISTRY.register("largebird_weapon", LargeBirdWeapon::new);
    public static final RegistryObject<Item> LARGEBIRD_CURIO = REGISTRY.register("largebird_curio", LargeBirdCurio::new);
    public static final RegistryObject<Item> LARGEBIRD_CHESTPLATE = REGISTRY.register("largebird_chestplate", LargeBirdChestplate::new);
    public static final RegistryObject<Item> LARGEBIRD_LEGGINGS = REGISTRY.register("largebird_leggings", LargeBirdLeggings::new);
    public static final RegistryObject<Item> LARGEBIRD_BOOTS = REGISTRY.register("largebird_boots", LargeBirdBoots::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_WEAPON = REGISTRY.register("punishing_bird_weapon", PunishingBirdWeapon::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_CURIO = REGISTRY.register("punishing_bird_curio", PunishingBirdCurio::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_CHESTPLATE = REGISTRY.register("punishing_bird_chestplate", PunishingBirdChestplate::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_LEGGINGS = REGISTRY.register("punishing_bird_leggings", PunishingBirdLeggings::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_BOOTS = REGISTRY.register("punishing_bird_boots", PunishingBirdBoots::new);
    public static final RegistryObject<Item> PUNISHING_BIRD_BLOCK = REGISTRY.register("punishing_bird_block", PunishingBirdBlockItem::new);
    public static final RegistryObject<Item> REMOVER = REGISTRY.register("remover", RemoverItem::new);
    public static final RegistryObject<Item> APPROVAL_BIRD_CHESTPLATE = REGISTRY.register("approval_bird_chestplate", ApprovalBirdChestplate::new);
    public static final RegistryObject<Item> APPROVAL_BIRD_LEGGINGS = REGISTRY.register("approval_bird_leggings", ApprovalBirdLeggings::new);
    public static final RegistryObject<Item> APPROVAL_BIRD_BOOTS = REGISTRY.register("approval_bird_boots", ApprovalBirdBoots::new);
    public static final RegistryObject<Item> APPROVAL_BIRD_WEAPON = REGISTRY.register("approval_bird_weapon", ApprovalBirdWeapon::new);
    public static final RegistryObject<Item> APPROVAL_BIRD_CURIO = REGISTRY.register("approval_bird_curio", ApprovalBirdCurio::new);
    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_CHESTPLATE = REGISTRY.register("fourth_match_flame_chestplate", FourthMatchFlameChestplate::new);
    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_LEGGINGS = REGISTRY.register("fourth_match_flame_leggings", FourthMatchFlameLeggings::new);
    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_BOOTS = REGISTRY.register("fourth_match_flame_boots", FourthMatchFlameBoots::new);
    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_WEAPON = REGISTRY.register("fourth_match_flame_weapon", FourthMatchFlameWeapon::new);
    public static final RegistryObject<Item> FOURTH_MATCH_FLAME_CURIO = REGISTRY.register("fourth_match_flame_curio", FourthMatchFlameCurio::new);
    public static final RegistryObject<Item> ENTITY_ESCAPE = REGISTRY.register("entity_escape", EscapeItem::new);
    public static final RegistryObject<Item> WINGBEAT_CHESTPLATE = REGISTRY.register("wingbeat_chestplate", WingBeatChestplate::new);
    public static final RegistryObject<Item> WINGBEAT_LEGGINGS = REGISTRY.register("wingbeat_leggings", WingBeatLeggings::new);
    public static final RegistryObject<Item> WINGBEAT_BOOTS = REGISTRY.register("wingbeat_boots", WingBeatBoots::new);
    public static final RegistryObject<Item> WINGBEAT_WEAPON = REGISTRY.register("wingbeat_weapon", WingBeatWeapon::new);
    public static final RegistryObject<Item> WINGBEAT_CURIO = REGISTRY.register("wingbeat_curio", WingBeatCurio::new);
    public static final RegistryObject<Item> ABANDONED_MURDERER_CHESTPLATE = REGISTRY.register("abandoned_murderer_chestplate", AbandonedMurdererChestplate::new);
    public static final RegistryObject<Item> ABANDONED_MURDERER_LEGGINGS = REGISTRY.register("abandoned_murderer_leggings", AbandonedMurdererLeggings::new);
    public static final RegistryObject<Item> ABANDONED_MURDERER_BOOTS = REGISTRY.register("abandoned_murderer_boots", AbandonedMurdererBoots::new);
    public static final RegistryObject<Item> ABANDONED_MURDERER_WEAPON = REGISTRY.register("abandoned_murderer_weapon", AbandonedMurdererWeapon::new);
    public static final RegistryObject<Item> ABANDONED_MURDERER_CURIO = REGISTRY.register("abandoned_murderer_curio", AbandonedMurdererCurio::new);
    public static final RegistryObject<Item> TT2 = REGISTRY.register("tt2", TT2Item::new);
    public static final RegistryObject<Item> THORN_BUS_CHESTPLATE = REGISTRY.register("thorn_bus_chestplate", ThornBusChestplate::new);
    public static final RegistryObject<Item> THORN_BUS_LEGGINGS = REGISTRY.register("thorn_bus_leggings", ThornBusLeggings::new);
    public static final RegistryObject<Item> THORN_BUS_BOOTS = REGISTRY.register("thorn_bus_boots", ThornBusBoots::new);
    public static final RegistryObject<Item> THORN_BUS_WEAPON = REGISTRY.register("thorn_bus_weapon", ThornBusWeapon::new);
    public static final RegistryObject<Item> THORN_BUS_CURIO = REGISTRY.register("thorn_bus_curio", ThornBusCurio::new);
    public static final RegistryObject<Item> END_BIRD_CHESTPLATE = REGISTRY.register("end_bird_chestplate", EndBirdChestplate::new);
    public static final RegistryObject<Item> END_BIRD_LEGGINGS = REGISTRY.register("end_bird_leggings", EndBirdLeggings::new);
    public static final RegistryObject<Item> END_BIRD_BOOTS = REGISTRY.register("end_bird_boots", EndBirdBoots::new);
    public static final RegistryObject<Item> END_BIRD_WEAPON = REGISTRY.register("end_bird_weapon", EndBirdWeapon::new);
    public static final RegistryObject<Item> END_BIRD_CURIO = REGISTRY.register("end_bird_curio", EndBirdCurio::new);
    public static final RegistryObject<Item> STOP_ESCAPE = REGISTRY.register("stop_escape", StopEscapeItem::new);
    public static final RegistryObject<Item> CAPTURE_UNIT = REGISTRY.register("capture_unit", CaptureUnitItem::new);
}
