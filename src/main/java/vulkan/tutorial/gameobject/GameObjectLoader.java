package vulkan.tutorial.gameobject;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.assimp.Assimp;
import vulkan.tutorial.VulkanAppEntryPoint;
import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.mesh.Model;
import vulkan.tutorial.mesh.ModelLoader;

import java.io.File;
import java.net.URL;

public class GameObjectLoader {

    private GameObjectLoader(){
        //No members defined
    }

    public static GameObject loadModel(String path) {
        //TODO check if classpath:URI can work
        URL url = VulkanAppEntryPoint.class.getClassLoader().getResource(path);

        if (url == null) {
            throw new RuntimeException("Cant find resource on the path: " + path);
        }

        File modelFile = new File(url.getFile());
        Model model = ModelLoader.loadModel(modelFile, Assimp.aiProcess_FlipUVs | Assimp.aiProcess_DropNormals);

        final int vertexCount = model.getPositions().size();

        Vertex[] vertices = new Vertex[vertexCount];

        final Vector3fc color = new Vector3f(1.0f, 1.0f, 1.0f);

        for (int i = 0; i < vertexCount; i++) {
            vertices[i] = new Vertex(
                    model.getPositions().get(i),
                    color,
                    model.getTexCoords().get(i)
            );
        }

        int[] indices = new int[model.getIndices().size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = model.getIndices().get(i);
        }

        return new GameObject(null, vertices, indices);
    }
}
