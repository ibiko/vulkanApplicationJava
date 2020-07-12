package vulkan.tutorial.vulkan;

public class VkGeometryInstanceNV {

    public static final int SIZE_OF = 12 * Float.BYTES + 4 * Integer.BYTES + Long.BYTES;

    /// Transform matrix, containing only the top 3 rows
    private float[] transform = new float[12];
    /// Instance index
    private int instanceId;
    /// Visibility mask
    private int mask;
    /// Index of the hit group which will be invoked when a ray hits the instance
    private int hitGroupId;
    /// Instance flags, such as culling
    private int flags;
    /// Opaque handle of the bottom-level acceleration structure
    long accelerationStructureHandle;

    public VkGeometryInstanceNV(float[] transform, int instanceId, int mask, int hitGroupId, int flags, long accelerationStructureHandle) {
        this.transform = transform;
        this.instanceId = instanceId;
        this.mask = mask;
        this.hitGroupId = hitGroupId;
        this.flags = flags;
        this.accelerationStructureHandle = accelerationStructureHandle;
    }

    public float[] getTransform() {
        return this.transform;
    }

    public void setTransform(float[] transform) {
        this.transform = transform;
    }

    public int getInstanceId() {
        return this.instanceId;
    }

    public void setInstanceId(int instanceId) {
        this.instanceId = instanceId;
    }

    public int getMask() {
        return this.mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    public int getHitGroupId() {
        return this.hitGroupId;
    }

    public void setHitGroupId(int hitGroupId) {
        this.hitGroupId = hitGroupId;
    }

    public int getFlags() {
        return this.flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public long getAccelerationStructureHandle() {
        return this.accelerationStructureHandle;
    }

    public void setAccelerationStructureHandle(long accelerationStructureHandle) {
        this.accelerationStructureHandle = accelerationStructureHandle;
    }
}
