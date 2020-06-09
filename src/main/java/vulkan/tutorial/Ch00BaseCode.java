package vulkan.tutorial;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Ch00BaseCode {

    public static void main(String[] args) {
        HelloTriangleApplication app = new HelloTriangleApplication();
        app.run();
    }

    private static class HelloTriangleApplication {
        private static final int WIDTH = 800;
        private static final int HEIGHT = 600;
        private static final boolean ENABLE_VALIDATION_LAYERS = Configuration.DEBUG.get(true);
        private static final Set<String> VALIDATION_LAYERS;
        private static final Set<String> DEVICE_EXTENSIONS = Stream.of(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
                .collect(Collectors.toSet());
        private static final int UINT32_MAX = 0xFFFFFFFF;

        static {
            if (ENABLE_VALIDATION_LAYERS) {
                VALIDATION_LAYERS = new HashSet<>();
                VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_validation");
            } else {
                VALIDATION_LAYERS = null;
            }
        }

        private long window;
        private VkInstance vkInstance;
        private long debugMessenger;
        private long surface;
        private VkPhysicalDevice vkPhysicalDevice;
        private VkDevice vkDevice;
        private VkQueue vkGraphicsQueue;
        private VkQueue vkPresentQueue;

        private long swapChain;
        private List<Long> swapChainImages;
        private int swapChainImageFormat;
        private VkExtent2D swapChainExtent;

        private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
            System.err.println("Validation layer: " + callbackData.pMessageString());
            return VK10.VK_FALSE;
        }

        public void run() {
            initWindow();
            initVulkan();
            mainLoop();
            cleanup();
        }

        private boolean checkValidationLayerSupport() {
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

        private void initWindow() {
            if (!GLFW.glfwInit()) {
                throw new RuntimeException("Cannot initialize GLFW");
            }

            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);

            String title = getClass().getEnclosingClass().getSimpleName();

            this.window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, title, MemoryUtil.NULL, MemoryUtil.NULL);

            if (this.window == MemoryUtil.NULL) {
                throw new RuntimeException("Cannot create window");
            }
        }

        private void initVulkan() {
            createInstance();
            setupDebugMessenger();
            createSurface();
            pickPhysicalDevice();
            createLogicalDevice();
            createSwapChain();
        }

        private void createSwapChain() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SwapChainSupportDetails swapChainSupport = querySwapChainSupport(this.vkPhysicalDevice, stack);

                VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.getFormats());
                int presentMode = chooseSwapPresentMode(swapChainSupport.getPresentMode());
                VkExtent2D vkExtent2D = chooseSwapExtent(swapChainSupport.getCapabilities());

                IntBuffer imageCount = stack.ints(swapChainSupport.getCapabilities().minImageCount() + 1);

                if (swapChainSupport.getCapabilities().maxImageCount() > 0 && imageCount.get(0) > swapChainSupport.getCapabilities().maxImageCount()) {
                    imageCount.put(0, swapChainSupport.getCapabilities().maxImageCount());
                }

                VkSwapchainCreateInfoKHR createInfoKHR = VkSwapchainCreateInfoKHR.callocStack(stack);

                createInfoKHR.sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
                createInfoKHR.surface(this.surface);

                //Image settings
                createInfoKHR.minImageCount(imageCount.get(0));
                createInfoKHR.imageFormat(surfaceFormat.format());
                createInfoKHR.imageColorSpace(surfaceFormat.colorSpace());
//                createInfoKHR.imageExtent(vkExtent2D);
                createInfoKHR.imageArrayLayers(1);
                createInfoKHR.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

                QueueFamilyIndices indices = findQueueFamilies(this.vkPhysicalDevice);

                if (!indices.getGraphicsFamily().equals(indices.getPresentationFamily())) {
                    createInfoKHR.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT);
                    createInfoKHR.pQueueFamilyIndices(stack.ints(indices.getGraphicsFamily(), indices.getPresentationFamily()));
                } else {
                    createInfoKHR.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
                }

                createInfoKHR.preTransform(swapChainSupport.getCapabilities().currentTransform());
                createInfoKHR.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
                createInfoKHR.presentMode(presentMode);
                createInfoKHR.clipped(true);
                createInfoKHR.oldSwapchain(VK10.VK_NULL_HANDLE);

                LongBuffer pSwapChain = stack.longs(VK10.VK_NULL_HANDLE);

                if (KHRSwapchain.vkCreateSwapchainKHR(this.vkDevice, createInfoKHR, null, pSwapChain) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create swap chain");
                }

                this.swapChain = pSwapChain.get(0);

                KHRSwapchain.vkGetSwapchainImagesKHR(this.vkDevice, this.swapChain, imageCount, null);

                LongBuffer pSwapChainImages = stack.mallocLong(imageCount.get(0));

                KHRSwapchain.vkGetSwapchainImagesKHR(this.vkDevice, this.swapChain, imageCount, pSwapChainImages);

                this.swapChainImages = new ArrayList<>(imageCount.get(0));

                for (int i = 0; i < pSwapChainImages.capacity(); i++) {
                    this.swapChainImages.add(pSwapChainImages.get(i));
                }

                this.swapChainImageFormat = surfaceFormat.format();
                this.swapChainExtent = VkExtent2D.create().set(vkExtent2D);
            }
        }

        private void createSurface() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pSurface = stack.longs(VK10.VK_NULL_HANDLE);

                if (GLFWVulkan.glfwCreateWindowSurface(this.vkInstance, this.window, null, pSurface) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create window surface");
                }

                this.surface = pSurface.get(0);
            }
        }

        private void createLogicalDevice() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                QueueFamilyIndices indices = findQueueFamilies(this.vkPhysicalDevice);

                int[] uniqueQueueFamilies = indices.unique();

                VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueQueueFamilies.length, stack);

                for (int i = 0; i < uniqueQueueFamilies.length; i++) {
                    VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i);
                    queueCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                    queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
                    queueCreateInfo.pQueuePriorities(stack.floats(1.0f));
                }

                VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

                VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.callocStack(stack);
                createInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
                createInfo.pQueueCreateInfos(queueCreateInfos);

                createInfo.pEnabledFeatures(deviceFeatures);

                createInfo.ppEnabledExtensionNames(asPointBuffer(DEVICE_EXTENSIONS));

                if (ENABLE_VALIDATION_LAYERS) {
                    createInfo.ppEnabledLayerNames(asPointBuffer(VALIDATION_LAYERS));
                }

                PointerBuffer pDevice = stack.pointers(VK10.VK_NULL_HANDLE);

                if (VK10.vkCreateDevice(this.vkPhysicalDevice, createInfo, null, pDevice) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create logical device");
                }

                this.vkDevice = new VkDevice(pDevice.get(0), this.vkPhysicalDevice, createInfo);
                PointerBuffer pQueue = stack.pointers(VK10.VK_NULL_HANDLE);
                VK10.vkGetDeviceQueue(this.vkDevice, indices.getGraphicsFamily(), 0, pQueue);

                this.vkGraphicsQueue = new VkQueue(pQueue.get(0), this.vkDevice);

                VK10.vkGetDeviceQueue(this.vkDevice, indices.getPresentationFamily(), 0, pQueue);
                this.vkPresentQueue = new VkQueue(pQueue.get(0), this.vkDevice);
            }
        }

        private PointerBuffer asPointBuffer(Set<String> collection) {
            MemoryStack stack = MemoryStack.stackGet();

            PointerBuffer buffer = stack.mallocPointer(collection.size());

            collection.stream().map(stack::UTF8).forEach(buffer::put);

            return buffer.rewind();
        }

        private void pickPhysicalDevice() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer deviceCount = stack.ints(0);
                VK10.vkEnumeratePhysicalDevices(this.vkInstance, deviceCount, null);

                if (deviceCount.get(0) == 0) {
                    throw new RuntimeException("Failed to find GPUs with Vulkan support");
                }

                PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

                VK10.vkEnumeratePhysicalDevices(this.vkInstance, deviceCount, ppPhysicalDevices);

                VkPhysicalDevice device = null;

                for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
                    device = new VkPhysicalDevice(ppPhysicalDevices.get(i), this.vkInstance);

                    if (isDeviceSuitable(device)) {
                        break;
                    }
                }

                if (device == null) {
                    throw new RuntimeException("Failed to find a suitable GPU");
                }

                this.vkPhysicalDevice = device;
            }
        }

        private SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice physicalDevice, MemoryStack stack) {
            SwapChainSupportDetails details = new SwapChainSupportDetails();

            details.setCapabilities(VkSurfaceCapabilitiesKHR.mallocStack(stack));
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, this.surface, details.getCapabilities());

            IntBuffer count = stack.ints(0);

            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, this.surface, count, null);

            if (count.get(0) != 0) {
                details.setFormats(VkSurfaceFormatKHR.mallocStack(count.get(0), stack));
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, this.surface, count, details.getFormats());
            }

            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, this.surface, count, null);

            if (count.get(0) != 0) {
                details.setPresentMode(stack.mallocInt(count.get(0)));
                KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, this.surface, count, details.getPresentMode());
            }

            return details;
        }

        private boolean isDeviceSuitable(VkPhysicalDevice device) {
            QueueFamilyIndices indices = findQueueFamilies(device);

            boolean swapChainAdequate = false;
            boolean extensionsSupported = checkDeviceExtensionSupported(device);
            if (extensionsSupported) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    SwapChainSupportDetails swapChainSupportDetails = querySwapChainSupport(device, stack);
                    swapChainAdequate = swapChainSupportDetails.getFormats().hasRemaining() && swapChainSupportDetails.getPresentMode().hasRemaining();
                }
            }

            return indices.isComplete() && extensionsSupported && swapChainAdequate;
        }

        private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
            return formats.stream()
                    .filter(format -> format.format() == VK10.VK_FORMAT_B8G8R8_UNORM)
                    .filter(format -> format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    .findAny()
                    .orElse(formats.get(0));
        }

        private int chooseSwapPresentMode(IntBuffer formats) {
            for (int i = 0; i < formats.capacity(); i++) {
                if (formats.get(i) == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                    return formats.get(i);
                }
            }
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }

        private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {
            if (capabilities.currentExtent().width() != UINT32_MAX) {
                return capabilities.currentExtent();
            }

            VkExtent2D actualExtent = VkExtent2D.mallocStack().set(WIDTH, HEIGHT);

            VkExtent2D minExtent = capabilities.minImageExtent();
            VkExtent2D maxExtent = capabilities.maxImageExtent();

            actualExtent.width(clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
            actualExtent.height(clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));

            return actualExtent;
        }

        private int clamp(int min, int max, int value) {
            return Math.max(min, Math.min(max, value));
        }

        private boolean checkDeviceExtensionSupported(VkPhysicalDevice device) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer extensionCount = stack.ints(0);
                VK10.vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

                VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);

                return availableExtensions.stream().collect(Collectors.toSet()).containsAll(DEVICE_EXTENSIONS);
            }
        }

        private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice vkPhysicalDevice) {
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

                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkPhysicalDevice, i, this.surface, presentSupport);

                    if (presentSupport.get(0) == VK10.VK_TRUE) {
                        queueFamilyIndices.setPresentationFamily(i);
                    }
                }

                return queueFamilyIndices;
            }
        }

        private void setupDebugMessenger() {
            if (!ENABLE_VALIDATION_LAYERS) {
                return;
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDebugUtilsMessengerCreateInfoEXT createInfoEXT = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);

                populateDebugMessengerCreateInfo(createInfoEXT);

                LongBuffer pDebugMessenger = stack.longs(VK10.VK_NULL_HANDLE);

                if (createDebugUtilsMessengerEXT(this.vkInstance, createInfoEXT, null, pDebugMessenger) != VK11.VK_SUCCESS) {
                    throw new RuntimeException("Failed to setup debug messenger");
                }

                this.debugMessenger = pDebugMessenger.get(0);
            }
        }

        private int createDebugUtilsMessengerEXT(VkInstance vkInstance, VkDebugUtilsMessengerCreateInfoEXT createInfoEXT, VkAllocationCallbacks vkAllocationCallbacks, LongBuffer pDebugMessenger) {
            if (VK10.vkGetInstanceProcAddr(vkInstance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
                return EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(vkInstance, createInfoEXT, vkAllocationCallbacks, pDebugMessenger);
            }

            return VK10.VK_ERROR_EXTENSION_NOT_PRESENT;
        }

        private void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT createInfoEXT) {
            createInfoEXT.sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
            createInfoEXT.messageSeverity(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
            createInfoEXT.messageType(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
            createInfoEXT.pfnUserCallback(HelloTriangleApplication::debugCallback);
        }

        private void createInstance() {

            if (ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
                throw new RuntimeException("Validation requested but not supported");
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);

                appInfo.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO);
                appInfo.pApplicationName(stack.UTF8Safe("Hello Triangle"));
                appInfo.applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0));
                appInfo.pEngineName(stack.UTF8Safe("No Engine"));
                appInfo.engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0));
                appInfo.apiVersion(VK11.VK_API_VERSION_1_1);

                IntBuffer extensionCount = stack.mallocInt(1);
                VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCount, null);
                VkExtensionProperties.Buffer buffer = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);
                VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCount, buffer);

                buffer.forEach(extensionProperty -> System.out.println(extensionProperty.extensionNameString()));

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack);
                createInfo.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
                createInfo.pApplicationInfo(appInfo);

                createInfo.ppEnabledExtensionNames(getRequiredExtensions());

                if (ENABLE_VALIDATION_LAYERS) {
                    createInfo.ppEnabledLayerNames(asPointBuffer(VALIDATION_LAYERS));
                    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
                    populateDebugMessengerCreateInfo(debugCreateInfo);
                    createInfo.pNext(debugCreateInfo.address());
                }

                PointerBuffer instancePtr = stack.mallocPointer(1);

                if (VK10.vkCreateInstance(createInfo, null, instancePtr) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create instance");
                }


                this.vkInstance = new VkInstance(instancePtr.get(0), createInfo);
            }

        }

        private PointerBuffer getRequiredExtensions() {
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

        private void mainLoop() {
            while (!GLFW.glfwWindowShouldClose(this.window)) {
                GLFW.glfwPollEvents();
            }
        }

        private void cleanup() {
            KHRSwapchain.vkDestroySwapchainKHR(this.vkDevice, this.swapChain, null);
            VK10.vkDestroyDevice(this.vkDevice, null);

            if (ENABLE_VALIDATION_LAYERS) {
                destroyDebugUtilsMessengerEXT(this.vkInstance, this.debugMessenger, null);
            }

            //must be destroyed before the instance
            KHRSurface.vkDestroySurfaceKHR(this.vkInstance, this.surface, null);

            VK10.vkDestroyInstance(this.vkInstance, null);
            GLFW.glfwDestroyWindow(this.window);
            GLFW.glfwTerminate();
        }

        private void destroyDebugUtilsMessengerEXT(VkInstance vkInstance, long debugMessenger, VkAllocationCallbacks vkAllocationCallbacks) {
            if (VK10.vkGetInstanceProcAddr(vkInstance, "vkDestroyDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
                EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vkInstance, debugMessenger, vkAllocationCallbacks);
            }
        }
    }
}
