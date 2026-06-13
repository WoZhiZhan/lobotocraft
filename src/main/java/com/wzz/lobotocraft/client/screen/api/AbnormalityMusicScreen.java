package com.wzz.lobotocraft.client.screen.api;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 异想体管理界面基类（带音乐）
 * 自动从异想体读取音乐配置
 * 使用方法：
 * public class OneBadManagementScreen extends AbnormalityMusicScreen {
 *     public OneBadManagementScreen(IAbnormality abnormality) {
 *         super(Component.literal("一罪与百善"), abnormality);
 *     }
 *     // 实现界面逻辑...
 * }
 * 音乐配置：
 * 如果异想体的hasManagementMusic()返回false或getManagementMusic()返回null，
 * 则不播放音乐（但Screen仍然可以正常使用）
 */
@OnlyIn(Dist.CLIENT)
public abstract class AbnormalityMusicScreen extends MusicScreen {
    
    protected final IAbnormality abnormality;
    
    /**
     * 构造函数 - 自动从异想体读取音乐配置
     * 
     * @param title 界面标题
     * @param abnormalityId 异想体ID
     */
    protected AbnormalityMusicScreen(Component title, int abnormalityId) {
        super(title);
        Entity entity = minecraft.level.getEntity(abnormalityId);
        if (entity instanceof IAbnormality iAbnormality) {
            this.abnormality = iAbnormality;
            if (iAbnormality.hasManagementMusic()) {
                SoundEvent music = iAbnormality.getManagementMusic();
                if (music != null) {
                    this.initSound(music, iAbnormality);
                }
            }
        } else {
            this.abnormality = null;
        }
    }
    
    /**
     * 安全地从异想体获取音乐
     * 如果异想体没有启用音乐或音乐为null，返回null
     * GuiMusicManager会自动处理null的情况（不播放音乐）
     */
    private static SoundEvent getSafeMusicFromAbnormality(IAbnormality abnormality) {
        if (abnormality.hasManagementMusic()) {
            return abnormality.getManagementMusic();
        }
        return null;  // 不播放音乐
    }
    
    /**
     * 获取音乐分类
     * 使用异想体的分类标识
     */
    @Override
    public String getMusicCategory() {
        return abnormality.getMusicCategory();
    }
}