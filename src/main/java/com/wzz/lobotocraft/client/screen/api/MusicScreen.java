package com.wzz.lobotocraft.client.screen.api;

import com.wzz.lobotocraft.client.audio.GuiMusicManager;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.sounds.SoundEvent;

/**
 * 带背景音乐的Screen基类
 * 继承此类的Screen会自动播放背景音乐，带淡入淡出效果
 */
@OnlyIn(Dist.CLIENT)
public abstract class MusicScreen extends Screen implements IMusicScreen {

    private SoundEvent soundEvent;
    private float maxVolume;
    private int fadeInDuration;
    private int fadeOutDuration;
    private boolean loop;
    
    /**
     * 构造函数 - 使用默认配置
     * @param title 标题
     * @param soundEvent 音乐
     */
    protected MusicScreen(Component title, SoundEvent soundEvent) {
        super(title);
        this.minecraft = Minecraft.getInstance();
        this.soundEvent = soundEvent;
        this.maxVolume = 0.5f;
        this.fadeInDuration = 20;
        this.fadeOutDuration = 20;
        this.loop = true;
    }
    
    /**
     * 构造函数 - 自定义配置
     * @param title 标题
     */
    protected MusicScreen(Component title) {
        super(title);
        this.minecraft = Minecraft.getInstance();
    }

    public void initSound(SoundEvent soundEvent, IAbnormality iAbnormality) {
        this.soundEvent = soundEvent;
        this.maxVolume = iAbnormality.getMusicMaxVolume();
        this.fadeInDuration = iAbnormality.getMusicFadeInDuration();
        this.fadeOutDuration = iAbnormality.getMusicFadeOutDuration();
        this.loop = iAbnormality.isMusicLooping();
    }
    
    @Override
    public void init() {
        super.init();
        // Screen初始化时开始播放音乐
        GuiMusicManager.getInstance().onScreenOpen(this);
    }
    
    @Override
    public void onClose() {
        // Screen关闭时停止音乐（带淡出效果）
        GuiMusicManager.getInstance().onScreenClose(this);
        super.onClose();
    }
    
    @Override
    public void tick() {
        super.tick();
        // 每tick更新音乐管理器
        GuiMusicManager.getInstance().tick();
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // 显示音乐状态（用于调试）
        if (minecraft != null && minecraft.options.renderDebug) {
            renderMusicDebugInfo(graphics);
        }
    }

    /**
     * 渲染音乐调试信息
     */
    private void renderMusicDebugInfo(GuiGraphics graphics) {
        GuiMusicManager manager = GuiMusicManager.getInstance();
        if (manager.isPlaying()) {
            String info = String.format("Music: %.2f volume", manager.getCurrentVolume());
            graphics.drawString(this.font, info, 5, 5, 0xFFFFFF, true);
        }
    }

    // ==================== IMusicScreen 接口实现 ====================
    
    @Override
    public SoundEvent getSoundEvent() {
        return soundEvent;
    }
    
    @Override
    public float getMaxVolume() {
        return maxVolume;
    }
    
    @Override
    public int getFadeInDuration() {
        return fadeInDuration;
    }
    
    @Override
    public int getFadeOutDuration() {
        return fadeOutDuration;
    }
    
    @Override
    public boolean shouldLoop() {
        return loop;
    }
    
    /**
     * 音乐开始播放时的回调
     * 子类可以重写此方法来执行自定义逻辑
     */
    @Override
    public void onMusicStart() {
        // 子类可以重写
    }
    
    /**
     * 音乐停止播放时的回调
     * 子类可以重写此方法来执行自定义逻辑
     */
    @Override
    public void onMusicStop() {
        // 子类可以重写
    }
}
