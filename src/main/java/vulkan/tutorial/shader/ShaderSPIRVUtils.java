package vulkan.tutorial.shader;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import vulkan.tutorial.shader.SPIRV;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ShaderSPIRVUtils {
    public static SPIRV compileShaderFile(String shaderFile, ShaderKind shaderKind){
        return compileShaderAbsoluteFile(ClassLoader.getSystemClassLoader().getResource(shaderFile).toExternalForm(), shaderKind);
    }

    private static SPIRV compileShaderAbsoluteFile(String shaderFile, ShaderKind shaderKind) {
        try{
            String source = new String(Files.readAllBytes(Paths.get(new URI(shaderFile))));
            return compileShader(shaderFile, source, shaderKind);
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {
        long compiler = Shaderc.shaderc_compiler_initialize();

        if(compiler == MemoryUtil.NULL){
            throw new RuntimeException("Failed to create shader compiler");
        }

        long result = Shaderc.shaderc_compile_into_spv(compiler, source, shaderKind.getKind(), filename, "main", MemoryUtil.NULL);

        if(result == MemoryUtil.NULL){
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if(Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success){
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V:\n" + Shaderc.shaderc_result_get_error_message(result));
        }

        Shaderc.shaderc_compiler_release(compiler);

        return new SPIRV(result, Shaderc.shaderc_result_get_bytes(result));


    }
}
