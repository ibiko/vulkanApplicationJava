package vulkan.tutorial;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

public class Window {

    private int width = 800;
    private int height = 600;
    private long windowHandle;
    private boolean windowResized;

    public Window(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void initWindow() {
        if (!GLFW.glfwInit()) {
            throw new RuntimeException("Cannot initialize GLFW");
        }

        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        String title = "Java-lwjgl-Vulkan-Rendering";

        this.windowHandle = GLFW.glfwCreateWindow(this.width, this.height, title, MemoryUtil.NULL, MemoryUtil.NULL);

        if (this.windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Cannot create window");
        }

        GLFW.glfwSetFramebufferSizeCallback(this.windowHandle, this::frameBufferSizeCallback);
    }

    private void frameBufferSizeCallback(long window, int width, int height) {
        this.windowResized = true;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public long getWindowHandle() {
        return this.windowHandle;
    }

    public boolean isWindowResized() {
        return this.windowResized;
    }

    public void setWindowResized(boolean windowResized) {
        this.windowResized = windowResized;
    }
}
