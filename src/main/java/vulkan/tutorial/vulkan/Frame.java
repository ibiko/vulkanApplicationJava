package vulkan.tutorial.vulkan;

import org.lwjgl.system.MemoryStack;

import java.nio.LongBuffer;

public class Frame {

    private final long imageAvailableSemaphore;
    private final long renderFinishedSemaphore;
    private final long fence;

    public Frame(long imageAvailableSemaphore, long renderFinishedSemaphore, long fence) {
        this.imageAvailableSemaphore = imageAvailableSemaphore;
        this.renderFinishedSemaphore = renderFinishedSemaphore;
        this.fence = fence;
    }

    public long getImageAvailableSemaphore() {
        return this.imageAvailableSemaphore;
    }

    public LongBuffer pImageAvailableSemaphore() {
        return MemoryStack.stackGet().longs(this.imageAvailableSemaphore);
    }

    public long getRenderFinishedSemaphore() {
        return this.renderFinishedSemaphore;
    }

    public LongBuffer pRenderFinishedSemaphore() {
        return MemoryStack.stackGet().longs(this.renderFinishedSemaphore);
    }

    public long getFence() {
        return this.fence;
    }

    public LongBuffer pFence() {
        return MemoryStack.stackGet().longs(this.fence);
    }
}
