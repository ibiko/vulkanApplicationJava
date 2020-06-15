package vulkan.tutorial.math;

import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.awt.*;

public class Vertex {

    public static final Vertex[] VERTICES = {
            new Vertex(new Vector2f(0.0f, -0.5f), new Vector3f(1.0f, 0.0f, 0.0f)),
            new Vertex(new Vector2f(0.5f, 0.5f), new Vector3f(0.0f, 1.0f, 0.0f)),
            new Vertex(new Vector2f(-0.5f, 0.5f), new Vector3f(1.0f, 0.0f, 1.0f))
    };

    public static final int SIZEOF = (2 + 3) * Float.BYTES;
    public static final int OFFSETOF_POS = 0;
    public static final int OFFSETOF_COLOR = 2 * Float.BYTES;

    private final Vector2fc pos;
    private final Vector3fc color;

    public Vertex(Vector2fc pos, Vector3fc color) {
        this.pos = pos;
        this.color = color;
    }

    public static VkVertexInputBindingDescription.Buffer getBindingDescription() {
        VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription.callocStack(1);
        bindingDescriptions.binding(0);
        bindingDescriptions.stride(SIZEOF);
        bindingDescriptions.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

        return bindingDescriptions;
    }

    public static VkVertexInputAttributeDescription.Buffer getAttributeDescriptions() {
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.callocStack(2);

        //Position
        VkVertexInputAttributeDescription posDescription = attributeDescriptions.get(0);
        posDescription.binding(0);
        posDescription.location(0);
        posDescription.format(VK10.VK_FORMAT_R32G32_SFLOAT);
        posDescription.offset(OFFSETOF_POS);

        //Color
        VkVertexInputAttributeDescription colorDescription = attributeDescriptions.get(1);
        colorDescription.binding(0);
        colorDescription.location(1);
        colorDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
        colorDescription.offset(OFFSETOF_COLOR);

        return attributeDescriptions.rewind();
    }

    public Vector2fc getPos() {
        return this.pos;
    }

    public Vector3fc getColor() {
        return this.color;
    }

}
