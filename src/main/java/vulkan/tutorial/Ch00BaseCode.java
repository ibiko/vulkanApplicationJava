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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


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

                if (ENABLE_VALIDATION_LAYERS) {
                    createInfo.ppEnabledLayerNames(validationLayersAsPointerBuffer());
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

        private boolean isDeviceSuitable(VkPhysicalDevice device) {
            QueueFamilyIndices indices = findQueueFamilies(device);
            return indices.isComplete();
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
                    createInfo.ppEnabledLayerNames(validationLayersAsPointerBuffer());
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

        private PointerBuffer validationLayersAsPointerBuffer() {
            MemoryStack stack = MemoryStack.stackGet();
            PointerBuffer buffer = stack.mallocPointer(VALIDATION_LAYERS.size());

            VALIDATION_LAYERS.stream().map(stack::UTF8)
                    .forEach(buffer::put);

            return buffer.rewind();
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
