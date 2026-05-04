package com.dclient.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class EspRenderType {
    private static RenderType espLines = null;
    private static RenderType espQuads = null;

    public static RenderType getEspLines() {
        if (espLines == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("dclient", "esp_lines"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
                .build();
            espLines = RenderType.create(
                "dclient_esp_lines",
                RenderSetup.builder(pipeline).createRenderSetup()
            );
        }
        return espLines;
    }

    /** Filled quads render type — no depth test, blended. Used by LightDebug. */
    public static RenderType getEspQuads() {
        if (espQuads == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_QUADS)
                .withLocation(Identifier.fromNamespaceAndPath("dclient", "esp_quads"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
            espQuads = RenderType.create(
                "dclient_esp_quads",
                RenderSetup.builder(pipeline).createRenderSetup()
            );
        }
        return espQuads;
    }
}
