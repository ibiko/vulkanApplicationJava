package vulkan.tutorial;

import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.shader.AlignmentUtils;
import vulkan.tutorial.shader.UniformBufferObject;
import vulkan.tutorial.vulkan.VkGeometryInstanceNV;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    private ByteBufferUtils() {
        //Util class
    }

    public static void copyIntoBuffer(ByteBuffer byteBuffer, UniformBufferObject uniformBufferObject) {
        final int mat4size = 16 * Float.BYTES;

        uniformBufferObject.getModel().get(0, byteBuffer);
        uniformBufferObject.getView().get(AlignmentUtils.alignAs(mat4size, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
        uniformBufferObject.getProjection().get(AlignmentUtils.alignAs(mat4size * 2, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
        uniformBufferObject.getViewInverse().get(AlignmentUtils.alignAs(mat4size * 3, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
        uniformBufferObject.getProjectionInverse().get(AlignmentUtils.alignAs(mat4size * 4, AlignmentUtils.alignOf(uniformBufferObject.getView())), byteBuffer);
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

    public static void copyIntoBuffer(ByteBuffer byteBuffer, VkGeometryInstanceNV geometry) {
        for (int i = 0; i < geometry.getTransform().length; i++) {
            byteBuffer.putFloat(geometry.getTransform()[i]);
        }
        byteBuffer.putLong(geometry.getAccelerationStructureHandle());
        byteBuffer.putInt(geometry.getHitGroupId());
        byteBuffer.putInt(geometry.getInstanceId());
        byteBuffer.putInt(geometry.getMask());
    }

    public static void copyIntoBufferNew(ByteBuffer dst, ByteBuffer src, int groupHandleSize, int groupCount) {
        dst.put(0, src.get(0));
        dst.put(1, src.get(1));
        dst.put(2, src.get(2));
    }
}
