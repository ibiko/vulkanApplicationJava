package vulkan.tutorial.shader;

import org.joml.Matrix4f;

public class UniformBufferObject {
    public static final int SIZEOF = 3 * 16 * Float.BYTES;

    private Matrix4f model;
    private Matrix4f view;
    private Matrix4f proj;

    public UniformBufferObject() {
        this.model = new Matrix4f();
        this.view = new Matrix4f();
        this.proj = new Matrix4f();
    }

    public Matrix4f getModel() {
        return this.model;
    }

    public Matrix4f getView() {
        return this.view;
    }

    public Matrix4f getProj() {
        return this.proj;
    }

}
