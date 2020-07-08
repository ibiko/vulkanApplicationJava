package vulkan.tutorial;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import vulkan.tutorial.gameobject.GameObject;
import vulkan.tutorial.gameobject.GameObjectLoader;
import vulkan.tutorial.lwjgl.LwjglAdapter;
import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.shader.SPIRV;
import vulkan.tutorial.shader.ShaderKind;
import vulkan.tutorial.shader.ShaderSPIRVUtils;
import vulkan.tutorial.shader.UniformBufferObject;
import vulkan.tutorial.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static vulkan.tutorial.vulkan.ValidationLayers.ENABLE_VALIDATION_LAYERS;

class VulkanApp {

    /*  Vulkan Components
        #instance
        #physicalDevice
        #logicalDevice

        GLFW
        #window

        #surface

        #swapChain
        #swapChainAttachments
        #swapChainImages
        #swapChainImageViews
        #swapChainExtent
        #swapChainFrameBuffers

        #presentationQueue
        #graphicalQueue

        #renderingPipeline
        #renderPass

        #commandPool
        #commandBuffers -> same size as swapChainImages
    */

    private static final Set<String> REQUIRED_DEVICE_EXTENSIONS = Set.of(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME, NVRayTracing.VK_NV_RAY_TRACING_EXTENSION_NAME);
    private static final int UINT32_MAX = 0xFFFFFFFF;
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final long NO_TIMEOUT = 0xFFFFFFFFFFFFFFFFL; // UINT64_MAX

    private VkInstance vkInstance;
    private long surface;
    private VkPhysicalDevice vkPhysicalDevice;
    private VkDevice vkDevice;

    private VkQueue vkGraphicsQueue;
    private VkQueue vkPresentQueue;

    private long swapChain;
    private List<Long> swapChainImages;
    private List<Long> swapChainImageViews;
    private int swapChainImageFormat;
    private VkExtent2D swapChainExtent;

    private List<Long> swapChainFrameBuffers;

    private long descriptorPool;
    private long descriptorSetLayout;
    private List<Long> descriptorSets;

    private long pipelineLayout;
    private long renderPass;
    private long graphicsPipeline;

    private long commandPool;
    private List<VkCommandBuffer> commandBuffers;

    private long colorImage;
    private long colorImageMemory;
    private long colorImageView;

    private long depthImage;
    private long depthImageMemory;
    private long depthImageView;

    private int mipLevels;
    private int msaaSamples = VK10.VK_SAMPLE_COUNT_1_BIT;

    private long textureImage;
    private long textureImageMemory;
    private long textureImageView;
    private long textureSampler;

    private Window window;
    private ValidationLayers validationLayers;
    private GameObject gameObject;

    private long vertexBuffer;
    private long vertexBufferMemory;

    private long indexBuffer;
    private long indexBufferMemory;

    private List<Long> uniformBuffers;
    private List<Long> uniformBuffersMemory;
    private List<Frame> inFlightFrames;
    private Map<Integer, Frame> imagesInFlight;
    private int currentFrame;
    private long blas;
    private long blasMemory;

    private static long createTextureSampler(VkDevice vkDevice, int mipLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerCreateInfo = VkSamplerCreateInfo.callocStack(stack);
            samplerCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
            samplerCreateInfo.magFilter(VK10.VK_FILTER_LINEAR);
            samplerCreateInfo.minFilter(VK10.VK_FILTER_LINEAR);
            samplerCreateInfo.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerCreateInfo.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerCreateInfo.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerCreateInfo.anisotropyEnable(true);
            samplerCreateInfo.maxAnisotropy(1.0f);
            samplerCreateInfo.borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK);
            samplerCreateInfo.unnormalizedCoordinates(false);
            samplerCreateInfo.compareEnable(false);
            samplerCreateInfo.compareOp(VK10.VK_COMPARE_OP_ALWAYS);
            samplerCreateInfo.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR);
            samplerCreateInfo.minLod(0); //Optional
            samplerCreateInfo.maxLod((float) mipLevels);
            samplerCreateInfo.mipLodBias(0); //Optional

            LongBuffer pTextureSampler = stack.mallocLong(1);

            if (VK10.vkCreateSampler(vkDevice, samplerCreateInfo, null, pTextureSampler) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture sampler");
            }

            return pTextureSampler.get(0);
        }
    }

    private static long createSurface(VkInstance vkInstance, long window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.longs(VK10.VK_NULL_HANDLE);

            if (GLFWVulkan.glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface");
            }

            return pSurface.get(0);
        }
    }

    private static VkPhysicalDevice pickPhysicalDevice(VkInstance vkInstance, long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer deviceCount = stack.ints(0);
            VK10.vkEnumeratePhysicalDevices(vkInstance, deviceCount, null);

            if (deviceCount.get(0) == 0) {
                throw new RuntimeException("Failed to find GPUs with Vulkan support");
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

            VK10.vkEnumeratePhysicalDevices(vkInstance, deviceCount, ppPhysicalDevices);

            VkPhysicalDevice device = null;

            for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
                device = new VkPhysicalDevice(ppPhysicalDevices.get(i), vkInstance);

                if (VulkanUtils.isDeviceSuitable(device, surface, REQUIRED_DEVICE_EXTENSIONS)) {
                    break;
                }
            }

            if (device == null) {
                throw new RuntimeException("Failed to find a suitable GPU");
            }

            return device;
        }
    }

    private static VkInstance createInstance(String appName, int appVersion, String engineName, int engineVersion, int vkApiVersion) {
        if (ValidationLayers.ENABLE_VALIDATION_LAYERS && !ValidationLayers.checkValidationLayerSupport()) {
            throw new RuntimeException("Validation requested but not supported");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);

            appInfo.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8Safe(appName));
            appInfo.applicationVersion(appVersion);
            appInfo.pEngineName(stack.UTF8Safe(engineName));
            appInfo.engineVersion(engineVersion);
            appInfo.apiVersion(vkApiVersion);

            IntBuffer extensionCount = stack.mallocInt(1);
            VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCount, null);
            VkExtensionProperties.Buffer buffer = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);
            VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCount, buffer);

            buffer.forEach(extensionProperty -> System.out.println(extensionProperty.extensionNameString()));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);

            createInfo.ppEnabledExtensionNames(VulkanUtils.fetchMandatoryExtensions());

            if (ValidationLayers.ENABLE_VALIDATION_LAYERS) {
                createInfo.ppEnabledLayerNames(LwjglAdapter.asPointBuffer(ValidationLayers.VALIDATION_LAYERS));
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = ValidationLayers.createVkDebugUtilsMessengerCreateInfoExt(stack);
                createInfo.pNext(debugCreateInfo.address());
            }

            PointerBuffer instancePtr = stack.mallocPointer(1);

            if (VK10.vkCreateInstance(createInfo, null, instancePtr) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create instance");
            }


            return new VkInstance(instancePtr.get(0), createInfo);
        }

    }

    private static VkDeviceQueueCreateInfo.Buffer createQueueCreateInfos(MemoryStack stack, int[] uniqueQueueFamilies) {
        VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueQueueFamilies.length, stack);

        for (int i = 0; i < uniqueQueueFamilies.length; i++) {
            VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i);
            queueCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
            queueCreateInfo.pQueuePriorities(stack.floats(1.0f));
        }
        return queueCreateInfos;
    }

    private static void transitionImageLayout(VkDevice vkDevice, long commandPool, VkQueue vkGraphicsQueue, long image, int format, int oldLayout, int newLayout, int mipMapLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
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

            if (newLayout == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT);

                if (VulkanUtils.hasStencilComponent(format)) {
                    barrier.subresourceRange().aspectMask(
                            barrier.subresourceRange().aspectMask() | VK10.VK_IMAGE_ASPECT_STENCIL_BIT);
                }
            } else {
                barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            int sourceStage;
            int destinationStage;

            if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            } else {
                throw new IllegalArgumentException("Unsupported layout transition");
            }

            VkCommandBuffer commandBuffer = beginSingleTimeCommands(vkDevice, commandPool);

            VK10.vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage,
                    0,
                    null,
                    null,
                    barrier);

            endSingleTimeCommands(commandBuffer, vkDevice, commandPool, vkGraphicsQueue);
        }
    }

    private static void createImage(VkDevice vkDevice, VkPhysicalDevice vkPhysicalDevice, int width, int height, int format, int tiling, int usage, int memProperties, LongBuffer pTextureImage, LongBuffer pTextureImageMemory, int mipMapLevels, int msaaSamplesNum) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
            imageInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D);
            imageInfo.extent().width(width);
            imageInfo.extent().height(height);
            imageInfo.extent().depth(1);
            imageInfo.mipLevels(mipMapLevels);
            imageInfo.arrayLayers(1);
            imageInfo.format(format);
            imageInfo.tiling(tiling);
            imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.usage(usage);
            imageInfo.samples(msaaSamplesNum);
            imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            if (VK10.vkCreateImage(vkDevice, imageInfo, null, pTextureImage) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create image");
            }

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.mallocStack(stack);
            VK10.vkGetImageMemoryRequirements(vkDevice, pTextureImage.get(0), memoryRequirements);

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.callocStack(stack);
            allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocateInfo.allocationSize(memoryRequirements.size());
            allocateInfo.memoryTypeIndex(VulkanUtils.extractTheCorrectMemoryTypeFromPhysicalDevice(memoryRequirements.memoryTypeBits(), memProperties, vkPhysicalDevice));

            if (VK10.vkAllocateMemory(vkDevice, allocateInfo, null, pTextureImageMemory) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate image memory");
            }

            VK10.vkBindImageMemory(vkDevice, pTextureImage.get(0), pTextureImageMemory.get(0), 0);
        }
    }

    private static VkCommandBuffer beginSingleTimeCommands(VkDevice vkDevice, long commandPool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocateInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocateInfo.commandPool(commandPool);
            allocateInfo.commandBufferCount(1);

            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            VK10.vkAllocateCommandBuffers(vkDevice, allocateInfo, pCommandBuffer);

            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), vkDevice);
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            VK10.vkBeginCommandBuffer(commandBuffer, beginInfo);

            return commandBuffer;
        }
    }

    private static void endSingleTimeCommands(VkCommandBuffer commandBuffer, VkDevice vkDevice, long commandPool, VkQueue vkGraphicsQueue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkEndCommandBuffer(commandBuffer);

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack);
            submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));

            VK10.vkQueueSubmit(vkGraphicsQueue, submitInfo, VK10.VK_NULL_HANDLE);
            VK10.vkQueueWaitIdle(vkGraphicsQueue);
            VK10.vkFreeCommandBuffers(vkDevice, commandPool, commandBuffer);
        }
    }

    private static long createCommandPool(VkDevice vkDevice, QueueFamilyIndices queueFamilyIndices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
            poolInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(queueFamilyIndices.getGraphicsFamily());

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (VK10.vkCreateCommandPool(vkDevice, poolInfo, null, pCommandPool) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            return pCommandPool.get(0);
        }
    }

    private static VkDevice createLogicalDevice(VkPhysicalDevice vkPhysicalDevice, QueueFamilyIndices queueFamilyIndices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] uniqueQueueFamilies = queueFamilyIndices.unique();

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = createQueueCreateInfos(stack, uniqueQueueFamilies);

            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);
            deviceFeatures.samplerAnisotropy(true);
            deviceFeatures.sampleRateShading(true); // Enable sample shading feature for the device

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.callocStack(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);

            createInfo.pEnabledFeatures(deviceFeatures);

            createInfo.ppEnabledExtensionNames(LwjglAdapter.asPointBuffer(REQUIRED_DEVICE_EXTENSIONS));

            if (ValidationLayers.ENABLE_VALIDATION_LAYERS) {
                createInfo.ppEnabledLayerNames(LwjglAdapter.asPointBuffer(ValidationLayers.VALIDATION_LAYERS));
            }

            PointerBuffer pDevice = stack.pointers(VK10.VK_NULL_HANDLE);

            if (VK10.vkCreateDevice(vkPhysicalDevice, createInfo, null, pDevice) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device");
            }

            return new VkDevice(pDevice.get(0), vkPhysicalDevice, createInfo);

        }
    }

    private static VkQueue createPresentationQueue(VkDevice vkDevice, QueueFamilyIndices queueFamilyIndices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueuePresentation = stack.pointers(VK10.VK_NULL_HANDLE);
            VK10.vkGetDeviceQueue(vkDevice, queueFamilyIndices.getPresentationFamily(), 0, pQueuePresentation);
            return new VkQueue(pQueuePresentation.get(0), vkDevice);
        }
    }

    private static VkQueue createGraphicsQueue(VkDevice vkDevice, QueueFamilyIndices queueFamilyIndices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueueGraphics = stack.pointers(VK10.VK_NULL_HANDLE);
            VK10.vkGetDeviceQueue(vkDevice, queueFamilyIndices.getGraphicsFamily(), 0, pQueueGraphics);
            return new VkQueue(pQueueGraphics.get(0), vkDevice);
        }
    }

    private static void createAllocateBindBuffer(long size, int usage, int properties, LongBuffer pBuffer, LongBuffer pBufferMemory, VkDevice vkDevice, VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.callocStack(stack);
            bufferCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferCreateInfo.size(size);
            bufferCreateInfo.usage(usage);
            bufferCreateInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            if (VK10.vkCreateBuffer(vkDevice, bufferCreateInfo, null, pBuffer) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer");
            }

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.mallocStack(stack);
            VK10.vkGetBufferMemoryRequirements(vkDevice, pBuffer.get(0), memoryRequirements);

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.callocStack(stack);
            allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocateInfo.allocationSize(memoryRequirements.size());
            allocateInfo.memoryTypeIndex(VulkanUtils.extractTheCorrectMemoryTypeFromPhysicalDevice(memoryRequirements.memoryTypeBits(), properties, vkPhysicalDevice));

            if (VK10.vkAllocateMemory(vkDevice, allocateInfo, null, pBufferMemory) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate vertex buffer memory");
            }

            VK10.vkBindBufferMemory(vkDevice, pBuffer.get(0), pBufferMemory.get(0), 0);
        }
    }

    private static long createRenderPass(int swapChainImageFormat, int msaaSamples, VkDevice vkDevice, VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(3, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.callocStack(3, stack);

            //Color attachments start

            //MSAA Image
            VkAttachmentDescription colorAttachment = attachments.get(0);
            colorAttachment.format(swapChainImageFormat);
            colorAttachment.samples(msaaSamples);
            colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference colorAttachmentRef = attachmentRefs.get(0);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            //Present Image
            VkAttachmentDescription colorAttachmentResolve = attachments.get(2);
            colorAttachmentResolve.format(swapChainImageFormat);
            colorAttachmentResolve.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
            colorAttachmentResolve.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachmentResolve.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachmentResolve.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachmentResolve.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachmentResolve.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachmentResolve.finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference colorAttachmentResolveRef = attachmentRefs.get(2);
            colorAttachmentResolveRef.attachment(2);
            colorAttachmentResolveRef.layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL); //TODO check if this is the right value.

            //Depth-Stencil attachments

            VkAttachmentDescription depthAttachment = attachments.get(1);
            depthAttachment.format(VulkanUtils.findSupportedDepthFormat(vkPhysicalDevice));
            depthAttachment.samples(msaaSamples);
            depthAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
            depthAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
            depthAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            depthAttachment.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
            depthAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            depthAttachment.finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthAttachmentRef = attachmentRefs.get(1);
            depthAttachmentRef.attachment(1);
            depthAttachmentRef.layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            //Attachments end

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
            subpass.pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(VkAttachmentReference.callocStack(1, stack).put(0, colorAttachmentRef));
            subpass.pDepthStencilAttachment(depthAttachmentRef);
            subpass.pResolveAttachments(VkAttachmentReference.callocStack(1, stack).put(0, colorAttachmentResolveRef));

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.callocStack(1, stack);
            dependency.srcSubpass(VK10.VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack);
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(attachments);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);

            if (VK10.vkCreateRenderPass(vkDevice, renderPassInfo, null, pRenderPass) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create redner pass");
            }

            return pRenderPass.get(0);
        }
    }

    private static VkExtent2D createSwapExtent(VkSurfaceCapabilitiesKHR capabilities, long windowHandle) {
        if (capabilities.currentExtent().width() != UINT32_MAX) {
            return capabilities.currentExtent();
        }

        IntBuffer width = MemoryStack.stackGet().ints(0);
        IntBuffer height = MemoryStack.stackGet().ints(0);

        GLFW.glfwGetFramebufferSize(windowHandle, width, height);

        VkExtent2D actualExtent = VkExtent2D.mallocStack().set(width.get(0), height.get(0));

        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();

        actualExtent.width(VulkanUtils.clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
        actualExtent.height(VulkanUtils.clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));

        return actualExtent;
    }

    public void run() {
        this.window = new Window(800, 600);
        this.window.initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }

    private void mainLoop() {
        int fps = 0;
        double lastTime = System.currentTimeMillis();
        while (!GLFW.glfwWindowShouldClose(this.window.getWindowHandle())) {
            if (System.currentTimeMillis() - lastTime >= 1000) {
                GLFW.glfwSetWindowTitle(this.window.getWindowHandle(), String.valueOf(fps));
                lastTime = System.currentTimeMillis();
                fps = 0;
            }
            GLFW.glfwPollEvents();
            drawFrame();
            fps++;
        }

        VK10.vkDeviceWaitIdle(this.vkDevice);
    }

    private void initVulkan() {
        this.vkInstance = createInstance("Hello Triangle",
                VK10.VK_MAKE_VERSION(1, 0, 0),
                "No Engine",
                VK10.VK_MAKE_VERSION(1, 0, 0),
                VK11.VK_API_VERSION_1_1);


        this.validationLayers = new ValidationLayers();
        this.validationLayers.setupDebugMessenger(this.vkInstance);

        this.surface = createSurface(this.vkInstance, this.window.getWindowHandle());
        this.vkPhysicalDevice = pickPhysicalDevice(this.vkInstance, this.surface);

        //TODO :: test how multisampling is efecting the fps :: ibikov
        this.msaaSamples = VulkanUtils.findMaxUsableSampleCount(this.vkPhysicalDevice);

        QueueFamilyIndices queueFamilyIndices = VulkanUtils.findQueueFamiliesFromPhysicalDevice(this.vkPhysicalDevice, this.surface);

        this.vkDevice = createLogicalDevice(this.vkPhysicalDevice, queueFamilyIndices);
        this.vkGraphicsQueue = createGraphicsQueue(this.vkDevice, queueFamilyIndices);
        this.vkPresentQueue = createPresentationQueue(this.vkDevice, queueFamilyIndices);
        this.commandPool = createCommandPool(this.vkDevice, queueFamilyIndices);

        this.gameObject = GameObjectLoader.loadModel("models/chalet.obj");

        createTextureImage();
        createTextureImageView();

        this.textureSampler = createTextureSampler(this.vkDevice, this.mipLevels);

        createVertexBuffer();
        createIndexBuffer();
        createDescriptorSetLayout();
        createSwapChainObjects();
        createSyncObjects();
        initRayTracing();
        sceneObjectToBlas();
    }

    private void initRayTracing() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceRayTracingPropertiesNV physicalDeviceRayTracingPropertiesNV = VkPhysicalDeviceRayTracingPropertiesNV.callocStack(stack);
            physicalDeviceRayTracingPropertiesNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PROPERTIES_NV);
            VkPhysicalDeviceProperties2 props2 = VkPhysicalDeviceProperties2.mallocStack(stack);
            props2.sType(VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2);
            props2.pNext(physicalDeviceRayTracingPropertiesNV.address());

            VK11.vkGetPhysicalDeviceProperties2(this.vkPhysicalDevice, props2);

            int maxRecursionDepth = physicalDeviceRayTracingPropertiesNV.maxRecursionDepth();
            int shaderGroupHandleSize = physicalDeviceRayTracingPropertiesNV.shaderGroupHandleSize();

            System.out.println("maxPushConstantSize: " + props2.properties().limits().maxPushConstantsSize());
            System.out.println("maxRecursion: " + maxRecursionDepth);
            System.out.println("shaderGroupHandleSize: " + shaderGroupHandleSize);

        }
    }

    public void sceneObjectToBlas() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            //either AABBs or Traingles we will try triangles first
//            VkGeometryAABBNV vkGeometryAABBNV = VkGeometryAABBNV.callocStack(stack);

            //i think actual data
            VkGeometryTrianglesNV vkGeometryTrianglesNV = VkGeometryTrianglesNV.callocStack(stack);
            vkGeometryTrianglesNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_GEOMETRY_TRIANGLES_NV);
//            createVertexBuffer(); //hopefully this creates the buffer as needed
            vkGeometryTrianglesNV.vertexData(this.vertexBuffer);
            vkGeometryTrianglesNV.vertexOffset(0); //Vertex.SIZEOF * this.gameObject.getVertices().length);
            vkGeometryTrianglesNV.vertexCount(this.gameObject.getVertices().length);
            vkGeometryTrianglesNV.vertexFormat(VK10.VK_FORMAT_R32G32B32_SFLOAT); //TODO idk
//            createIndexBuffer(); //hopefully this creates the buffer as needed
            vkGeometryTrianglesNV.indexData(this.indexBuffer);
            vkGeometryTrianglesNV.indexType(VK10.VK_INDEX_TYPE_UINT32);
            vkGeometryTrianglesNV.indexOffset(0); //Integer.BYTES * this.gameObject.getIndices().length);
            vkGeometryTrianglesNV.indexCount(this.gameObject.getIndices().length);

            vkGeometryTrianglesNV.transformData(VK10.VK_NULL_HANDLE);//Model transformation matrix data
            vkGeometryTrianglesNV.transformOffset(0);

            VkGeometryAABBNV aabbnv = VkGeometryAABBNV.callocStack(stack);
            aabbnv.sType(NVRayTracing.VK_STRUCTURE_TYPE_GEOMETRY_AABB_NV);
            aabbnv.aabbData(VK10.VK_NULL_HANDLE);

            VkGeometryDataNV vkGeometryDataNV = VkGeometryDataNV.callocStack(stack);
            vkGeometryDataNV.triangles(vkGeometryTrianglesNV);
            vkGeometryDataNV.aabbs(aabbnv);

            VkGeometryNV.Buffer vkGeometryNV = VkGeometryNV.callocStack(1, stack);
            vkGeometryNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_GEOMETRY_NV);
            vkGeometryNV.geometry(vkGeometryDataNV);
            vkGeometryNV.geometryType(NVRayTracing.VK_GEOMETRY_TYPE_TRIANGLES_NV);
            vkGeometryNV.flags(NVRayTracing.VK_GEOMETRY_OPAQUE_BIT_NV);

            VkAccelerationStructureInfoNV accelerationStructureInfoNV = VkAccelerationStructureInfoNV.callocStack(stack);
            accelerationStructureInfoNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_INFO_NV);
            accelerationStructureInfoNV.flags(NVRayTracing.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_NV);
            accelerationStructureInfoNV.pGeometries(vkGeometryNV);
            accelerationStructureInfoNV.type(NVRayTracing.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_NV);


            VkAccelerationStructureCreateInfoNV accelerationStructureCreateInfoNV = VkAccelerationStructureCreateInfoNV.callocStack(stack);
            accelerationStructureCreateInfoNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_NV);
            accelerationStructureCreateInfoNV.info(accelerationStructureInfoNV);

            LongBuffer pDescriptorPool = stack.mallocLong(1);

            NVRayTracing.vkCreateAccelerationStructureNV(this.vkDevice, accelerationStructureCreateInfoNV, null, pDescriptorPool);

            this.blas = pDescriptorPool.get(0);

            VkAccelerationStructureMemoryRequirementsInfoNV memoryRequirementsInfoNV = VkAccelerationStructureMemoryRequirementsInfoNV.callocStack(stack);
            memoryRequirementsInfoNV.sType(NVRayTracing.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_INFO_NV);
            memoryRequirementsInfoNV.type(NVRayTracing.VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_OBJECT_NV);
            memoryRequirementsInfoNV.accelerationStructure(this.blas);

            VkMemoryRequirements2KHR memoryRequirements = VkMemoryRequirements2KHR.mallocStack(stack);

            LongBuffer pBlasMemory = stack.mallocLong(1);

            NVRayTracing.vkGetAccelerationStructureMemoryRequirementsNV(this.vkDevice, memoryRequirementsInfoNV, memoryRequirements);

            VkMemoryAllocateInfo memoryAllocateInfo = VkMemoryAllocateInfo.callocStack(stack);
            memoryAllocateInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            memoryAllocateInfo.allocationSize(memoryRequirements.memoryRequirements().size());
            memoryAllocateInfo.memoryTypeIndex(VulkanUtils.extractTheCorrectMemoryTypeFromPhysicalDevice(memoryRequirements.memoryRequirements().memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, this.vkPhysicalDevice));

            if (VK10.vkAllocateMemory(this.vkDevice, memoryAllocateInfo, null, pBlasMemory) != VK10.VK_SUCCESS) {
                System.out.println("Could not allocate memory for acceleration structure");
            }

            this.blasMemory = pBlasMemory.get(0);

            VkBindAccelerationStructureMemoryInfoNV.Buffer bind = VkBindAccelerationStructureMemoryInfoNV.callocStack(1, stack);
            bind.sType(NVRayTracing.VK_STRUCTURE_TYPE_BIND_ACCELERATION_STRUCTURE_MEMORY_INFO_NV);
            bind.accelerationStructure(this.blas);
            bind.memory(this.blasMemory);

            NVRayTracing.vkBindAccelerationStructureMemoryNV(this.vkDevice, bind);

            //Estimate scratch memory
            VkAccelerationStructureMemoryRequirementsInfoNV scratchMemoReq = VkAccelerationStructureMemoryRequirementsInfoNV.callocStack(stack);
            scratchMemoReq.sType(NVRayTracing.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_INFO_NV);
            scratchMemoReq.type(NVRayTracing.VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_BUILD_SCRATCH_NV);
            scratchMemoReq.accelerationStructure(this.blas);

            VkMemoryRequirements2KHR memoryRequirementsForScratch = VkMemoryRequirements2KHR.mallocStack(stack);

            NVRayTracing.vkGetAccelerationStructureMemoryRequirementsNV(this.vkDevice, scratchMemoReq, memoryRequirementsForScratch);

            long scratchMemory = memoryRequirementsForScratch.memoryRequirements().size();

            //These two LongBuffers represent the nvvk::Buffer from the tutorial
            LongBuffer pStagingBuffer = stack.mallocLong(1);
            LongBuffer pStagingBufferMemory = stack.mallocLong(1);

            createAllocateBindBuffer(scratchMemory, NVRayTracing.VK_BUFFER_USAGE_RAY_TRACING_BIT_NV, VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, pStagingBuffer, pStagingBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            VkQueryPoolCreateInfo queryPoolCreateInfo = VkQueryPoolCreateInfo.callocStack(stack);
            queryPoolCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO);
            queryPoolCreateInfo.queryCount(1); //if loop then change size here
            queryPoolCreateInfo.queryType(NVRayTracing.VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_NV);

            LongBuffer pQueryPool = stack.mallocLong(1);

            VK10.vkCreateQueryPool(this.vkDevice, queryPoolCreateInfo, null, pQueryPool);

            VK10.vkDestroyBuffer(this.vkDevice, pStagingBuffer.get(0), null);
            VK10.vkFreeMemory(this.vkDevice, pStagingBufferMemory.get(0), null);
            VK10.vkDestroyQueryPool(this.vkDevice, pQueryPool.get(0), null);
        }
    }

    private void drawFrame() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Frame thisFrame = this.inFlightFrames.get(this.currentFrame);

            VK10.vkWaitForFences(this.vkDevice, thisFrame.pFence(), true, NO_TIMEOUT);

            IntBuffer pImageIndex = stack.mallocInt(1);

            int vkResult = KHRSwapchain.vkAcquireNextImageKHR(this.vkDevice, this.swapChain, NO_TIMEOUT, thisFrame.getImageAvailableSemaphore(), VK10.VK_NULL_HANDLE, pImageIndex);

            if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapChain();
                return;
            } else if (vkResult != VK10.VK_SUCCESS) {
                throw new RuntimeException("Cannot get image");
            }

            final int imageIndex = pImageIndex.get(0);

            updateUniformBuffer(imageIndex);

            if (this.imagesInFlight.containsKey(imageIndex)) {
                VK10.vkWaitForFences(this.vkDevice, this.imagesInFlight.get(imageIndex).getFence(), true, NO_TIMEOUT);
            }

            this.imagesInFlight.put(imageIndex, thisFrame);

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.waitSemaphoreCount(1);
            submitInfo.pWaitSemaphores(thisFrame.pImageAvailableSemaphore());
            submitInfo.pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            submitInfo.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore());
            submitInfo.pCommandBuffers(stack.pointers(this.commandBuffers.get(imageIndex)));

            VK10.vkResetFences(this.vkDevice, thisFrame.pFence());

            if (VK10.vkQueueSubmit(this.vkGraphicsQueue, submitInfo, thisFrame.getFence()) != VK10.VK_SUCCESS) {
                VK10.vkResetFences(this.vkDevice, thisFrame.pFence());
                throw new RuntimeException("Failed to submit draw command buffer");
            }

            VkPresentInfoKHR presentInfoKHR = VkPresentInfoKHR.callocStack(stack);
            presentInfoKHR.sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            presentInfoKHR.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore());
            presentInfoKHR.swapchainCount(1);
            presentInfoKHR.pSwapchains(stack.longs(this.swapChain));
            presentInfoKHR.pImageIndices(pImageIndex);

            vkResult = KHRSwapchain.vkQueuePresentKHR(this.vkPresentQueue, presentInfoKHR);

            if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || vkResult == KHRSwapchain.VK_SUBOPTIMAL_KHR || this.window.isWindowResized()) {
                this.window.setWindowResized(false);
                recreateSwapChain();
            } else if (vkResult != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to present swap chain image");
            }

            this.currentFrame = (this.currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    private void createTextureImageView() {
        this.textureImageView = createImageView(this.textureImage, VK10.VK_FORMAT_R8G8B8A8_SRGB, VK10.VK_IMAGE_ASPECT_COLOR_BIT, this.mipLevels);
    }

    private void createTextureImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String filename = ClassLoader.getSystemClassLoader().getResource("textures/chalet.jpg").getPath();

            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            IntBuffer pChannels = stack.mallocInt(1);

            ByteBuffer pixels = STBImage.stbi_load(filename, pWidth, pHeight, pChannels, STBImage.STBI_rgb_alpha);

            long imageSize = pWidth.get(0) * pHeight.get(0) * 4;//pChannels.get(0); //always 4 due to STBI_rgb_alpha

            this.mipLevels = (int) Math.floor(VulkanUtils.log2(Math.max(pWidth.get(0), pHeight.get(0)))) + 1;

            if (pixels == null) {
                throw new RuntimeException("Failed to load texture image " + filename);
            }

            LongBuffer pStagingBuffer = stack.mallocLong(1);
            LongBuffer pStagingBufferMemory = stack.mallocLong(1);
            createAllocateBindBuffer(imageSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pStagingBuffer,
                    pStagingBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            PointerBuffer data = stack.mallocPointer(1);
            VK10.vkMapMemory(this.vkDevice, pStagingBufferMemory.get(0), 0, imageSize, 0, data);
            {
                ByteBufferUtils.copyIntoBuffer(data.getByteBuffer(0, (int) imageSize), pixels, imageSize);
            }
            VK10.vkUnmapMemory(this.vkDevice, pStagingBufferMemory.get(0));

            STBImage.stbi_image_free(pixels);

            LongBuffer pTextureImage = stack.mallocLong(1);
            LongBuffer pTextureImageMemory = stack.mallocLong(1);

            createImage(this.vkDevice, this.vkPhysicalDevice, pWidth.get(0),
                    pHeight.get(0),
                    VK10.VK_FORMAT_R8G8B8A8_SRGB, VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pTextureImage,
                    pTextureImageMemory,
                    this.mipLevels, VK10.VK_SAMPLE_COUNT_1_BIT);

            this.textureImage = pTextureImage.get(0);
            this.textureImageMemory = pTextureImageMemory.get(0);

            transitionImageLayout(this.vkDevice, this.commandPool, this.vkGraphicsQueue, this.textureImage, VK10.VK_FORMAT_R8G8B8A8_SRGB,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    this.mipLevels);

            copyBufferToImage(pStagingBuffer.get(0), this.textureImage, pWidth.get(0), pHeight.get(0));

            //Transitioned to VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL while generating mipmaps
            generateMipmaps(this.textureImage, VK10.VK_FORMAT_R8G8B8A8_SRGB, pWidth.get(0), pHeight.get(0), this.mipLevels);

            VK10.vkDestroyBuffer(this.vkDevice, pStagingBuffer.get(0), null);
            VK10.vkFreeMemory(this.vkDevice, pStagingBufferMemory.get(0), null);
        }
    }

    private void generateMipmaps(long image, int imageFormat, int width, int height, int mipMapLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Check if image format supports linear blitting
            VkFormatProperties formatProperties = VkFormatProperties.mallocStack(stack);
            VK10.vkGetPhysicalDeviceFormatProperties(this.vkPhysicalDevice, imageFormat, formatProperties);

            if ((formatProperties.optimalTilingFeatures() & VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0) {
                throw new RuntimeException("Texture image format does not support linear blitting");
            }

            VkCommandBuffer commandBuffer = beginSingleTimeCommands(this.vkDevice, this.commandPool);

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.callocStack(1, stack);
            barrier.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
            barrier.image(image);
            barrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier.dstAccessMask(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            barrier.subresourceRange().baseArrayLayer(0);
            barrier.subresourceRange().layerCount(1);
            barrier.subresourceRange().levelCount(1);

            int mipWidth = width;
            int mipHeight = height;

            for (int i = 1; i < mipMapLevels; i++) {
                barrier.subresourceRange().baseMipLevel(i - 1);
                barrier.oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                barrier.newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT);

                VK10.vkCmdPipelineBarrier(commandBuffer,
                        VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        null,
                        barrier);

                VkImageBlit.Buffer blit = VkImageBlit.callocStack(1, stack);
                blit.srcOffsets(0).set(0, 0, 0);
                blit.srcOffsets(1).set(mipWidth, mipHeight, 1);
                blit.srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                blit.srcSubresource().mipLevel(i - 1);
                blit.srcSubresource().baseArrayLayer(0);
                blit.srcSubresource().layerCount(1);
                blit.dstOffsets(0).set(0, 0, 0);
                blit.dstOffsets(1).set(mipWidth > 1 ? mipWidth / 2 : 1, mipHeight > 1 ? mipHeight / 2 : 1, 1);
                blit.dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                blit.dstSubresource().mipLevel(i);
                blit.dstSubresource().baseArrayLayer(0);
                blit.dstSubresource().layerCount(1);

                VK10.vkCmdBlitImage(commandBuffer,
                        image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        blit,
                        VK10.VK_FILTER_LINEAR);

                barrier.oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                barrier.newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT);
                barrier.dstAccessMask(VK10.VK_ACCESS_SHADER_WRITE_BIT);

                VK10.vkCmdPipelineBarrier(commandBuffer,
                        VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        barrier);

                if (mipWidth > 1) {
                    mipWidth /= 2;
                }

                if (mipHeight > 1) {
                    mipHeight /= 2;
                }
            }

            barrier.subresourceRange().baseMipLevel(mipMapLevels - 1);
            barrier.oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            barrier.newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            barrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

            VK10.vkCmdPipelineBarrier(commandBuffer,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    barrier);

            endSingleTimeCommands(commandBuffer, this.vkDevice, this.commandPool, this.vkGraphicsQueue);
        }
    }

    private void copyBufferToImage(long buffer, long image, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = beginSingleTimeCommands(this.vkDevice, this.commandPool);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.callocStack(1, stack);
            region.bufferOffset(0);
            region.bufferRowLength(0); //Tightly packed
            region.bufferImageHeight(0); //Tightly packed
            region.imageSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            region.imageSubresource().mipLevel(0);
            region.imageSubresource().baseArrayLayer(0);
            region.imageSubresource().layerCount(1);
            region.imageOffset().set(0, 0, 0);
            region.imageExtent(VkExtent3D.callocStack(stack).set(width, height, 1));

            VK10.vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            endSingleTimeCommands(commandBuffer, this.vkDevice, this.commandPool, this.vkGraphicsQueue);
        }
    }

    private void createUniformBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.uniformBuffers = new ArrayList<>(this.swapChainImages.size());
            this.uniformBuffersMemory = new ArrayList<>(this.swapChainImages.size());

            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);

            for (int i = 0; i < this.swapChainImages.size(); i++) {
                createAllocateBindBuffer(UniformBufferObject.SIZEOF,
                        VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer,
                        pBufferMemory, this.vkDevice, this.vkPhysicalDevice);
                this.uniformBuffers.add(pBuffer.get(0));
                this.uniformBuffersMemory.add(pBufferMemory.get(0));
            }
        }
    }

    private void createIndexBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bufferSize = Integer.BYTES * this.gameObject.getIndices().length;

            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);

            createAllocateBindBuffer(bufferSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            long stagingBuffer = pBuffer.get(0);
            long stagingBufferMemory = pBufferMemory.get(0);

            PointerBuffer data = stack.mallocPointer(1);

            VK10.vkMapMemory(this.vkDevice, stagingBufferMemory, 0, bufferSize, 0, data);
            {
                ByteBufferUtils.copyIntoBuffer(data.getByteBuffer(0, (int) bufferSize), this.gameObject.getIndices());
            }
            VK10.vkUnmapMemory(this.vkDevice, stagingBufferMemory);

            createAllocateBindBuffer(bufferSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                    pBuffer,
                    pBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            this.indexBuffer = pBuffer.get(0);
            this.indexBufferMemory = pBufferMemory.get(0);

            copyBuffer(stagingBuffer, this.indexBuffer, bufferSize);

            VK10.vkDestroyBuffer(this.vkDevice, stagingBuffer, null);
            VK10.vkFreeMemory(this.vkDevice, stagingBufferMemory, null);
        }
    }

    private void createVertexBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bufferSize = Vertex.SIZEOF * this.gameObject.getVertices().length;

            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);
            createAllocateBindBuffer(bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            long stagingBuffer = pBuffer.get(0);
            long stagingBufferMemory = pBufferMemory.get(0);

            PointerBuffer data = stack.mallocPointer(1);

            VK10.vkMapMemory(this.vkDevice, stagingBufferMemory, 0, bufferSize, 0, data);
            {
                ByteBufferUtils.copyIntoBuffer(data.getByteBuffer(0, (int) bufferSize), this.gameObject.getVertices());
            }
            VK10.vkUnmapMemory(this.vkDevice, stagingBufferMemory);

            createAllocateBindBuffer(bufferSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                    pBuffer,
                    pBufferMemory, this.vkDevice, this.vkPhysicalDevice);

            this.vertexBuffer = pBuffer.get(0);
            this.vertexBufferMemory = pBufferMemory.get(0);

            copyBuffer(stagingBuffer, this.vertexBuffer, bufferSize);

            VK10.vkDestroyBuffer(this.vkDevice, stagingBuffer, null);
            VK10.vkFreeMemory(this.vkDevice, stagingBufferMemory, null);
        }
    }

    private void copyBuffer(long srcBuffer, long dstBuffer, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = beginSingleTimeCommands(this.vkDevice, this.commandPool);

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
            copyRegion.size(size);

            VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);

            endSingleTimeCommands(commandBuffer, this.vkDevice, this.commandPool, this.vkGraphicsQueue);
        }
    }

    private void createSwapChainObjects() {
        createSwapChain();
        createImageViews();
        this.renderPass = createRenderPass(this.swapChainImageFormat, this.msaaSamples, this.vkDevice, this.vkPhysicalDevice);
        createGraphicsPipeline();
        createColorResources();
        createDepthResources();
        createFrameBuffers();
        createUniformBuffers();
        createDescriptorPool();
        createDescriptorSets();
        createCommandBuffers();
    }

    private void createColorResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pColorImage = stack.mallocLong(1);
            LongBuffer pColorImageMemory = stack.mallocLong(1);

            createImage(this.vkDevice, this.vkPhysicalDevice, this.swapChainExtent.width(),
                    this.swapChainExtent.height(),
                    this.swapChainImageFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pColorImage,
                    pColorImageMemory,
                    1,
                    this.msaaSamples);

            this.colorImage = pColorImage.get(0);
            this.colorImageMemory = pColorImageMemory.get(0);

            this.colorImageView = createImageView(this.colorImage, this.swapChainImageFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1);

            transitionImageLayout(this.vkDevice, this.commandPool, this.vkGraphicsQueue, this.colorImage, this.swapChainImageFormat, VK10.VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 1);
        }
    }

    private void createDepthResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int depthFormat = VulkanUtils.findSupportedDepthFormat(this.vkPhysicalDevice);

            LongBuffer pDepthImage = stack.mallocLong(1);
            LongBuffer pDepthImageMemory = stack.mallocLong(1);

            createImage(this.vkDevice, this.vkPhysicalDevice, this.swapChainExtent.width(),
                    this.swapChainExtent.height(),
                    depthFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pDepthImage,
                    pDepthImageMemory,
                    1, this.msaaSamples);

            this.depthImage = pDepthImage.get(0);
            this.depthImageMemory = pDepthImageMemory.get(0);

            this.depthImageView = createImageView(this.depthImage, depthFormat, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 1);

            //Explicitly transitioning the depth image
            transitionImageLayout(this.vkDevice, this.commandPool, this.vkGraphicsQueue, this.depthImage,
                    depthFormat,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    1);
        }
    }

    private void createDescriptorSets() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer layouts = stack.mallocLong(this.swapChainImages.size());
            for (int i = 0; i < layouts.capacity(); i++) {
                layouts.put(i, this.descriptorSetLayout);
            }

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.callocStack(stack);
            allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
            allocateInfo.descriptorPool(this.descriptorPool);
            allocateInfo.pSetLayouts(layouts);

            LongBuffer pDescriptorSets = stack.mallocLong(this.swapChainImages.size());

            if (VK10.vkAllocateDescriptorSets(this.vkDevice, allocateInfo, pDescriptorSets) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate descriptor sets");
            }

            this.descriptorSets = new ArrayList<>(pDescriptorSets.capacity());

            VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.callocStack(1, stack);
            bufferInfos.offset(0);
            bufferInfos.range(UniformBufferObject.SIZEOF);

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack);
            imageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            imageInfo.imageView(this.textureImageView);
            imageInfo.sampler(this.textureSampler);

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.callocStack(2, stack);

            VkWriteDescriptorSet uboDescriptorWrite = descriptorWrites.get(0);
            uboDescriptorWrite.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            uboDescriptorWrite.dstBinding(0);
            uboDescriptorWrite.dstArrayElement(0);
            uboDescriptorWrite.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uboDescriptorWrite.descriptorCount(1);
            uboDescriptorWrite.pBufferInfo(bufferInfos);

            VkWriteDescriptorSet samplerDescriptorWrite = descriptorWrites.get(1);
            samplerDescriptorWrite.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            samplerDescriptorWrite.dstBinding(1);
            samplerDescriptorWrite.dstArrayElement(0);
            samplerDescriptorWrite.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            samplerDescriptorWrite.descriptorCount(1);
            samplerDescriptorWrite.pImageInfo(imageInfo);

            for (int i = 0; i < pDescriptorSets.capacity(); i++) {
                long descriptorSet = pDescriptorSets.get(i);
                bufferInfos.buffer(this.uniformBuffers.get(i));
                uboDescriptorWrite.dstSet(descriptorSet);
                samplerDescriptorWrite.dstSet(descriptorSet);
                VK10.vkUpdateDescriptorSets(this.vkDevice, descriptorWrites, null);
                this.descriptorSets.add(descriptorSet);
            }
        }
    }

    private void createDescriptorPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.callocStack(2, stack);

            VkDescriptorPoolSize uniformBufferPoolSize = poolSizes.get(0);
            uniformBufferPoolSize.type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uniformBufferPoolSize.descriptorCount(this.swapChainImages.size());

            VkDescriptorPoolSize textureSamplerPoolSize = poolSizes.get(1);
            textureSamplerPoolSize.type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            textureSamplerPoolSize.descriptorCount(this.swapChainImages.size());

            VkDescriptorPoolCreateInfo poolCreateInfo = VkDescriptorPoolCreateInfo.callocStack(stack);
            poolCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
            poolCreateInfo.pPoolSizes(poolSizes);
            poolCreateInfo.maxSets(this.swapChainImages.size());

            LongBuffer pDescriptorPool = stack.mallocLong(1);

            if (VK10.vkCreateDescriptorPool(this.vkDevice, poolCreateInfo, null, pDescriptorPool) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool");
            }

            this.descriptorPool = pDescriptorPool.get(0);
        }
    }

    private void createDescriptorSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.callocStack(2, stack);

            VkDescriptorSetLayoutBinding uboLayoutBinding = bindings.get(0);
            uboLayoutBinding.binding(0);
            uboLayoutBinding.descriptorCount(1);
            uboLayoutBinding.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uboLayoutBinding.pImmutableSamplers(null);
            uboLayoutBinding.stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT);

            VkDescriptorSetLayoutBinding samplerLayoutBinding = bindings.get(1);
            samplerLayoutBinding.binding(1);
            samplerLayoutBinding.descriptorCount(1);
            samplerLayoutBinding.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            samplerLayoutBinding.pImmutableSamplers(null);
            samplerLayoutBinding.stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack);
            layoutInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            if (VK10.vkCreateDescriptorSetLayout(this.vkDevice, layoutInfo, null, pDescriptorSetLayout) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }

            this.descriptorSetLayout = pDescriptorSetLayout.get(0);
        }
    }

    private void createSyncObjects() {
        this.inFlightFrames = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
        this.imagesInFlight = new HashMap<>(this.swapChainImages.size());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.callocStack(stack);
            semaphoreCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.callocStack(stack);
            fenceCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceCreateInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
            LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (VK10.vkCreateSemaphore(this.vkDevice, semaphoreCreateInfo, null, pImageAvailableSemaphore) != VK10.VK_SUCCESS
                        || VK10.vkCreateSemaphore(this.vkDevice, semaphoreCreateInfo, null, pRenderFinishedSemaphore) != VK10.VK_SUCCESS
                        || VK10.vkCreateFence(this.vkDevice, fenceCreateInfo, null, pFence) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create synchronization objects for the frame" + i);
                }
                this.inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
            }
        }
    }

    private void createCommandBuffers() {
        final int commandBuffersCount = this.swapChainFrameBuffers.size();
        this.commandBuffers = new ArrayList<>(commandBuffersCount);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(this.commandPool);
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(commandBuffersCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

            if (VK10.vkAllocateCommandBuffers(this.vkDevice, allocInfo, pCommandBuffers) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for (int i = 0; i < commandBuffersCount; i++) {
                this.commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), this.vkDevice));
            }

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack);
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            renderPassInfo.renderPass(this.renderPass);

            VkRect2D renderArea = VkRect2D.callocStack(stack);
            renderArea.offset(VkOffset2D.callocStack(stack).set(0, 0));
            renderArea.extent(this.swapChainExtent);

            renderPassInfo.renderArea(renderArea);

            VkClearValue.Buffer clearValues = VkClearValue.callocStack(2, stack);
            clearValues.get(0).color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
            clearValues.get(1).depthStencil().set(1.0f, 0);

            renderPassInfo.pClearValues(clearValues);

            for (int i = 0; i < commandBuffersCount; i++) {
                VkCommandBuffer commandBuffer = this.commandBuffers.get(i);

                if (VK10.vkBeginCommandBuffer(commandBuffer, beginInfo) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to begin recording command buffer");
                }

                renderPassInfo.framebuffer(this.swapChainFrameBuffers.get(i));

                VK10.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);
                {
                    VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.graphicsPipeline);

                    LongBuffer vertexBuffers = stack.longs(this.vertexBuffer);
                    LongBuffer vertexOffsets = stack.longs(0);
                    VK10.vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
                    VK10.vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer, 0, VK10.VK_INDEX_TYPE_UINT32);

                    VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                            this.pipelineLayout, 0, stack.longs(this.descriptorSets.get(i)), null);

                    VK10.vkCmdDrawIndexed(commandBuffer, this.gameObject.getIndices().length, 1, 0, 0, 0);
                }
                VK10.vkCmdEndRenderPass(commandBuffer);

                if (VK10.vkEndCommandBuffer(commandBuffer) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to record command buffer");
                }
            }
        }
    }

    private void createFrameBuffers() {
        this.swapChainFrameBuffers = new ArrayList<>(this.swapChainImageViews.size());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.longs(this.colorImageView, this.depthImageView, VK10.VK_NULL_HANDLE);
            LongBuffer pFrameBuffer = stack.mallocLong(1);

            //Lets allocate the create info struct once and just update the pAttachments field each iteration
            VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.callocStack(stack);
            framebufferCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferCreateInfo.renderPass(this.renderPass);
            framebufferCreateInfo.width(this.swapChainExtent.width());
            framebufferCreateInfo.height(this.swapChainExtent.height());
            framebufferCreateInfo.layers(1);

            for (long imageView : this.swapChainImageViews) {
                attachments.put(2, imageView);

                framebufferCreateInfo.pAttachments(attachments);

                if (VK10.vkCreateFramebuffer(this.vkDevice, framebufferCreateInfo, null, pFrameBuffer) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }

                this.swapChainFrameBuffers.add(pFrameBuffer.get(0));
            }
        }
    }

    private void createGraphicsPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
            //Check ShaderSPIRVUtils class to see how it can be done
            SPIRV vertShaderSpirv = ShaderSPIRVUtils.compileShaderFile("shaders/shader.vert", ShaderKind.VERTEX_SHADER);
            SPIRV fragShaderSpirv = ShaderSPIRVUtils.compileShaderFile("shaders/shader.frag", ShaderKind.FRAGMENT_SHADER);

            long vertShaderModule = createShaderModule(vertShaderSpirv.byteCode());
            long fragShaderModule = createShaderModule(fragShaderSpirv.byteCode());

            ByteBuffer entryPoint = stack.UTF8("main");

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, stack);

            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);
            vertShaderStageInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            vertShaderStageInfo.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT);
            vertShaderStageInfo.module(vertShaderModule);
            vertShaderStageInfo.pName(entryPoint);

            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);
            fragShaderStageInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            fragShaderStageInfo.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            fragShaderStageInfo.module(fragShaderModule);
            fragShaderStageInfo.pName(entryPoint);

            // ===> VERTEX STAGE <===
            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.callocStack(stack);
            vertexInputInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            vertexInputInfo.pVertexBindingDescriptions(Vertex.getBindingDescription());
            vertexInputInfo.pVertexAttributeDescriptions(Vertex.getAttributeDescriptions());

            // ===> ASSEMBLY STAGE <===
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack);
            inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            inputAssembly.primitiveRestartEnable(false);

            // ===> VIEWPORT & SCISSOR
            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack);
            viewport.x(0.0f);
            viewport.y(0.0f);
            viewport.width(this.swapChainExtent.width());
            viewport.height(this.swapChainExtent.height());
            viewport.minDepth(0.0f);
            viewport.maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack);
            scissor.offset(VkOffset2D.callocStack(stack).set(0, 0));
            scissor.extent(this.swapChainExtent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.callocStack(stack);
            viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
            viewportState.pViewports(viewport);
            viewportState.pScissors(scissor);

            // ===> RASTERIZATION STAGE <===
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(stack);
            rasterizer.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
            rasterizer.depthClampEnable(false);
            rasterizer.rasterizerDiscardEnable(false);
            rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL);
            rasterizer.lineWidth(1.0f);
            rasterizer.cullMode(VK10.VK_CULL_MODE_BACK_BIT);
            rasterizer.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
            rasterizer.depthBiasEnable(false);

            // ===> MULTISAMPLING <===
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
            multisampling.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(true);
            multisampling.minSampleShading(0.2f); //Enable sample shading in the pipeline
            multisampling.rasterizationSamples(this.msaaSamples); //Min fraction for sample shading; closer to one is smoother

            // ===> DEPTH-STENCIL TESTING <===
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack);
            depthStencil.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
            depthStencil.depthTestEnable(true);
            depthStencil.depthWriteEnable(true);
            depthStencil.depthCompareOp(VK10.VK_COMPARE_OP_LESS);
            depthStencil.depthBoundsTestEnable(false);
            depthStencil.minDepthBounds(0.0f); //Optional
            depthStencil.maxDepthBounds(1.0f); //Optional
            depthStencil.stencilTestEnable(false);

            // ===> COLOR BLENDING <===
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachmentStates = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
            colorBlendAttachmentStates.colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);
            colorBlendAttachmentStates.blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.callocStack(stack);
            colorBlendStateCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            colorBlendStateCreateInfo.logicOpEnable(false);
            colorBlendStateCreateInfo.logicOp(VK10.VK_LOGIC_OP_COPY);
            colorBlendStateCreateInfo.pAttachments(colorBlendAttachmentStates);
            colorBlendStateCreateInfo.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            // ===> PIPELINE LAYOUT CREATION <===
            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
            pipelineLayoutCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            pipelineLayoutCreateInfo.pSetLayouts(stack.longs(this.descriptorSetLayout));

            LongBuffer pPipelineLayout = stack.longs(VK10.VK_NULL_HANDLE);

            if (VK10.vkCreatePipelineLayout(this.vkDevice, pipelineLayoutCreateInfo, null, pPipelineLayout) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }

            this.pipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfos = VkGraphicsPipelineCreateInfo.callocStack(1, stack);
            pipelineCreateInfos.sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pipelineCreateInfos.pStages(shaderStages);
            pipelineCreateInfos.pVertexInputState(vertexInputInfo);
            pipelineCreateInfos.pInputAssemblyState(inputAssembly);
            pipelineCreateInfos.pViewportState(viewportState);
            pipelineCreateInfos.pRasterizationState(rasterizer);
            pipelineCreateInfos.pMultisampleState(multisampling);
            pipelineCreateInfos.pDepthStencilState(depthStencil);
            pipelineCreateInfos.pColorBlendState(colorBlendStateCreateInfo);
            pipelineCreateInfos.layout(this.pipelineLayout);
            pipelineCreateInfos.renderPass(this.renderPass);
            pipelineCreateInfos.subpass(0);
            pipelineCreateInfos.basePipelineHandle(VK10.VK_NULL_HANDLE);
            pipelineCreateInfos.basePipelineIndex(-1);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);

            if (VK10.vkCreateGraphicsPipelines(this.vkDevice, VK10.VK_NULL_HANDLE, pipelineCreateInfos, null, pGraphicsPipeline) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            this.graphicsPipeline = pGraphicsPipeline.get(0);

            // ===> RELEASE RESOURCES <===

            VK10.vkDestroyShaderModule(this.vkDevice, vertShaderModule, null);
            VK10.vkDestroyShaderModule(this.vkDevice, fragShaderModule, null);

            vertShaderSpirv.free();
            fragShaderSpirv.free();
        }
    }

    private long createShaderModule(ByteBuffer spirvCode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if (VK10.vkCreateShaderModule(this.vkDevice, createInfo, null, pShaderModule) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }

    private void createImageViews() {
        this.swapChainImageViews = new ArrayList<>(this.swapChainImages.size());
        for (long swapChainImage : this.swapChainImages) {
            this.swapChainImageViews.add(createImageView(swapChainImage, this.swapChainImageFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1));
        }
    }

    private Long createImageView(long image, int format, int aspactFlags, int mipMapLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.callocStack(stack);

            createInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            createInfo.image(image);
            createInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
            createInfo.format(format);

            createInfo.subresourceRange().aspectMask(aspactFlags);
            createInfo.subresourceRange().baseMipLevel(0);
            createInfo.subresourceRange().levelCount(mipMapLevels);
            createInfo.subresourceRange().baseArrayLayer(0);
            createInfo.subresourceRange().layerCount(1);

            LongBuffer pImageView = stack.mallocLong(1);

            if (VK10.vkCreateImageView(this.vkDevice, createInfo, null, pImageView) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create image views");
            }

            return pImageView.get(0);
        }
    }

    private void recreateSwapChain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);

            while (width.get(0) == 0 && height.get(0) == 0) {
                GLFW.glfwGetFramebufferSize(this.window.getWindowHandle(), width, height);
                GLFW.glfwWaitEvents();
            }
        }

        VK10.vkDeviceWaitIdle(this.vkDevice);
        cleanupSwapChain();
        createSwapChainObjects();
    }

    private void createSwapChain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SwapChainSupportDetails swapChainSupport = VulkanUtils.querySwapChainSupport(this.vkPhysicalDevice, stack, this.surface);

            VkSurfaceFormatKHR surfaceFormat = VulkanUtils.findBestSwapSurfaceFormat(swapChainSupport.getFormats());
            int presentMode = VulkanUtils.chooseSwapPresentMode(swapChainSupport.getPresentMode());
            VkExtent2D vkExtent2D = createSwapExtent(swapChainSupport.getCapabilities(), this.window.getWindowHandle());

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
            createInfoKHR.imageExtent(vkExtent2D);
            createInfoKHR.imageArrayLayers(1);
            createInfoKHR.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

            QueueFamilyIndices queueFamilyIndices = VulkanUtils.findQueueFamiliesFromPhysicalDevice(this.vkPhysicalDevice, this.surface);

            if (!queueFamilyIndices.getGraphicsFamily().equals(queueFamilyIndices.getPresentationFamily())) {
                createInfoKHR.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT);
                createInfoKHR.pQueueFamilyIndices(stack.ints(queueFamilyIndices.getGraphicsFamily(), queueFamilyIndices.getPresentationFamily()));
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

    private void updateUniformBuffer(int currentImage) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            UniformBufferObject ubo = new UniformBufferObject();

            ubo.getModel().rotate((float) (GLFW.glfwGetTime() * Math.toRadians(90)), 0.0f, 0.0f, 1.0f);
            ubo.getView().lookAt(2.0f, 2.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
            ubo.getProjection().perspective((float) Math.toRadians(45),
                    (float) this.swapChainExtent.width() / (float) this.swapChainExtent.height(), 1.0f, 10.0f);
            ubo.getProjection().m11(ubo.getProjection().m11() * -1);

            PointerBuffer data = stack.mallocPointer(1);
            VK10.vkMapMemory(this.vkDevice, this.uniformBuffersMemory.get(currentImage), 0, UniformBufferObject.SIZEOF, 0, data);
            {
                ByteBufferUtils.copyIntoBuffer(data.getByteBuffer(0, UniformBufferObject.SIZEOF), ubo);
            }
            VK10.vkUnmapMemory(this.vkDevice, this.uniformBuffersMemory.get(currentImage));
        }
    }

    private void cleanupSwapChain() {
        VK10.vkDestroyImageView(this.vkDevice, this.colorImageView, null);
        VK10.vkDestroyImage(this.vkDevice, this.colorImage, null);
        VK10.vkFreeMemory(this.vkDevice, this.colorImageMemory, null);

        VK10.vkDestroyImageView(this.vkDevice, this.depthImageView, null);
        VK10.vkDestroyImage(this.vkDevice, this.depthImage, null);
        VK10.vkFreeMemory(this.vkDevice, this.depthImageMemory, null);

        this.uniformBuffers.forEach(uniformBuffer -> VK10.vkDestroyBuffer(this.vkDevice, uniformBuffer, null));
        this.uniformBuffersMemory.forEach(uniformBufferMemory -> VK10.vkFreeMemory(this.vkDevice, uniformBufferMemory, null));

        VK10.vkDestroyDescriptorPool(this.vkDevice, this.descriptorPool, null);

        this.swapChainFrameBuffers.forEach(frameBuffer -> VK10.vkDestroyFramebuffer(this.vkDevice, frameBuffer, null));
        VK10.vkFreeCommandBuffers(this.vkDevice, this.commandPool, LwjglAdapter.asPointBuffer(this.commandBuffers));
        VK10.vkDestroyPipeline(this.vkDevice, this.graphicsPipeline, null);
        VK10.vkDestroyPipelineLayout(this.vkDevice, this.pipelineLayout, null);
        VK10.vkDestroyRenderPass(this.vkDevice, this.renderPass, null);
        this.swapChainImageViews.forEach(imageView -> VK10.vkDestroyImageView(this.vkDevice, imageView, null));
        KHRSwapchain.vkDestroySwapchainKHR(this.vkDevice, this.swapChain, null);
    }

    private void cleanup() {
        cleanupSwapChain();

        VK10.vkDestroySampler(this.vkDevice, this.textureSampler, null);
        VK10.vkDestroyImageView(this.vkDevice, this.textureImageView, null);
        VK10.vkDestroyImage(this.vkDevice, this.textureImage, null);
        VK10.vkFreeMemory(this.vkDevice, this.textureImageMemory, null);

        VK10.vkDestroyDescriptorSetLayout(this.vkDevice, this.descriptorSetLayout, null);
        VK10.vkDestroyBuffer(this.vkDevice, this.indexBuffer, null);
        VK10.vkFreeMemory(this.vkDevice, this.indexBufferMemory, null);

        VK10.vkDestroyBuffer(this.vkDevice, this.vertexBuffer, null);
        VK10.vkFreeMemory(this.vkDevice, this.vertexBufferMemory, null);

        //Ray-tracing cleanup
        NVRayTracing.vkDestroyAccelerationStructureNV(this.vkDevice, this.blas, null);
        VK10.vkFreeMemory(this.vkDevice, this.blasMemory, null);

        this.inFlightFrames.forEach(frame -> {
            VK10.vkDestroySemaphore(this.vkDevice, frame.getRenderFinishedSemaphore(), null);
            VK10.vkDestroySemaphore(this.vkDevice, frame.getImageAvailableSemaphore(), null);
            VK10.vkDestroyFence(this.vkDevice, frame.getFence(), null);
        });
        this.inFlightFrames.clear();

        VK10.vkDestroyCommandPool(this.vkDevice, this.commandPool, null);
        VK10.vkDestroyDevice(this.vkDevice, null);

        if (ENABLE_VALIDATION_LAYERS) {
            this.validationLayers.destroyDebugUtilsMessengerEXT(this.vkInstance, null);
        }

        //must be destroyed before the instance
        KHRSurface.vkDestroySurfaceKHR(this.vkInstance, this.surface, null);

        VK10.vkDestroyInstance(this.vkInstance, null);
        GLFW.glfwDestroyWindow(this.window.getWindowHandle());
        GLFW.glfwTerminate();
    }
}
