package io.github.foundationgames.splinecart;

import java.io.PrintWriter;
import java.io.FileWriter;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;

public class Inspect {
    public static void main(String[] args) {
        try (PrintWriter out = new PrintWriter(new FileWriter("fog_parameters_info.txt"))) {
            dumpPipeline(out, "ENTITY_CUTOUT", RenderPipelines.ENTITY_CUTOUT);
            dumpPipeline(out, "ENTITY_CUTOUT_Z_OFFSET", RenderPipelines.ENTITY_CUTOUT_Z_OFFSET);
            System.out.println("Done! Written to fog_parameters_info.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void dumpPipeline(PrintWriter out, String name, RenderPipeline pipeline) {
        out.println("Pipeline: " + name);
        out.println("  Location: " + pipeline.getLocation());
        out.println("  VertexShader: " + pipeline.getVertexShader());
        out.println("  FragmentShader: " + pipeline.getFragmentShader());
        out.println("  Cull: " + pipeline.isCull());
        out.println("  FormatBindings: " + java.util.Arrays.toString(pipeline.getVertexFormatBindings()));
        out.println("  ShaderDefines: " + pipeline.getShaderDefines());
        out.println("  PolygonMode: " + pipeline.getPolygonMode());
        out.println("  ColorTargetState: " + pipeline.getColorTargetState());
        out.println("  DepthStencilState: " + pipeline.getDepthStencilState());
        out.println();
    }
}



