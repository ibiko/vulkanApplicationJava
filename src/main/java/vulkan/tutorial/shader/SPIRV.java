package vulkan.tutorial.shader;

import org.lwjgl.system.NativeResource;

import java.nio.ByteBuffer;

public class SPIRV implements NativeResource {
    private final long handle;
    private ByteBuffer bytecode;

    public SPIRV(long handle, ByteBuffer bytecode) {
        this.handle = handle;
        this.bytecode = bytecode;
    }

    public ByteBuffer bytecode() {
        return this.bytecode;
    }

    @Override
    public void free() {

    }
}
