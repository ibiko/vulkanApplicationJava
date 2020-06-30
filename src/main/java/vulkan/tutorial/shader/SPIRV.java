package vulkan.tutorial.shader;

import org.lwjgl.system.NativeResource;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

public class SPIRV implements NativeResource {
    private final long handle;
    private ByteBuffer byteCode;

    public SPIRV(long handle, ByteBuffer byteCode) {
        this.handle = handle;
        this.byteCode = byteCode;
    }

    public ByteBuffer byteCode() {
        return this.byteCode;
    }

    @Override
    public void free() {
        Shaderc.shaderc_result_release(this.handle);
        this.byteCode = null; //Help the GC
    }
}
