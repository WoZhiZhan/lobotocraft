package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.renderer.FullScreenRenderer;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class FullScreenRenderMessage implements IMessage {

    private Integer showDuration;
    private Integer fadeInDuration;
    private Integer fadeOutDuration;
    private ResourceLocation texturePath;
    private Integer textureSwitchDuration;
    private String text;
    private Integer textColor;
    private Integer backgroundColor;
    private Boolean forceStop;
    private Boolean stopImmediately;

    // 新增：多图轮播相关字段
    private List<ResourceLocation> textureList;
    private Integer perImageDuration; // 每张图显示多长时间后自动切换下一张

    public FullScreenRenderMessage() {}

    public Integer getShowDuration() { return showDuration; }
    public Integer getFadeInDuration() { return fadeInDuration; }
    public Integer getFadeOutDuration() { return fadeOutDuration; }
    public ResourceLocation getTexturePath() { return texturePath; }
    public Integer getTextureSwitchDuration() { return textureSwitchDuration; }
    public String getText() { return text; }
    public Integer getTextColor() { return textColor; }
    public Integer getBackgroundColor() { return backgroundColor; }
    public Boolean getForceStop() { return forceStop; }
    public Boolean getStopImmediately() { return stopImmediately; }
    public List<ResourceLocation> getTextureList() { return textureList; }
    public Integer getPerImageDuration() { return perImageDuration; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        int mask = buf.readInt();

        if ((mask & 1) != 0) showDuration = buf.readInt();
        if ((mask & 2) != 0) fadeInDuration = buf.readInt();
        if ((mask & 4) != 0) fadeOutDuration = buf.readInt();
        if ((mask & 8) != 0) texturePath = buf.readResourceLocation();
        if ((mask & 16) != 0) textureSwitchDuration = buf.readInt();
        if ((mask & 32) != 0) text = buf.readUtf();
        if ((mask & 64) != 0) textColor = buf.readInt();
        if ((mask & 128) != 0) backgroundColor = buf.readInt();
        if ((mask & 256) != 0) forceStop = buf.readBoolean();
        if ((mask & 512) != 0) stopImmediately = buf.readBoolean();
        // 新增：读取多图列表
        if ((mask & 1024) != 0) {
            int count = buf.readInt();
            textureList = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                textureList.add(buf.readResourceLocation());
            }
        }
        if ((mask & 2048) != 0) perImageDuration = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        int mask = 0;
        if (showDuration != null) mask |= 1;
        if (fadeInDuration != null) mask |= 2;
        if (fadeOutDuration != null) mask |= 4;
        if (texturePath != null) mask |= 8;
        if (textureSwitchDuration != null) mask |= 16;
        if (text != null) mask |= 32;
        if (textColor != null) mask |= 64;
        if (backgroundColor != null) mask |= 128;
        if (forceStop != null) mask |= 256;
        if (stopImmediately != null) mask |= 512;
        if (textureList != null && !textureList.isEmpty()) mask |= 1024;
        if (perImageDuration != null) mask |= 2048;

        buf.writeInt(mask);

        if (showDuration != null) buf.writeInt(showDuration);
        if (fadeInDuration != null) buf.writeInt(fadeInDuration);
        if (fadeOutDuration != null) buf.writeInt(fadeOutDuration);
        if (texturePath != null) buf.writeResourceLocation(texturePath);
        if (textureSwitchDuration != null) buf.writeInt(textureSwitchDuration);
        if (text != null) buf.writeUtf(text);
        if (textColor != null) buf.writeInt(textColor);
        if (backgroundColor != null) buf.writeInt(backgroundColor);
        if (forceStop != null) buf.writeBoolean(forceStop);
        if (stopImmediately != null) buf.writeBoolean(stopImmediately);
        // 新增：写入多图列表
        if (textureList != null && !textureList.isEmpty()) {
            buf.writeInt(textureList.size());
            for (ResourceLocation loc : textureList) {
                buf.writeResourceLocation(loc);
            }
        }
        if (perImageDuration != null) buf.writeInt(perImageDuration);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        if (ctx.getDirection().getReceptionSide().isClient()) {
            FullScreenRenderer renderer = FullScreenRenderer.getInstance();

            if (Boolean.TRUE.equals(stopImmediately)) {
                renderer.stopImmediately();
                return;
            }

            if (Boolean.TRUE.equals(forceStop)) {
                renderer.forceStop();
                return;
            }

            if (fadeInDuration != null || fadeOutDuration != null) {
                renderer.setFadeDuration(
                        fadeInDuration != null ? fadeInDuration : 1000,
                        fadeOutDuration != null ? fadeOutDuration : 1000
                );
            }

            if (backgroundColor != null) {
                renderer.setBackgroundColor(backgroundColor);
            }

            if (textureList != null && !textureList.isEmpty()) {
                int switchTime = textureSwitchDuration != null ? textureSwitchDuration : 500;
                int perDuration = perImageDuration != null ? perImageDuration : 3000; // 默认每张3秒

                renderer.setTextureQueue(textureList, perDuration, switchTime);
            } else if (texturePath != null) {
                // 单图模式（保持向后兼容）
                int switchTime = textureSwitchDuration != null ? textureSwitchDuration : 500;
                if (renderer.isRendering()) {
                    renderer.switchTexture(texturePath, switchTime);
                } else {
                    renderer.setBackgroundTexture(texturePath);
                }
            }

            if (text != null) {
                renderer.setText(text, textColor != null ? textColor : 0xFFFFFF);
            }

            if (showDuration != null) {
                renderer.startRender(showDuration);
            }
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer showDuration;
        private Integer fadeInDuration;
        private Integer fadeOutDuration;
        private ResourceLocation texturePath;
        private Integer textureSwitchDuration;
        private String text;
        private Integer textColor;
        private Integer backgroundColor;
        private Boolean forceStop;
        private Boolean stopImmediately;
        private List<ResourceLocation> textureList;
        private Integer perImageDuration;

        public Builder showDuration(int duration) {
            this.showDuration = duration;
            return this;
        }

        public Builder fadeIn(int duration) {
            this.fadeInDuration = duration;
            return this;
        }

        public Builder fadeOut(int duration) {
            this.fadeOutDuration = duration;
            return this;
        }

        public Builder fade(int in, int out) {
            this.fadeInDuration = in;
            this.fadeOutDuration = out;
            return this;
        }

        public Builder texture(ResourceLocation path) {
            this.texturePath = path;
            return this;
        }

        public Builder texture(ResourceLocation path, int switchDuration) {
            this.texturePath = path;
            this.textureSwitchDuration = switchDuration;
            return this;
        }

        /**
         * 新增：设置多图轮播列表
         * @param textures 纹理列表
         * @param perImageDuration 每张图显示时间（毫秒），切换时会用 switchDuration 做过渡
         */
        public Builder textures(List<ResourceLocation> textures, int perImageDuration) {
            this.textureList = textures;
            this.perImageDuration = perImageDuration;
            return this;
        }

        /**
         * 新增：设置多图轮播列表（使用默认每张3秒）
         */
        public Builder textures(List<ResourceLocation> textures) {
            return textures(textures, 3000);
        }

        /**
         * 设置纹理切换动画时长
         */
        public Builder switchDuration(int duration) {
            this.textureSwitchDuration = duration;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder text(String text, int color) {
            this.text = text;
            this.textColor = color;
            return this;
        }

        public Builder backgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }

        public Builder forceStop() {
            this.forceStop = true;
            return this;
        }

        public Builder stopImmediately() {
            this.stopImmediately = true;
            return this;
        }

        public FullScreenRenderMessage build() {
            FullScreenRenderMessage msg = new FullScreenRenderMessage();
            msg.showDuration = this.showDuration;
            msg.fadeInDuration = this.fadeInDuration;
            msg.fadeOutDuration = this.fadeOutDuration;
            msg.texturePath = this.texturePath;
            msg.textureSwitchDuration = this.textureSwitchDuration;
            msg.text = this.text;
            msg.textColor = this.textColor;
            msg.backgroundColor = this.backgroundColor;
            msg.forceStop = this.forceStop;
            msg.stopImmediately = this.stopImmediately;
            msg.textureList = this.textureList;
            msg.perImageDuration = this.perImageDuration;
            return msg;
        }
    }
}