package vulkan.tutorial.gameobject;

import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.mesh.Model;

public class GameObject {
    private final Model model;
    private final Vertex[] vertices;
    private final int[] indices;

    public GameObject(Model model, Vertex[] vertices, int[] indices) {
        this.model = model;
        this.vertices = vertices;
        this.indices = indices;
    }

    public Model getModel() {
        return this.model;
    }

    public Vertex[] getVertices() {
        return this.vertices;
    }

    public int[] getIndices() {
        return this.indices;
    }
}
