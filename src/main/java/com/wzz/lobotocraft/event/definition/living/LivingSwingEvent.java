package com.wzz.lobotocraft.event.definition.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public class LivingSwingEvent extends LivingEvent {

    public LivingSwingEvent(LivingEntity entity) {
        super(entity);
    }

    /**
     * Pre事件：挥手前触发，可取消
     */
    @Cancelable
    public static class Pre extends LivingSwingEvent {
        public Pre(LivingEntity entity) {
            super(entity);
        }
    }

    /**
     * Post事件：挥手后触发，不可取消
     */
    public static class Post extends LivingSwingEvent {
        public Post(LivingEntity entity) {
            super(entity);
        }

        @Override
        public boolean isCancelable() {
            return false;
        }
    }
}