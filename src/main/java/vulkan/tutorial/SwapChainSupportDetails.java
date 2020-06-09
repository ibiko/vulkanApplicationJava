package vulkan.tutorial;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.nio.IntBuffer;

public class SwapChainSupportDetails {
    private VkSurfaceCapabilitiesKHR capabilities;
    private VkSurfaceFormatKHR.Buffer formats;
    private IntBuffer presentMode;

    public VkSurfaceCapabilitiesKHR getCapabilities() {
        return this.capabilities;
    }

    public void setCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
        this.capabilities = capabilities;
    }

    public VkSurfaceFormatKHR.Buffer getFormats() {
        return this.formats;
    }

    public void setFormats(VkSurfaceFormatKHR.Buffer formats) {
        this.formats = formats;
    }

    public IntBuffer getPresentMode() {
        return this.presentMode;
    }

    public void setPresentMode(IntBuffer presentMode) {
        this.presentMode = presentMode;
    }
}
