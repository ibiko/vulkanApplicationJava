package vulkan.tutorial.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.Set;
import java.util.stream.Collectors;

import static vulkan.tutorial.vulkan.ValidationLayers.ENABLE_VALIDATION_LAYERS;

public class VulkanUtils {

    private VulkanUtils() {
        //Utility class
    }

    public static int extractTheCorrectMemoryTypeFromPhysicalDevice(int typeFilter, int properties, VkPhysicalDevice vkPhysicalDevice) {
        VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.mallocStack();
        VK10.vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, memoryProperties);

        for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0 && (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }

        throw new RuntimeException("Failed to find suitable memory type");
    }

    public static boolean hasStencilComponent(int format) {
        return format == VK10.VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK10.VK_FORMAT_D24_UNORM_S8_UINT;
    }

    public static int findSupportedFormat(IntBuffer formatCandidates, int tiling, int features, VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.callocStack(stack);
            for (int i = 0; i < formatCandidates.capacity(); i++) {
                int format = formatCandidates.get(i);
                VK10.vkGetPhysicalDeviceFormatProperties(vkPhysicalDevice, format, properties);

                if (isImageTilingLinear(tiling, features, properties) || isImageTilingOptimal(tiling, features, properties)) {
                    return format;
                }
            }
        }

        throw new RuntimeException("Failed to find supported format");
    }

    private static boolean isImageTilingOptimal(int tiling, int features, VkFormatProperties properties) {
        return tiling == VK10.VK_IMAGE_TILING_OPTIMAL && (properties.optimalTilingFeatures() & features) == features;
    }

    private static boolean isImageTilingLinear(int tiling, int features, VkFormatProperties properties) {
        return tiling == VK10.VK_IMAGE_TILING_LINEAR && (properties.linearTilingFeatures() & features) == features;
    }

    public static int chooseSwapPresentMode(IntBuffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            if (formats.get(i) == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                return formats.get(i);
            }
        }
        System.out.println("VK_PRESENT_MODE_MAILBOX_KHR not supported! VK_PRESENT_MODE_FIFO_KHR will be used as a fallback instead");
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    public static VkSurfaceFormatKHR findBestSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        return formats.stream()
                .filter(format -> format.format() == VK10.VK_FORMAT_B8G8R8_SRGB)
                .filter(format -> format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                .findAny()
                .orElse(formats.get(0));
    }

    public static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    public static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    public static int findMaxUsableSampleCount(VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
            VK10.vkGetPhysicalDeviceProperties(vkPhysicalDevice, physicalDeviceProperties);

            int sampleCountFlags = physicalDeviceProperties.limits().framebufferColorSampleCounts()
                    & physicalDeviceProperties.limits().framebufferDepthSampleCounts();

            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_64_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_64_BIT;
            }
            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_32_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_32_BIT;
            }
            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_16_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_16_BIT;
            }
            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_8_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_8_BIT;
            }
            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_4_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_4_BIT;
            }
            if ((sampleCountFlags & VK10.VK_SAMPLE_COUNT_2_BIT) != 0) {
                return VK10.VK_SAMPLE_COUNT_2_BIT;
            }
            return VK10.VK_SAMPLE_COUNT_1_BIT;
        }
    }

    public static int findSupportedDepthFormat(VkPhysicalDevice vkPhysicalDevice) {
        return findSupportedFormat(
                MemoryStack.stackGet().ints(VK10.VK_FORMAT_D32_SFLOAT, VK10.VK_FORMAT_D32_SFLOAT_S8_UINT, VK10.VK_FORMAT_D24_UNORM_S8_UINT),
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT, vkPhysicalDevice);
    }

    public static boolean isExtensionsSupported(VkPhysicalDevice device, Set<String> requiredDeviceExtensions) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionCount = stack.ints(0);
            VK10.vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);

            return availableExtensions.stream().collect(Collectors.toSet()).containsAll(requiredDeviceExtensions);
        }
    }

    public static QueueFamilyIndices findQueueFamiliesFromPhysicalDevice(VkPhysicalDevice vkPhysicalDevice, long surface) {
        QueueFamilyIndices queueFamilyIndices = new QueueFamilyIndices();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer queueFamilyCount = stack.ints(0);

            VK10.vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            VK10.vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK10.VK_FALSE);

            for (int i = 0; i < queueFamilies.capacity() || !queueFamilyIndices.isComplete(); i++) {
                if ((queueFamilies.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    queueFamilyIndices.setGraphicsFamily(i);
                }

                KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkPhysicalDevice, i, surface, presentSupport);

                if (presentSupport.get(0) == VK10.VK_TRUE) {
                    queueFamilyIndices.setPresentationFamily(i);
                }
            }

            return queueFamilyIndices;
        }
    }

    public static SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice physicalDevice, MemoryStack stack, long surface) {
        SwapChainSupportDetails details = new SwapChainSupportDetails();

        details.setCapabilities(VkSurfaceCapabilitiesKHR.mallocStack(stack));
        KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, details.getCapabilities());

        IntBuffer count = stack.ints(0);

        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null);

        if (count.get(0) != 0) {
            details.setFormats(VkSurfaceFormatKHR.mallocStack(count.get(0), stack));
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, details.getFormats());
        }

        KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, null);

        if (count.get(0) != 0) {
            details.setPresentMode(stack.mallocInt(count.get(0)));
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, details.getPresentMode());
        }

        return details;
    }

    public static boolean isDeviceSuitable(VkPhysicalDevice vkPhysicalDevice, long surface, Set<String> requiredDeviceExtensions) {
        QueueFamilyIndices indices = findQueueFamiliesFromPhysicalDevice(vkPhysicalDevice, surface);

        boolean swapChainAdequate = false;
        boolean anisotropySupported = false;

        boolean extensionsSupported = isExtensionsSupported(vkPhysicalDevice, requiredDeviceExtensions);
        if (extensionsSupported) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SwapChainSupportDetails swapChainSupportDetails = querySwapChainSupport(vkPhysicalDevice, stack, surface);
                swapChainAdequate = swapChainSupportDetails.getFormats().hasRemaining() && swapChainSupportDetails.getPresentMode().hasRemaining();
                VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
                VK10.vkGetPhysicalDeviceFeatures(vkPhysicalDevice, supportedFeatures);
                anisotropySupported = supportedFeatures.samplerAnisotropy();
            }
        }

        return indices.isComplete() && extensionsSupported && swapChainAdequate && anisotropySupported;
    }

    public static PointerBuffer fetchMandatoryExtensions() {
        PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();

        if (ENABLE_VALIDATION_LAYERS && glfwExtensions != null) {
            MemoryStack stack = MemoryStack.stackGet();
            PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);

            extensions.put(glfwExtensions);
            extensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));

            return extensions.rewind();
        }

        return glfwExtensions;
    }
}
