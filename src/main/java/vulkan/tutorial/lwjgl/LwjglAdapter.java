package vulkan.tutorial.lwjgl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;

import java.util.Collection;
import java.util.List;

public class LwjglAdapter {
    private LwjglAdapter(){
        //Adapter class
    }

    public static PointerBuffer asPointBuffer(List<? extends Pointer> list) {
        MemoryStack stack = MemoryStack.stackGet();
        PointerBuffer buffer = stack.mallocPointer(list.size());

        list.forEach(buffer::put);

        return buffer.rewind();
    }

    public static PointerBuffer asPointBuffer(Collection<String> collection) {
        MemoryStack stack = MemoryStack.stackGet();

        PointerBuffer buffer = stack.mallocPointer(collection.size());

        collection.stream().map(stack::UTF8).forEach(buffer::put);

        return buffer.rewind();
    }
}
