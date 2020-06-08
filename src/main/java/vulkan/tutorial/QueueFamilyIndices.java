package vulkan.tutorial;

import java.util.stream.IntStream;

public class QueueFamilyIndices {
    private Integer graphicsFamily;
    private Integer presentationFamily;

    public boolean isComplete() {
        return this.graphicsFamily != null && this.presentationFamily != null;
    }

    public Integer getGraphicsFamily() {
        return this.graphicsFamily;
    }

    public void setGraphicsFamily(Integer graphicsFamily) {
        this.graphicsFamily = graphicsFamily;
    }

    public Integer getPresentationFamily() {
        return this.presentationFamily;
    }

    public void setPresentationFamily(Integer presentationFamily) {
        this.presentationFamily = presentationFamily;
    }

    public int[] unique() {
        return IntStream.of(this.graphicsFamily, this.presentationFamily).distinct().toArray();
    }
}
