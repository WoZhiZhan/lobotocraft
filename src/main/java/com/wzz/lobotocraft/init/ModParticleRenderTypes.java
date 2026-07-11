package com.wzz.lobotocraft.init;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public class ModParticleRenderTypes {

    /**
     * 使用shader的粒子渲染类型
     */
    public static final ParticleRenderType SHADER_PARTICLE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "SHADER_PARTICLE";
        }
    };

    /**
     * 使用加法混合的shader粒子渲染类型 - 更强的发光效果
     */
    public static final ParticleRenderType SHADER_PARTICLE_ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();

            // 使用加法混合
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            
            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }
            
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "SHADER_PARTICLE_ADDITIVE";
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_SPHERE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE);

            RenderSystem.depthMask(false);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "SHADER_PARTICLE_SPHERE";
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_MULTIPLY = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            // 乘法混合 - 让粒子变暗周围区域
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR,
                    GlStateManager.DestFactor.ZERO);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_SCREEN = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            // 屏幕混合 - 柔和的增亮效果
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_DOUBLE_SIDED = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);

            // 禁用背面剔除
            RenderSystem.disableCull();

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull(); // 恢复背面剔除
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_HEXAGON = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            // 使用三角形扇形绘制六边形
            buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_DIAMOND = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            // 四个三角形组成钻石形
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull(); // 恢复背面剔除
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_STAR = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE); // 星星适合发光效果
            RenderSystem.depthMask(false);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull(); // 恢复背面剔除
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_TRIANGLE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull(); // 恢复背面剔除
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };

    public static final ParticleRenderType SHADER_PARTICLE_LINE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);

            // 设置线宽
            RenderSystem.lineWidth(2.0f);

            if (ModShaders.PARTICLE_SHADER != null) {
                RenderSystem.setShader(() -> ModShaders.PARTICLE_SHADER);
            }

            buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.lineWidth(1.0f); // 恢复默认线宽
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    };
}