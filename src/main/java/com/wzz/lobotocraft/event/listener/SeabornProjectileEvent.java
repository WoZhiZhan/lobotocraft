package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 脊海喷吐者的喷吐投射物命中处理:
 * 命中活体造成红色伤害,并销毁箭(投射物本体伤害设为0)。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SeabornProjectileEvent {

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!arrow.getPersistentData().contains("ridgesea_spit_damage")) return;
        if (arrow.level().isClientSide) return;

        float dmg = arrow.getPersistentData().getFloat("ridgesea_spit_damage");
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target
                && target.isAlive()
                && (target instanceof Player || target instanceof EntityClerk)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            net.minecraft.world.entity.Entity owner = arrow.getOwner();
            if (target != owner) {
                net.minecraft.world.damagesource.DamageSource src =
                        (owner != null) ? DamageHelper.getDamage(owner, "red")
                            : DamageHelper.getDamage().getDamageSources().generic();
                target.hurt(src, dmg);
            }
        }
        // 命中后移除箭(改用 setImpactResult 替代已弃用的 setCanceled:
        // STOP_AT_CURRENT_NO_DAMAGE 表示在当前位置停止且不施加投射物本体伤害)
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        arrow.discard();
    }
}
