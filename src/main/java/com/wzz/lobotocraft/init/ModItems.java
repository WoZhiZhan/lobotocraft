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
import com.wzz.lobotocraft.item.ego.SheepskinCurio;
import com.wzz.lobotocraft.item.ego.abandoned_murderer.*;
import com.wzz.lobotocraft.item.ego.approval_birds.*;
import com.wzz.lobotocraft.item.ego.army_in_black.*;
import com.wzz.lobotocraft.item.ego.bigbadwolf.*;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.*;
import com.wzz.lobotocraft.item.ego.children_galaxy.*;
import com.wzz.lobotocraft.item.ego.crumbling_armor.*;
import com.wzz.lobotocraft.item.ego.end_bird.*;
import com.wzz.lobotocraft.item.ego.fourth_match_flame.*;
import com.wzz.lobotocraft.item.ego.fragment_of_the_universe.*;
import com.wzz.lobotocraft.item.ego.happy_teddy.*;
import com.wzz.lobotocraft.item.ego.helper.*;
import com.wzz.lobotocraft.item.ego.largebird.*;
import com.wzz.lobotocraft.item.ego.leticia.*;
import com.wzz.lobotocraft.item.ego.ppodae.*;
import com.wzz.lobotocraft.item.ego.punishing_bird.*;
import com.wzz.lobotocraft.item.ego.queen_bee.*;
import com.wzz.lobotocraft.item.ego.red_shoes.*;
import com.wzz.lobotocraft.item.ego.redhat_mercenary.*;
import com.wzz.lobotocraft.item.ego.repentance.*;
import com.wzz.lobotocraft.item.ego.smiling_corpse_mountain.*;
import com.wzz.lobotocraft.item.ego.snowqueen.*;
import com.wzz.lobotocraft.item.ego.the_lady_facing_the_wall.*;
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

    public static final RegistryObject<Item> CLERK_SPAWN_EGG = REGISTRY.register("clerk_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.clerk, 0xE8C7A4, 0x4A4A4A, new Item.Properties(),
                    "§7公司基层文职人员。"));

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

    public static final RegistryObject<Item> QUEEN_BEE_SPAWN_EGG = REGISTRY.register("queen_bee_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.queen_bee, 0xEDC343, 0x43241B, new Item.Properties(),
                    "§c如果你感到胃部剧痛或是颈部发痒，那么唯一能做的事就是最后一次仰望蓝天。"));

    public static final RegistryObject<Item> RED_SHOES_SPAWN_EGG = REGISTRY.register("red_shoes_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.red_shoes, 0x8B0000, 0xFFFFFF, new Item.Properties(),
                    "§c女孩泪流满面地哀求着：“先生，求您把我的脚砍下来吧！”"));

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

    public static final RegistryObject<Item> AMBER_DAWN_SPAWN_EGG = REGISTRY.register("amber_dawn_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.amber_dawn, 0x6B3F1D, 0xE0A43A, new Item.Properties(),
                    "§c食物-新鲜。替代品-很好。"));

    public static final RegistryObject<Item> VIOLET_NOON_SPAWN_EGG = REGISTRY.register("violet_noon_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.violet_noon, 0x2B123F, 0xC77DFF, new Item.Properties(),
                    "§d请给我们爱！！！"));

    public static final RegistryObject<Item> GREEN_NOON_SPAWN_EGG = REGISTRY.register("green_noon_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.green_noon, 0x101510, 0x6BFF8E, new Item.Properties(),
                    "§a理解的过程"));

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
    public static final RegistryObject<Item> ARMY_IN_BLACK_SPAWN_EGG = REGISTRY.register("army_in_black_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.army_in_black, 0xff69b4, 0x8a8a8a, new Item.Properties(),
                    "§c人类的心是粉红色的，与之同色的迷彩能让我们融入他们的内心。"));
    public static final RegistryObject<Item> CRUMBLING_ARMOR_SPAWN_EGG = REGISTRY.register("crumbling_armor_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.crumbling_armor, 0x050505, 0x2f5fb3, new Item.Properties(),
                    "§c“不畏死，方可生。”"));
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_SPAWN_EGG = REGISTRY.register("the_lady_facing_the_wall_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.the_lady_facing_the_wall, 0x5a5048, 0xd8d2c8, new Item.Properties(),
                    "§c一个人，即便生前有再多的故事能够讲述，可到了死后，唯有孤独才是唯一的倾听者。"));
    public static final RegistryObject<Item> LETICIA_SPAWN_EGG = REGISTRY.register("leticia_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.leticia, 0x5a2f67, 0xe7d7f0, new Item.Properties(),
                    "§c所以，她想出了这个绝妙的主意！"));

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
    public static final RegistryObject<Item> NOTHING_THERE_SPAWN_EGG = REGISTRY.register("nothing_there_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.nothing_there, 0x8B0000, 0xCD5C5C, new Item.Properties(),
                    "§c主管！主管！主管！主管！主管！主管！主管！主管！主管！主管！"));
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_SPAWN_EGG = REGISTRY.register("smiling_corpse_mountain_spawn_egg",
            () -> new TextSpawnEggItem(ModEntities.smiling_corpse_mountain, ExtendedColor.BLACK.getRGB(), ExtendedColor.WHITE_SMOKE.getRGB(), new Item.Properties(),
                    "§c那些阴森可怖的笑脸上弥漫着哀伤。"));

    public static final RegistryObject<Item> PE_BOX = REGISTRY.register("pe_box", PEBoxItem::new);
    public static final RegistryObject<Item> STRANGE_BADGE = REGISTRY.register("strange_badge", StrangeBadgeItem::new);
    public static final RegistryObject<Item> STRUCTURE_TOOL_SAFE = REGISTRY.register("structure_tool_save", StructureToolSaveItem::new);
    public static final RegistryObject<Item> ICON = REGISTRY.register("icon", IconItem::new);
    public static final RegistryObject<Item> CONVEYER = REGISTRY.register("conveyer", ConveyerItem::new);
    public static final RegistryObject<Item> REGENERATION_REACTOR = REGISTRY.register("regeneration_reactor", RegenerationReactorBlockItem::new);
    public static final RegistryObject<Item> ARMOR_LOCK = REGISTRY.register("armor_lock", ArmorLockItem::new);
    public static final RegistryObject<Item> SPECIAL_RECORD = REGISTRY.register("special_record", SpecialRecordItem::new);
    public static final RegistryObject<Item> TARGET_MARKER = REGISTRY.register("target_marker", TargetMarkerItem::new);
    public static final RegistryObject<Item> WORK_DEVICE = REGISTRY.register("work_device", WorkDeviceItem::new);

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
    public static final RegistryObject<Item> CHILDREN_GALAXY_CHESTPLATE = REGISTRY.register("children_galaxy_chestplate", ChildrenGalaxyChestplate::new);
    public static final RegistryObject<Item> CHILDREN_GALAXY_LEGGINGS = REGISTRY.register("children_galaxy_leggings", ChildrenGalaxyLeggings::new);
    public static final RegistryObject<Item> CHILDREN_GALAXY_BOOTS = REGISTRY.register("children_galaxy_boots", ChildrenGalaxyBoots::new);
    public static final RegistryObject<Item> CHILDREN_GALAXY_WEAPON = REGISTRY.register("children_galaxy_weapon", ChildrenGalaxyWeapon::new);
    public static final RegistryObject<Item> CHILDREN_GALAXY_CURIO = REGISTRY.register("children_galaxy_curio", ChildrenGalaxyCurio::new);
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
    public static final RegistryObject<Item> PPODAE_CHESTPLATE = REGISTRY.register("ppodae_chestplate", PpodaeChestplate::new);
    public static final RegistryObject<Item> PPODAE_LEGGINGS = REGISTRY.register("ppodae_leggings", PpodaeLeggings::new);
    public static final RegistryObject<Item> PPODAE_BOOTS = REGISTRY.register("ppodae_boots", PpodaeBoots::new);
    public static final RegistryObject<Item> PPODAE_WEAPON = REGISTRY.register("ppodae_weapon", PpodaeWeapon::new);
    public static final RegistryObject<Item> PPODAE_CURIO = REGISTRY.register("ppodae_curio", PpodaeCurio::new);
    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_CHESTPLATE = REGISTRY.register("fragment_of_the_universe_chestplate", FragmentUniverseChestplate::new);
    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_LEGGINGS = REGISTRY.register("fragment_of_the_universe_leggings", FragmentUniverseLeggings::new);
    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_BOOTS = REGISTRY.register("fragment_of_the_universe_boots", FragmentUniverseBoots::new);
    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_WEAPON = REGISTRY.register("fragment_of_the_universe_weapon", FragmentUniverseWeapon::new);
    public static final RegistryObject<Item> FRAGMENT_OF_THE_UNIVERSE_CURIO = REGISTRY.register("fragment_of_the_universe_curio", FragmentUniverseCurio::new);
    public static final RegistryObject<Item> SNOWQUEEN_CHESTPLATE = REGISTRY.register("snowqueen_chestplate", SnowQueenChestplate::new);
    public static final RegistryObject<Item> SNOWQUEEN_LEGGINGS = REGISTRY.register("snowqueen_leggings", SnowQueenLeggings::new);
    public static final RegistryObject<Item> SNOWQUEEN_BOOTS = REGISTRY.register("snowqueen_boots", SnowQueenBoots::new);
    public static final RegistryObject<Item> SNOWQUEEN_WEAPON = REGISTRY.register("snowqueen_weapon", SnowQueenWeapon::new);
    public static final RegistryObject<Item> SNOWQUEEN_CURIO = REGISTRY.register("snowqueen_curio", SnowQueenCurio::new);
    public static final RegistryObject<Item> INNER_COURAGE_CURIO = REGISTRY.register("inner_courage_curio", InnerCourageCurio::new);
    public static final RegistryObject<Item> FOOLHARDY_COURAGE_CURIO = REGISTRY.register("foolhardy_courage_curio", FoolhardyCourageCurio::new);
    public static final RegistryObject<Item> CRUMBLING_ARMOR_CHESTPLATE = REGISTRY.register("crumbling_armor_chestplate", CrumblingArmorChestplate::new);
    public static final RegistryObject<Item> CRUMBLING_ARMOR_LEGGINGS = REGISTRY.register("crumbling_armor_leggings", CrumblingArmorLeggings::new);
    public static final RegistryObject<Item> CRUMBLING_ARMOR_BOOTS = REGISTRY.register("crumbling_armor_boots", CrumblingArmorBoots::new);
    public static final RegistryObject<Item> CRUMBLING_ARMOR_WEAPON = REGISTRY.register("crumbling_armor_weapon", CrumblingArmorWeapon::new);
    public static final RegistryObject<Item> RED_SHOES_CHESTPLATE = REGISTRY.register("red_shoes_chestplate", RedShoesChestplate::new);
    public static final RegistryObject<Item> RED_SHOES_LEGGINGS = REGISTRY.register("red_shoes_leggings", RedShoesLeggings::new);
    public static final RegistryObject<Item> RED_SHOES_BOOTS = REGISTRY.register("red_shoes_boots", RedShoesBoots::new);
    public static final RegistryObject<Item> RED_SHOES_WEAPON = REGISTRY.register("red_shoes_weapon", RedShoesWeapon::new);
    public static final RegistryObject<Item> RED_SHOES_CURIO = REGISTRY.register("red_shoes_curio", RedShoesCurio::new);
    public static final RegistryObject<Item> QUEEN_BEE_CHESTPLATE = REGISTRY.register("queen_bee_chestplate", QueenBeeChestplate::new);
    public static final RegistryObject<Item> QUEEN_BEE_LEGGINGS = REGISTRY.register("queen_bee_leggings", QueenBeeLeggings::new);
    public static final RegistryObject<Item> QUEEN_BEE_BOOTS = REGISTRY.register("queen_bee_boots", QueenBeeBoots::new);
    public static final RegistryObject<Item> QUEEN_BEE_WEAPON = REGISTRY.register("queen_bee_weapon", QueenBeeWeapon::new);
    public static final RegistryObject<Item> QUEEN_BEE_CURIO = REGISTRY.register("queen_bee_curio", QueenBeeCurio::new);
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_CHESTPLATE = REGISTRY.register("the_lady_facing_the_wall_chestplate", LadyFacingWallChestplate::new);
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_LEGGINGS = REGISTRY.register("the_lady_facing_the_wall_leggings", LadyFacingWallLeggings::new);
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_BOOTS = REGISTRY.register("the_lady_facing_the_wall_boots", LadyFacingWallBoots::new);
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_WEAPON = REGISTRY.register("the_lady_facing_the_wall_weapon", LadyFacingWallWeapon::new);
    public static final RegistryObject<Item> THE_LADY_FACING_THE_WALL_CURIO = REGISTRY.register("the_lady_facing_the_wall_curio", LadyFacingWallCurio::new);
    public static final RegistryObject<Item> HAPPY_TEDDY_CHESTPLATE = REGISTRY.register("happy_teddy_chestplate", HappyTeddyChestplate::new);
    public static final RegistryObject<Item> HAPPY_TEDDY_LEGGINGS = REGISTRY.register("happy_teddy_leggings", HappyTeddyLeggings::new);
    public static final RegistryObject<Item> HAPPY_TEDDY_BOOTS = REGISTRY.register("happy_teddy_boots", HappyTeddyBoots::new);
    public static final RegistryObject<Item> HAPPY_TEDDY_WEAPON = REGISTRY.register("happy_teddy_weapon", HappyTeddyWeapon::new);
    public static final RegistryObject<Item> HAPPY_TEDDY_CURIO = REGISTRY.register("happy_teddy_curio", HappyTeddyCurio::new);
    public static final RegistryObject<Item> LETICIA_CHESTPLATE = REGISTRY.register("leticia_chestplate", LeticiaChestplate::new);
    public static final RegistryObject<Item> LETICIA_LEGGINGS = REGISTRY.register("leticia_leggings", LeticiaLeggings::new);
    public static final RegistryObject<Item> LETICIA_BOOTS = REGISTRY.register("leticia_boots", LeticiaBoots::new);
    public static final RegistryObject<Item> LETICIA_WEAPON = REGISTRY.register("leticia_weapon", LeticiaWeapon::new);
    public static final RegistryObject<Item> LETICIA_CURIO = REGISTRY.register("leticia_curio", LeticiaCurio::new);
    public static final RegistryObject<Item> HELPER_CHESTPLATE = REGISTRY.register("helper_chestplate", HelperChestplate::new);
    public static final RegistryObject<Item> HELPER_LEGGINGS = REGISTRY.register("helper_leggings", HelperLeggings::new);
    public static final RegistryObject<Item> HELPER_BOOTS = REGISTRY.register("helper_boots", HelperBoots::new);
    public static final RegistryObject<Item> HELPER_WEAPON = REGISTRY.register("helper_weapon", HelperWeapon::new);
    public static final RegistryObject<Item> HELPER_CURIO = REGISTRY.register("helper_curio", HelperCurio::new);
    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_CHESTPLATE = REGISTRY.register("butterfly_funeral_chestplate", ButterflyFuneralChestplate::new);
    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_LEGGINGS = REGISTRY.register("butterfly_funeral_leggings", ButterflyFuneralLeggings::new);
    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_BOOTS = REGISTRY.register("butterfly_funeral_boots", ButterflyFuneralBoots::new);
    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_WEAPON = REGISTRY.register("butterfly_funeral_weapon", ButterflyFuneralWeapon::new);
    public static final RegistryObject<Item> BUTTERFLY_FUNERAL_CURIO = REGISTRY.register("butterfly_funeral_curio", ButterflyFuneralCurio::new);
    public static final RegistryObject<Item> BIG_BADWOLF_CHESTPLATE = REGISTRY.register("big_badwolf_chestplate", BigBadwolfChestplate::new);
    public static final RegistryObject<Item> BIG_BADWOLF_LEGGINGS = REGISTRY.register("big_badwolf_leggings", BigBadwolfLeggings::new);
    public static final RegistryObject<Item> BIG_BADWOLF_BOOTS = REGISTRY.register("big_badwolf_boots", BigBadwolfBoots::new);
    public static final RegistryObject<Item> BIG_BADWOLF_WEAPON = REGISTRY.register("big_badwolf_weapon", BigBadwolfWeapon::new);
    public static final RegistryObject<Item> BIG_BADWOLF_CURIO = REGISTRY.register("big_badwolf_curio", BigBadwolfCurio::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_CHESTPLATE = REGISTRY.register("redhat_mercenary_chestplate", RedhatMercenaryChestplate::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_LEGGINGS = REGISTRY.register("redhat_mercenary_leggings", RedhatMercenaryLeggings::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_BOOTS = REGISTRY.register("redhat_mercenary_boots", RedhatMercenaryBoots::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_WEAPON_KNIFE = REGISTRY.register("redhat_mercenary_weapon_knife", RedhatMercenaryWeaponKnife::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_WEAPON_GUN = REGISTRY.register("redhat_mercenary_weapon_gun", RedhatMercenaryWeaponGun::new);
    public static final RegistryObject<Item> REDHAT_MERCENARY_CURIO = REGISTRY.register("redhat_mercenary_curio", RedhatMercenaryCurio::new);
    public static final RegistryObject<Item> SHEEPSKIN_CURIO = REGISTRY.register("sheepskin_curio", SheepskinCurio::new);
    public static final RegistryObject<Item> ARMY_IN_BLACK_CHESTPLATE = REGISTRY.register("army_in_black_chestplate", ArmyInBlackChestplate::new);
    public static final RegistryObject<Item> ARMY_IN_BLACK_LEGGINGS = REGISTRY.register("army_in_black_leggings", ArmyInBlackLeggings::new);
    public static final RegistryObject<Item> ARMY_IN_BLACK_BOOTS = REGISTRY.register("army_in_black_boots", ArmyInBlackBoots::new);
    public static final RegistryObject<Item> ARMY_IN_BLACK_WEAPON = REGISTRY.register("army_in_black_weapon", ArmyInBlackWeapon::new);
    public static final RegistryObject<Item> ARMY_IN_BLACK_CURIO = REGISTRY.register("army_in_black_curio", ArmyInBlackCurio::new);
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_CHESTPLATE = REGISTRY.register("smiling_corpse_mountain_chestplate", SmilingCorpseMountainChestplate::new);
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_LEGGINGS = REGISTRY.register("smiling_corpse_mountain_leggings", SmilingCorpseMountainLeggings::new);
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_BOOTS = REGISTRY.register("smiling_corpse_mountain_boots", SmilingCorpseMountainBoots::new);
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_WEAPON = REGISTRY.register("smiling_corpse_mountain_weapon", SmilingCorpseMountainWeapon::new);
    public static final RegistryObject<Item> SMILING_CORPSE_MOUNTAIN_CURIO = REGISTRY.register("smiling_corpse_mountain_curio", SmilingCorpseMountainCurio::new);
}
