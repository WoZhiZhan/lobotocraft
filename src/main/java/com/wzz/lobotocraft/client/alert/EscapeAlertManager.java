package com.wzz.lobotocraft.client.alert;

import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;

/**
 * 客户端警报管理器：负责BGM切换和图标闪烁状态
 */
public class EscapeAlertManager {

    private static final EscapeAlertManager INSTANCE = new EscapeAlertManager();

    public static EscapeAlertManager getInstance() { return INSTANCE; }

    /** 当前正在播放的警报等级（-1表示无） */
    private int currentPlayingLevel = -1;

    /** 当前BGM实例 */
    private AlertBGMSoundInstance currentBGM = null;

    /** 图标闪烁剩余tick数（80tick = 4秒） */
    public int flashTicksRemaining = 0;

    /** 当前闪烁使用的图标索引（0/1/2 对应 icon1/2/3.png） */
    public int flashIconIndex = -1;

    /** 闪烁节拍控制（每10tick切换一次显示/隐藏） */
    public int flashBeatTick = 0;

    private EscapeAlertManager() {}

    /**
     * 收到服务端警报包时调用
     * 规则：同等级不重置；高等级覆盖低等级；-1停止一切
     */
    public void onAlertReceived(int newLevel) {
        if (newLevel == -1) {
            stopAll();
            return;
        }

        // 同等级且BGM正在播放时不重置（避免刷新进度）
        // 但如果BGM已经停止（如玩家刚重生），则重新播放
        if (newLevel == currentPlayingLevel && currentBGM != null && !currentBGM.isStopped()) return;

        // 低等级不覆盖高等级
        if (newLevel < currentPlayingLevel) return;

        stopBGM();
        currentPlayingLevel = newLevel;
        playBGMForLevel(newLevel);
        startFlash(newLevel);
    }

    private void playBGMForLevel(int level) {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc.getSoundManager();

        // 停止游戏内所有音乐
        sm.stop(null, net.minecraft.sounds.SoundSource.MUSIC);
        sm.stop(null, net.minecraft.sounds.SoundSource.RECORDS);

        net.minecraft.sounds.SoundEvent bgmSound = switch (level) {
            case 0 -> ModSounds.ESCAPE_ALERT_BGM_0.get(); // 任意出逃
            case 1 -> ModSounds.ESCAPE_ALERT_BGM_1.get(); // WAW/ALEPH <3
            case 2 -> ModSounds.ESCAPE_ALERT_BGM_2.get(); // ALEPH >3
            default -> null;
        };

        if (bgmSound == null) return;

        currentBGM = new AlertBGMSoundInstance(bgmSound);
        sm.play(currentBGM);
    }

    private void startFlash(int level) {
        flashIconIndex = level; // 0→icon1, 1→icon2, 2→icon3
        flashTicksRemaining = 80; // 4秒
        flashBeatTick = 0;
    }

    private void stopBGM() {
        if (currentBGM != null) {
            currentBGM.forceStop();
            Minecraft.getInstance().getSoundManager().stop(currentBGM);
            currentBGM = null;
        }
    }

    private void stopAll() {
        stopBGM();
        currentPlayingLevel = -1;
        flashTicksRemaining = 0;
        flashIconIndex = -1;
    }

    /** 每客户端tick调用一次 */
    public void tick() {
        if (flashTicksRemaining > 0) {
            flashTicksRemaining--;
            flashBeatTick++;
            if (flashTicksRemaining == 0) {
                flashIconIndex = -1;
                flashBeatTick = 0;
            }
        }
    }

    public boolean isFlashVisible() {
        if (flashTicksRemaining <= 0) return false;
        // 每10tick切换一次（闪烁效果）
        return (flashBeatTick / 10) % 2 == 0;
    }
}