package vulkan.tutorial.vulkan;

import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidationLayers {

    public static final boolean ENABLE_VALIDATION_LAYERS = Configuration.DEBUG.get(true);
    public static final Set<String> VALIDATION_LAYERS;

    static {
        if (ENABLE_VALIDATION_LAYERS) {
            VALIDATION_LAYERS = new HashSet<>();
            VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_validation");
        } else {
            VALIDATION_LAYERS = null;
        }
    }

    private long debugMessenger;

    private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        System.err.println("Validation layer: " + callbackData.pMessageString());
        return VK10.VK_FALSE;
    }

    public static VkDebugUtilsMessengerCreateInfoEXT createVkDebugUtilsMessengerCreateInfoExt(MemoryStack stack) {
        VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
        debugCreateInfo.sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
        debugCreateInfo.messageSeverity(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
        debugCreateInfo.messageType(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
        debugCreateInfo.pfnUserCallback(ValidationLayers::debugCallback);
        return debugCreateInfo;
    }

    public static boolean checkValidationLayerSupport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer layerCount = stack.ints(0);

            VK10.vkEnumerateInstanceLayerProperties(layerCount, null);

            VkLayerProperties.Buffer availableLayers = VkLayerProperties.mallocStack(layerCount.get(0), stack);
            VK10.vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

            Set<String> availableLayerNames = availableLayers.stream().map(VkLayerProperties::layerNameString)
                    .collect(Collectors.toSet());

            return availableLayerNames.containsAll(VALIDATION_LAYERS);
        }
    }

    private static int createDebugUtilsMessengerEXT(VkInstance vkInstance, VkDebugUtilsMessengerCreateInfoEXT createInfoEXT, VkAllocationCallbacks vkAllocationCallbacks, LongBuffer pDebugMessenger) {
        if (VK10.vkGetInstanceProcAddr(vkInstance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
            return EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(vkInstance, createInfoEXT, vkAllocationCallbacks, pDebugMessenger);
        }

        return VK10.VK_ERROR_EXTENSION_NOT_PRESENT;
    }

    public void setupDebugMessenger(VkInstance vkInstance) {
        if (!ENABLE_VALIDATION_LAYERS) {
            this.debugMessenger = 0;
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfoExt = createVkDebugUtilsMessengerCreateInfoExt(stack);

            LongBuffer pDebugMessenger = stack.longs(VK10.VK_NULL_HANDLE);

            if (createDebugUtilsMessengerEXT(vkInstance, createInfoExt, null, pDebugMessenger) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to setup debug messenger");
            }

            this.debugMessenger = pDebugMessenger.get(0);
        }
    }

    public void destroyDebugUtilsMessengerEXT(VkInstance vkInstance, VkAllocationCallbacks vkAllocationCallbacks) {
        if (VK10.vkGetInstanceProcAddr(vkInstance, "vkDestroyDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vkInstance, this.debugMessenger, vkAllocationCallbacks);
        }
    }
}
