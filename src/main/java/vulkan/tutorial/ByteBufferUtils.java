package vulkan.tutorial;

import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.shader.AlignmentUtils;
import vulkan.tutorial.shader.UniformBufferObject;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    private ByteBufferUtils(){
        //Util class
    }

    public static void copyIntoBuffer(ByteBuffer byteBuffer, UniformBufferObject uniformBufferObject) {
        final int mat4size = 16 * Float.BYTES;

        uniformBufferObject.getModel().get(0, byteBuffer);
        uniformBufferObject.getView().get(AlignmentUtils.alignAs(mat4size, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
        uniformBufferObject.getProjection().get(AlignmentUtils.alignAs(mat4size * 2, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
    }

    public static void copyIntoBuffer(ByteBuffer dst, ByteBuffer src, long size) {
        src.limit((int) size);
        dst.put(src);
        src.limit(src.capacity()).rewind();
    }

    public static void copyIntoBuffer(ByteBuffer byteBuffer, int[] indices) {
        for (int index : indices) {
            byteBuffer.putInt(index);
        }

        byteBuffer.rewind();
    }

    public static void copyIntoBuffer(ByteBuffer byteBuffer, Vertex[] vertices) {
        for (Vertex vertex : vertices) {
            byteBuffer.putFloat(vertex.getPos().x());
            byteBuffer.putFloat(vertex.getPos().y());
            byteBuffer.putFloat(vertex.getPos().z());

            byteBuffer.putFloat(vertex.getColor().x());
            byteBuffer.putFloat(vertex.getColor().y());
            byteBuffer.putFloat(vertex.getColor().z());

            byteBuffer.putFloat(vertex.getTexCoords().x());
            byteBuffer.putFloat(vertex.getTexCoords().y());
        }
    }
}
