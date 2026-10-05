package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.projectile.DeflectableAttack;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ranged protection and real projectile reflection share one equipment qualification. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class ThornBusDefense {
    private ThornBusDefense() {}

    public static boolean protects(LivingEntity victim) {
        return victim instanceof Player player && !victim.level().isClientSide
                && EgoArmorHelper.isFullEGO(player, "thorn_bus");
    }

    public static boolean tryDeflect(LivingEntity victim, Entity attack) {
        if (!protects(victim)) return false;
        if (attack instanceof DeflectableAttack custom) {
            custom.deflect(victim);
        } else if (attack instanceof Projectile projectile) {
            Vec3 incoming = projectile.getDeltaMovement();
            Vec3 out = incoming.lengthSqr() > 1.0E-6 ? incoming.scale(-1)
                    : victim.getLookAngle().scale(.5);
            projectile.setOwner(victim);
            projectile.setDeltaMovement(out);
            projectile.setPos(projectile.position().add(out.normalize().scale(.7)));
            projectile.hasImpulse = true;
            if (projectile instanceof AbstractArrow arrow) arrow.shakeTime = 0;
        } else return false;
        feedback(victim);
        return true;
    }

    public static void feedback(LivingEntity victim) {
        if (victim.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * .6, victim.getZ(),
                    5, .25, .25, .25, .05);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity victim
                && tryDeflect(victim, event.getProjectile())) {
            // SKIP_ENTITY keeps the projectile alive and prevents its original onHit effects.
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }
}
