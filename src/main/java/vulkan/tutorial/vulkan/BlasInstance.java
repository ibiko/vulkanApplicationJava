package vulkan.tutorial.vulkan;

import org.joml.Matrix4f;
import org.lwjgl.vulkan.NVRayTracing;

public class BlasInstance {
    private int blasId;
    private int instanceId;
    private int hitGroupId;
    private int mask = 0xFF;
    private final int flags = NVRayTracing.VK_GEOMETRY_INSTANCE_TRIANGLE_CULL_DISABLE_BIT_NV;
    private final Matrix4f transform = new Matrix4f();

    public int getBlasId() {
        return this.blasId;
    }

    public void setBlasId(int blasId) {
        this.blasId = blasId;
    }

    public int getInstanceId() {
        return this.instanceId;
    }

    public void setInstanceId(int instanceId) {
        this.instanceId = instanceId;
    }

    public int getHitGroupId() {
        return this.hitGroupId;
    }

    public void setHitGroupId(int hitGroupId) {
        this.hitGroupId = hitGroupId;
    }

    public int getMask() {
        return this.mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }
}
