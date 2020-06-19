package vulkan.tutorial.mesh;

import org.joml.Vector2fc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public class Model {
    private final List<Vector3fc> positions = new ArrayList<>();
    private final List<Vector2fc> texCoords = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    public List<Vector3fc> getPositions() {
        return this.positions;
    }

    public List<Vector2fc> getTexCoords() {
        return this.texCoords;
    }

    public List<Integer> getIndices() {
        return this.indices;
    }
}
