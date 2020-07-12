package vulkan.tutorial.vulkan;

import org.joml.Vector3f;
import org.joml.Vector4f;

public class RtPushConstant {
    public static final int SIZE_OF = 3 * Float.BYTES + 4 * Float.BYTES + Float.BYTES + Integer.BYTES;

    public static final int OFFSETOF_POS = 0;
    public static final int OFFSETOF_COLOR = 3 * Float.BYTES;
    public static final int OFFSETOF_LIGHT = 7 * Float.BYTES;
    public static final int OFFSETOF_LIGHT_TYPE = 8 * Float.BYTES;

    private Vector3f lightPosition = new Vector3f();
    private Vector4f clearColor = new Vector4f();
    private float lightIntensity;
    private int lightType;

    public RtPushConstant() {
    }

    public Vector4f getClearColor() {
        return this.clearColor;
    }

    public void setClearColor(Vector4f clearColor) {
        this.clearColor = clearColor;
    }

    public Vector3f getLightPosition() {
        return this.lightPosition;
    }

    public void setLightPosition(Vector3f lightPosition) {
        this.lightPosition = lightPosition;
    }

    public float getLightIntensity() {
        return this.lightIntensity;
    }

    public void setLightIntensity(float lightIntensity) {
        this.lightIntensity = lightIntensity;
    }

    public int getLightType() {
        return this.lightType;
    }

    public void setLightType(int lightType) {
        this.lightType = lightType;
    }
}
