package vulkan.tutorial.shader;

import org.lwjgl.util.shaderc.Shaderc;

public enum ShaderKind {
    VERTEX_SHADER(Shaderc.shaderc_glsl_vertex_shader),
    GEOMETRY_SHADER(Shaderc.shaderc_glsl_geometry_shader),
    FRAGMENT_SHADER(Shaderc.shaderc_glsl_fragment_shader),
    RAYGEN_SHADER(Shaderc.shaderc_glsl_raygen_shader),
    MISS_SHADER(Shaderc.shaderc_glsl_miss_shader),
    CLOSEST_HIT_SHADER(Shaderc.shaderc_glsl_closesthit_shader);

    private final int kind;

    ShaderKind(int kind) {
        this.kind = kind;
    }

    public int getKind() {
        return this.kind;
    }
}
