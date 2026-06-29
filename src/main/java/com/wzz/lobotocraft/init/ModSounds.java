package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModMain.MODID);

	private static RegistryObject<SoundEvent> register(String id) {
		return REGISTRY.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceUtil.createInstance(id)));
	}

	public static final RegistryObject<SoundEvent> ONEBAD_AMBIENT = register("onebad_ambient");
	public static final RegistryObject<SoundEvent> TOUCH_OFF = register("touch_off");
	public static final RegistryObject<SoundEvent> UNLOCK_INFORMATION = register("unlock_information");
	public static final RegistryObject<SoundEvent> ENERGY_SHORTAGE_INFORMATION = register("energy_shortage_information");
	public static final RegistryObject<SoundEvent> SPECIAL_RECORD = register("special_record");
	public static final RegistryObject<SoundEvent> BLOODY_DAWN_START = register("bloody_dawn_start");
	public static final RegistryObject<SoundEvent> BLOODY_DAWN_END = register("bloody_dawn_end");
	public static final RegistryObject<SoundEvent> GREEN_DAWN_START = register("green_dawn_start");
	public static final RegistryObject<SoundEvent> GREEN_DAWN_AMBIENT = register("green_dawn_ambient");
	public static final RegistryObject<SoundEvent> GREEN_DAWN_ATTACK = register("green_dawn_attack");
	public static final RegistryObject<SoundEvent> GREEN_DAWN_END = register("green_dawn_end");
	public static final RegistryObject<SoundEvent> VIOLET_DAWN_START = register("violet_dawn_start");
	public static final RegistryObject<SoundEvent> VIOLET_DAWN_ATTACK = register("violet_dawn_attack");
	public static final RegistryObject<SoundEvent> VIOLET_DAWN_DEATH = register("violet_dawn_death");
	public static final RegistryObject<SoundEvent> VIOLET_DAWN_END = register("violet_dawn_end");
	public static final RegistryObject<SoundEvent> IRON_MAIDEN_CLOSING = register("iron_maiden_closing");
	public static final RegistryObject<SoundEvent> IRON_MAIDEN_DAMAGE = register("iron_maiden_damage");
	public static final RegistryObject<SoundEvent> IRON_MAIDEN_OPENING = register("iron_maiden_opening");
	public static final RegistryObject<SoundEvent> IRON_MAIDEN_END_MUSIC = register("iron_maiden_end_music");
	public static final RegistryObject<SoundEvent> ABANDONED_MURDERER_ESCAPE = register("abandoned_murderer_escape");
	public static final RegistryObject<SoundEvent> ABANDONED_MURDERER_WARNING = register("abandoned_murderer_warning");
	public static final RegistryObject<SoundEvent> ABANDONED_MURDERER_ATTACK = register("abandoned_murderer_attack");
	public static final RegistryObject<SoundEvent> ABANDONED_MURDERER_AMBIENT = register("abandoned_murderer_ambient");
	public static final RegistryObject<SoundEvent> LARGE_BIRD_WEAPON = register("largebird_weapon");
	public static final RegistryObject<SoundEvent> LARGE_BIRD_WALK = register("large_bird_walk");
	public static final RegistryObject<SoundEvent> LARGE_BIRD_CHARM = register("large_bird_charm");
	public static final RegistryObject<SoundEvent> LARGE_BIRD_KILLER_PLAYER = register("large_bird_killer_player");
	public static final RegistryObject<SoundEvent> LARGE_BIRD_OPEN_MOUTH = register("large_bird_open_mouth");
	public static final RegistryObject<SoundEvent> PUNISHING_BIRD_WEAPON_ATTACK = register("punishing_bird_weapon_attack");
	public static final RegistryObject<SoundEvent> PUNISHING_BIRD_ANGRY_ATTACK = register("punishing_bird_angry_attack");
	public static final RegistryObject<SoundEvent> PUNISHING_BIRD_ATTACK = register("punishing_bird_attack");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WEAPON_SPECIAL_1 = register("approval_bird_weapon_special_1");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WEAPON_SPECIAL_2 = register("approval_bird_weapon_special_2");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WEAPON_SPECIAL_3 = register("approval_bird_weapon_special_3");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WEAPON_SPECIAL_4 = register("approval_bird_weapon_special_4");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WEAPON_ATTACK = register("approval_bird_weapon_attack");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_ATTACK = register("approval_bird_attack");
	public static final RegistryObject<SoundEvent> APPROVAL_BIRD_WILL_ATTACK = register("approval_bird_will_attack");
	public static final RegistryObject<SoundEvent> FOURTH_MATCH_FLAME_WEAPON = register("fourth_match_flame_weapon");
	public static final RegistryObject<SoundEvent> FOURTH_MATCH_FLAME = register("fourth_match_flame");
	public static final RegistryObject<SoundEvent> WINGBEAT_WEAPON = register("wingbeat_weapon");
	public static final RegistryObject<SoundEvent> WINGBEAT_HEAL_HEALTH = register("wingbeat_heal_health");
	public static final RegistryObject<SoundEvent> WINGBEAT_KILL_PLAYER = register("wingbeat_kill_player");
	public static final RegistryObject<SoundEvent> WINGBEAT_FEAST = register("wingbeat_feast");
	public static final RegistryObject<SoundEvent> ARMY_IN_BLACK_PROTECT_START = register("army_in_black_protect_start");
	public static final RegistryObject<SoundEvent> ARMY_IN_BLACK_PROTECT_END = register("army_in_black_protect_end");
	public static final RegistryObject<SoundEvent> ARMY_IN_BLACK_ATTACK = register("army_in_black_attack");
	public static final RegistryObject<SoundEvent> ARMY_IN_BLACK_EXPLODE = register("army_in_black_explode");
	public static final RegistryObject<SoundEvent> QUEEN_BEE_IDLE = register("queen_bee_idle");
	public static final RegistryObject<SoundEvent> QUEEN_BEE_SPORE = register("queen_bee_spore");
	public static final RegistryObject<SoundEvent> WORKER_BEE_ATTACK = register("worker_bee_attack");
	public static final RegistryObject<SoundEvent> WORKER_BEE_DEATH = register("worker_bee_death");
	public static final RegistryObject<SoundEvent> WORKER_BEE_DEATH2 = register("worker_bee_death2");
	public static final RegistryObject<SoundEvent> WORKER_BEE_SPAWN = register("worker_bee_spawn");
	public static final RegistryObject<SoundEvent> LETICIA_IDLE = register("leticia_idle");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_SPAWN = register("leticia_friend_spawn");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_IDLE_1 = register("leticia_friend_idle_1");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_IDLE_2 = register("leticia_friend_idle_2");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_IDLE_3 = register("leticia_friend_idle_3");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_DEATH = register("leticia_friend_death");
	public static final RegistryObject<SoundEvent> LETICIA_FRIEND_KILL_PLAYER = register("leticia_friend_kill_player");
	public static final RegistryObject<SoundEvent> PPODAE_DEATH = register("ppodae_death");
	public static final RegistryObject<SoundEvent> PPODAE_ATTACK = register("ppodae_attack");
	public static final RegistryObject<SoundEvent> PPODAE_RETURN = register("ppodae_return");
	public static final RegistryObject<SoundEvent> PPODAE_WORK_START = register("ppodae_work_start");
	public static final RegistryObject<SoundEvent> ESCAPE_ALERT_BGM_0 = register("escape_alert_bgm_0");
	public static final RegistryObject<SoundEvent> ESCAPE_ALERT_BGM_1 = register("escape_alert_bgm_1");
	public static final RegistryObject<SoundEvent> ESCAPE_ALERT_BGM_2 = register("escape_alert_bgm_2");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_IDLE = register("snow_queen_idle");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_DUEL = register("snow_queen_duel");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_DUEL_FAIL = register("snow_queen_duel_fail");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_DUEL_SUCCESS = register("snow_queen_duel_success");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_SIGN_KISS = register("snow_queen_sign_kiss");
	public static final RegistryObject<SoundEvent> SNOW_QUEEN_KISS = register("snow_queen_kiss");
	public static final RegistryObject<SoundEvent> CHILDREN_GALAXY_ATTACK = register("children_galaxy_attack");
	public static final RegistryObject<SoundEvent> CHILDREN_GALAXY_WEAPON = register("children_galaxy_weapon");
	public static final RegistryObject<SoundEvent> HAPPY_TEDDY_IDLE = register("happy_teddy_idle");
	public static final RegistryObject<SoundEvent> BLUE_STAR_ESCAPE = register("blue_star_escape");
	public static final RegistryObject<SoundEvent> BLUE_STAR_DIE = register("blue_star_die");
	public static final RegistryObject<SoundEvent> BLUE_STAR_ATTACK = register("blue_star_attack");
	public static final RegistryObject<SoundEvent> THORN_BUS_DEATH = register("thorn_bus_death");
	public static final RegistryObject<SoundEvent> THORN_BUS_PARTICLE = register("thorn_bus_particle");
	public static final RegistryObject<SoundEvent> THORN_BUS_FACE_ATTACK_1 = register("thorn_bus_face_attack_1");
	public static final RegistryObject<SoundEvent> THORN_BUS_FACE_ATTACK_2 = register("thorn_bus_face_attack_2");
	public static final RegistryObject<SoundEvent> THORN_BUS_USE_PUT_TO_DEATH = register("thorn_bus_use_put_to_death");
	public static final RegistryObject<SoundEvent> THORN_BUS_PUT_TO_DEATH_ATTACK = register("thorn_bus_put_to_death_attack");
	public static final RegistryObject<SoundEvent> PLAYER_DEATH = register("player_death");
	public static final RegistryObject<SoundEvent> PLAYER_DEATH_AFTER = register("player_death_after");
	public static final RegistryObject<SoundEvent> PLAYER_LOW_HEALTH = register("player_low_health");
	public static final RegistryObject<SoundEvent> THORN_BUS_WEAPON = register("thorn_bus_weapon");
	public static final RegistryObject<SoundEvent> END_BIRD_EGG_HIGH = register("end_bird_egg_high");
	public static final RegistryObject<SoundEvent> END_BIRD_EGG_SMALL = register("end_bird_egg_small");
	public static final RegistryObject<SoundEvent> END_BIRD_EGG_EYE = register("end_bird_egg_eye");
	public static final RegistryObject<SoundEvent> END_BIRD_DIE = register("end_bird_die");
	public static final RegistryObject<SoundEvent> END_BIRD_TRANSFER = register("end_bird_transfer");
	public static final RegistryObject<SoundEvent> END_BIRD_SKILL_1 = register("end_bird_skill1");
	public static final RegistryObject<SoundEvent> END_BIRD_SKILL_2 = register("end_bird_skill2");
	public static final RegistryObject<SoundEvent> END_BIRD_WILL_SKILL_2 = register("end_bird_will_skill2");
	public static final RegistryObject<SoundEvent> END_BIRD_ATTACK_1 = register("end_bird_attack1");
	public static final RegistryObject<SoundEvent> END_BIRD_ATTACK_2 = register("end_bird_attack2");
	public static final RegistryObject<SoundEvent> END_BIRD_WILL_ATTACK_2 = register("end_bird_will_attack2");
	public static final RegistryObject<SoundEvent> END_BIRD_SKILL_3 = register("end_bird_skill3");
	public static final RegistryObject<SoundEvent> END_BIRD_WILL_SKILL_3 = register("end_bird_will_skill3");
	public static final RegistryObject<SoundEvent> THREE_BIRDS_APPEAR = register("three_birds_appear");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_NORMAL = register("end_bird_weapon_normal");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_SPECIAL_1 = register("end_bird_weapon_special_1");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_SPECIAL_2 = register("end_bird_weapon_special_2");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_SPECIAL_3 = register("end_bird_weapon_special_3");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_SPECIAL_4 = register("end_bird_weapon_special_4");
	public static final RegistryObject<SoundEvent> END_BIRD_WEAPON_STAGE2_HIT = register("end_bird_weapon_stage2_hit");

	// 浊心斯卡蒂
	public static final RegistryObject<SoundEvent> SKADI_AMBIENT = register("skadi_ambient");
	public static final RegistryObject<SoundEvent> SKADI_ESCAPE = register("skadi_escape");

	// 伊莎玛拉
	public static final RegistryObject<SoundEvent> ISHARMLA_TO_MONSTER = register("isharmla_to_monster");
	public static final RegistryObject<SoundEvent> ISHARMLA_TO_HUMAN = register("isharmla_to_human");
	public static final RegistryObject<SoundEvent> ISHARMLA_BITE = register("isharmla_bite");
	public static final RegistryObject<SoundEvent> ISHARMLA_TAIL = register("isharmla_tail");
	public static final RegistryObject<SoundEvent> ISHARMLA_BEAM = register("isharmla_beam");
	public static final RegistryObject<SoundEvent> ISHARMLA_TEAR_SUMMON = register("isharmla_tear_summon");
	public static final RegistryObject<SoundEvent> ISHARMLA_TEAR_ATTACK = register("isharmla_tear_attack");
	public static final RegistryObject<SoundEvent> ISHARMLA_TEAR_DEATH_NATURAL = register("isharmla_tear_death_natural");
	public static final RegistryObject<SoundEvent> ISHARMLA_TEAR_DEATH_KILLED = register("isharmla_tear_death_killed");

	// 野怪清道夫
	public static final RegistryObject<SoundEvent> CLEANER_VOICE_1 = register("cleaner_voice_1");
	public static final RegistryObject<SoundEvent> CLEANER_VOICE_2 = register("cleaner_voice_2");
	public static final RegistryObject<SoundEvent> CLEANER_VOICE_3 = register("cleaner_voice_3");
	public static final RegistryObject<SoundEvent> CLEANER_VOICE_4 = register("cleaner_voice_4");
	public static final RegistryObject<SoundEvent> CLEANER_VOICE_5 = register("cleaner_voice_5");
	public static final RegistryObject<SoundEvent> CLEANER_SKILL1 = register("cleaner_skill1");
	public static final RegistryObject<SoundEvent> CLEANER_SKILL2 = register("cleaner_skill2");

	// 亡蝶葬仪
	public static final RegistryObject<SoundEvent> BUTTERFLY_ATTACK = register("butterfly_attack");
	public static final RegistryObject<SoundEvent> BUTTERFLY_SKILL_START = register("butterfly_skill_start");
	public static final RegistryObject<SoundEvent> BUTTERFLY_SKILL_LOOP = register("butterfly_skill_loop");
	public static final RegistryObject<SoundEvent> BUTTERFLY_SKILL_END = register("butterfly_skill_end");
	public static final RegistryObject<SoundEvent> BUTTERFLY_DEATH = register("butterfly_death");

	// 小红帽雇佣兵
	public static final RegistryObject<SoundEvent> REDHAT_KNIFE = register("redhat_knife");
	public static final RegistryObject<SoundEvent> REDHAT_KNIFE_RAGE = register("redhat_knife_rage");
	public static final RegistryObject<SoundEvent> REDHAT_GUN = register("redhat_gun");
	public static final RegistryObject<SoundEvent> REDHAT_THROW = register("redhat_throw");

	// 宇宙碎片
	public static final RegistryObject<SoundEvent> FRAGMENT_SING = register("fragment_sing");
	public static final RegistryObject<SoundEvent> FRAGMENT_ATTACK = register("fragment_attack");

	// 又大又可能很坏的狼
	public static final RegistryObject<SoundEvent> WOLF_STEALTH = register("wolf_stealth");
	public static final RegistryObject<SoundEvent> WOLF_CLAW = register("wolf_claw");
	public static final RegistryObject<SoundEvent> WOLF_IDLE = register("wolf_idle");
	public static final RegistryObject<SoundEvent> WOLF_HOWL = register("wolf_howl");

	// 小帮手
	public static final RegistryObject<SoundEvent> HELPER_BOOT = register("helper_boot");
	public static final RegistryObject<SoundEvent> HELPER_SPIN = register("helper_spin");
	public static final RegistryObject<SoundEvent> HELPER_HIT = register("helper_hit");
}
