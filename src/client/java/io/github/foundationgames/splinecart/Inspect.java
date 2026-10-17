package io.github.foundationgames.splinecart;

import java.io.PrintWriter;
import java.io.FileWriter;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;

public class Inspect {
    public static void main(String[] args) {
        try (PrintWriter out = new PrintWriter(new FileWriter("fog_parameters_info.txt"))) {
            dumpPipeline(out, "ENTITY_CUTOUT", RenderPipelines.ENTITY_CUTOUT);
            dumpPipeline(out, "ENTITY_CUTOUT_NO_CULL_Z_OFFSET", RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET);
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
        out.println("  VertexFormat: " + pipeline.getVertexFormat());
        out.println("  ShaderDefines: " + pipeline.getShaderDefines());
        out.println("  PolygonMode: " + pipeline.getPolygonMode());
        out.println("  DepthTestFunction: " + pipeline.getDepthTestFunction());
        out.println();
    }
}
