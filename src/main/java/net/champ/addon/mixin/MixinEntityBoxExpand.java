package net.champ.addon.mixin;

import net.champ.client.module.impl.combat.InstantPack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Vergroot hitboxes voor aiming: kopie van de vanilla-logica met extra inflate
 * voor LivingEntity-targets + verlengde range-cap via ReachModule.
 * Alleen actief als ExpandHitboxes (of Reach) aanstaat, anders vanilla-flow.
 */
@Mixin(targets = "net.minecraft.world.entity.projectile.ProjectileUtil")
public abstract class MixinEntityBoxExpand {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
            + "Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void champ$expandHitbox(Entity entity, Vec3 min, Vec3 max, AABB box,
                                           Predicate<Entity> predicate, double range,
                                           CallbackInfoReturnable<EntityHitResult> cir) {
        float expand = InstantPack.AddonState.hitboxExpand;
        float reach = InstantPack.AddonState.reachBonus + InstantPack.AddonState.spearReachBonus;
        if (expand <= 0.001F && reach <= 0.001F) return;
        double best = range + reach;
        Entity hit = null;
        Vec3 pos = null;
        for (Entity candidate : entity.level().getEntities(entity, box, predicate)) {
            double inflate = candidate.getPickRadius();
            if (candidate instanceof LivingEntity) inflate += expand;
            AABB grown = candidate.getBoundingBox().inflate(inflate);
            Optional<Vec3> clip = grown.clip(min, max);
            if (clip.isEmpty()) continue;
            double dist = min.distanceToSqr(clip.get());
            if (dist < best) {
                hit = candidate;
                pos = clip.get();
                best = dist;
            }
        }
        cir.setReturnValue(hit == null ? null : new EntityHitResult(hit, pos));
    }
}
