package vulkan.tutorial.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageMemoryBarrier;

public class BarrierHelper {
    private VkImageMemoryBarrier.Buffer barrier;
    private int sourceStage;
    private int destinationStage;

    public static BarrierHelper createBarrierHelper(long image, int format, int oldLayout, int newLayout, int mipMapLevels, MemoryStack stack) {
        BarrierHelper result = new BarrierHelper();
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.callocStack(1, stack);
        barrier.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.oldLayout(oldLayout);
        barrier.newLayout(newLayout);
        barrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
        barrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
        barrier.image(image);

        barrier.subresourceRange().baseMipLevel(0);
        barrier.subresourceRange().levelCount(mipMapLevels);
        barrier.subresourceRange().baseArrayLayer(0);
        barrier.subresourceRange().layerCount(1);

        result.setBarrier(barrier);

        if (newLayout == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT);

            if (VulkanUtils.hasStencilComponent(format)) {
                barrier.subresourceRange().aspectMask(
                        barrier.subresourceRange().aspectMask() | VK10.VK_IMAGE_ASPECT_STENCIL_BIT);
            }
        } else {
            barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        }

        if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_GENERAL) { // Storage Image
            barrier.srcAccessMask(0);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_GENERAL && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) { // Storage Image
            barrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) { // Storage Image
            barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_GENERAL) { // Storage Image
            barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT);

            result.setSourceStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            result.setDestinationStage(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        } else {
            throw new IllegalArgumentException("Unsupported layout transition");
        }
        return result;
    }

    public VkImageMemoryBarrier.Buffer getBarrier() {
        return barrier;
    }

    public void setBarrier(VkImageMemoryBarrier.Buffer barrier) {
        this.barrier = barrier;
    }

    public int getSourceStage() {
        return sourceStage;
    }

    public void setSourceStage(int sourceStage) {
        this.sourceStage = sourceStage;
    }

    public int getDestinationStage() {
        return destinationStage;
    }

    public void setDestinationStage(int destinationStage) {
        this.destinationStage = destinationStage;
    }
}
