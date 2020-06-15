package vulkan.tutorial;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;
import vulkan.tutorial.math.Vertex;
import vulkan.tutorial.shader.SPIRV;
import vulkan.tutorial.shader.ShaderKind;
import vulkan.tutorial.shader.ShaderSPIRVUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
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
        private static final int MAX_FRAMES_IN_FLIGHT = 2;
        private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

        static {
            if (ENABLE_VALIDATION_LAYERS) {
                VALIDATION_LAYERS = new HashSet<>();
                VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_validation");
            } else {
                VALIDATION_LAYERS = null;
            }
        }

        boolean frameBufferResize;
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
        private List<Long> swapChainImageViews;
        private int swapChainImageFormat;
        private VkExtent2D swapChainExtent;
        private List<Long> swapChainFrameBuffers;
        private long pipelineLayout;
        private long renderPass;
        private long graphicsPipeline;
        private long commandPool;

        private long vertexBuffer;
        private long vertexBufferMemory;

        private long indexBuffer;
        private long indexBufferMemory;

        private List<VkCommandBuffer> commandBuffers;
        private List<Frame> inFlightFrames;
        private Map<Integer, Frame> imagesInFlight;
        private int currentFrame;

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
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

            String title = getClass().getEnclosingClass().getSimpleName();

            this.window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, title, MemoryUtil.NULL, MemoryUtil.NULL);

            if (this.window == MemoryUtil.NULL) {
                throw new RuntimeException("Cannot create window");
            }

            GLFW.glfwSetFramebufferSizeCallback(this.window, this::frameBufferSizeCallback);
        }

        private void frameBufferSizeCallback(long window, int width, int height) {
            this.frameBufferResize = true;
        }

        private void initVulkan() {
            createInstance();
            setupDebugMessenger();
            createSurface();
            pickPhysicalDevice();
            createLogicalDevice();
            createCommandPool();
            createVertexBuffer();
            createIndexBuffer();
            createSwapChainObjects();
            createSyncObjects();
        }

        private void createIndexBuffer() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                long bufferSize = Short.BYTES * Vertex.INDICES.length;

                LongBuffer pBuffer = stack.mallocLong(1);
                LongBuffer pBufferMemory = stack.mallocLong(1);

                createBuffer(bufferSize,
                        VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer,
                        pBufferMemory);

                long stagingBuffer = pBuffer.get(0);
                long stagingBufferMemory = pBufferMemory.get(0);

                PointerBuffer data = stack.mallocPointer(1);

                VK10.vkMapMemory(this.vkDevice, stagingBufferMemory, 0, bufferSize, 0, data);
                {
                    memcpy(data.getByteBuffer(0, (int) bufferSize), Vertex.INDICES);
                }
                VK10.vkUnmapMemory(this.vkDevice, stagingBufferMemory);

                createBuffer(bufferSize,
                        VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                        VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                        pBuffer,
                        pBufferMemory);

                this.indexBuffer = pBuffer.get(0);
                this.indexBufferMemory = pBufferMemory.get(0);

                copyBuffer(stagingBuffer, this.indexBuffer, bufferSize);

                VK10.vkDestroyBuffer(this.vkDevice, stagingBuffer, null);
                VK10.vkFreeMemory(this.vkDevice, stagingBufferMemory, null);
            }
        }

        private void createVertexBuffer() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                long bufferSize = Vertex.SIZEOF * Vertex.VERTICES.length;

                LongBuffer pBuffer = stack.mallocLong(1);
                LongBuffer pBufferMemory = stack.mallocLong(1);
                createBuffer(bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer,
                        pBufferMemory);

                long stagingBuffer = pBuffer.get(0);
                long stagingBufferMemory = pBufferMemory.get(0);

                PointerBuffer data = stack.mallocPointer(1);

                VK10.vkMapMemory(this.vkDevice, stagingBufferMemory, 0, bufferSize, 0, data);
                {
                    memcpy(data.getByteBuffer(0, (int) bufferSize), Vertex.VERTICES);
                }
                VK10.vkUnmapMemory(this.vkDevice, stagingBufferMemory);

                createBuffer(bufferSize,
                        VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                        VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                        pBuffer,
                        pBufferMemory);

                this.vertexBuffer = pBuffer.get(0);
                this.vertexBufferMemory = pBufferMemory.get(0);

                copyBuffer(stagingBuffer, this.vertexBuffer, bufferSize);

                VK10.vkDestroyBuffer(this.vkDevice, stagingBuffer, null);
                VK10.vkFreeMemory(this.vkDevice, stagingBufferMemory, null);
            }
        }

        private void copyBuffer(long srcBuffer, long dstBuffer, long size) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.callocStack(stack);
                allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
                allocateInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocateInfo.commandPool(this.commandPool);
                allocateInfo.commandBufferCount(1);

                PointerBuffer pCommandBuffer = stack.mallocPointer(1);
                VK10.vkAllocateCommandBuffers(this.vkDevice, allocateInfo, pCommandBuffer);

                VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), this.vkDevice);
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
                beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                beginInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                VK10.vkBeginCommandBuffer(commandBuffer, beginInfo);
                {
                    VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
                    copyRegion.size(size);
                    VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
                }
                VK10.vkEndCommandBuffer(commandBuffer);

                VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
                submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
                submitInfo.pCommandBuffers(pCommandBuffer);

                if (VK10.vkQueueSubmit(this.vkGraphicsQueue, submitInfo, VK10.VK_NULL_HANDLE) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to submit copy command buffer");
                }

                VK10.vkQueueWaitIdle(this.vkGraphicsQueue);
                VK10.vkFreeCommandBuffers(this.vkDevice, this.commandPool, pCommandBuffer);
            }
        }

        private void createBuffer(long size, int usage, int properties, LongBuffer pBuffer, LongBuffer pBufferMemory) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.callocStack(stack);
                bufferCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
                bufferCreateInfo.size(size);
                bufferCreateInfo.usage(usage);
                bufferCreateInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

                if (VK10.vkCreateBuffer(this.vkDevice, bufferCreateInfo, null, pBuffer) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create vertex buffer");
                }

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.mallocStack(stack);
                VK10.vkGetBufferMemoryRequirements(this.vkDevice, pBuffer.get(0), memoryRequirements);

                VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.callocStack(stack);
                allocateInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
                allocateInfo.allocationSize(memoryRequirements.size());
                allocateInfo.memoryTypeIndex(findMemoryType(memoryRequirements.memoryTypeBits(), properties));

                if (VK10.vkAllocateMemory(this.vkDevice, allocateInfo, null, pBufferMemory) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate vertex buffer memory");
                }

                VK10.vkBindBufferMemory(this.vkDevice, pBuffer.get(0), pBufferMemory.get(0), 0);
            }
        }

        private void memcpy(ByteBuffer byteBuffer, Vertex[] vertices) {
            for (Vertex vertex : vertices) {
                byteBuffer.putFloat(vertex.getPos().x());
                byteBuffer.putFloat(vertex.getPos().y());

                byteBuffer.putFloat(vertex.getColor().x());
                byteBuffer.putFloat(vertex.getColor().y());
                byteBuffer.putFloat(vertex.getColor().z());
            }
        }

        private void memcpy(ByteBuffer byteBuffer, short[] indices) {
            for (short index : indices) {
                byteBuffer.putShort(index);
            }

            byteBuffer.rewind();
        }

        private int findMemoryType(int typeFilter, int properties) {
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.mallocStack();
            VK10.vkGetPhysicalDeviceMemoryProperties(this.vkPhysicalDevice, memoryProperties);

            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 && (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }

            throw new RuntimeException("Failed to find suitable memory type");
        }


        private void createSwapChainObjects() {
            createSwapChain();
            createImageViews();
            createRenderPass();
            createGraphicsPipeline();
            createFrameBuffers();
            createCommandBuffers();
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
                VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
                clearValues.color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
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
                        LongBuffer offsets = stack.longs(0);
                        VK10.vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets);
                        VK10.vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer, 0, VK10.VK_INDEX_TYPE_UINT16);

                        VK10.vkCmdDrawIndexed(commandBuffer, Vertex.INDICES.length, 1, 0, 0, 0);
                    }
                    VK10.vkCmdEndRenderPass(commandBuffer);

                    if (VK10.vkEndCommandBuffer(commandBuffer) != VK10.VK_SUCCESS) {
                        throw new RuntimeException("Failed to record command buffer");
                    }
                }
            }
        }

        private void createCommandPool() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                QueueFamilyIndices queueFamilyIndices = findQueueFamilies(this.vkPhysicalDevice);

                VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
                poolInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
                poolInfo.queueFamilyIndex(queueFamilyIndices.getGraphicsFamily());

                LongBuffer pCommandPool = stack.mallocLong(1);

                if (VK10.vkCreateCommandPool(this.vkDevice, poolInfo, null, pCommandPool) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create command pool");
                }

                this.commandPool = pCommandPool.get(0);
            }
        }

        private void createFrameBuffers() {
            this.swapChainFrameBuffers = new ArrayList<>(this.swapChainImageViews.size());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer attachments = stack.mallocLong(1);
                LongBuffer pFrameBuffer = stack.mallocLong(1);

                //Lets allocate the create info struct once and just update the pAttachments field each iteration
                VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.callocStack(stack);
                framebufferCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                framebufferCreateInfo.renderPass(this.renderPass);
                framebufferCreateInfo.width(this.swapChainExtent.width());
                framebufferCreateInfo.height(this.swapChainExtent.height());
                framebufferCreateInfo.layers(1);

                for (long imageView : this.swapChainImageViews) {
                    attachments.put(0, imageView);

                    framebufferCreateInfo.pAttachments(attachments);

                    if (VK10.vkCreateFramebuffer(this.vkDevice, framebufferCreateInfo, null, pFrameBuffer) != VK10.VK_SUCCESS) {
                        throw new RuntimeException("Failed to create framebuffer");
                    }

                    this.swapChainFrameBuffers.add(pFrameBuffer.get(0));
                }
            }
        }

        private void createRenderPass() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.callocStack(1, stack);
                colorAttachment.format(this.swapChainImageFormat);
                colorAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
                colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
                colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
                colorAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                colorAttachment.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
                colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                colorAttachment.finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

                VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.callocStack(1, stack);
                colorAttachmentRef.attachment(0);
                colorAttachmentRef.layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
                subpass.pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);
                subpass.colorAttachmentCount(1);
                subpass.pColorAttachments(colorAttachmentRef);

                VkSubpassDependency.Buffer dependency = VkSubpassDependency.callocStack(1, stack);
                dependency.srcSubpass(VK10.VK_SUBPASS_EXTERNAL);
                dependency.dstSubpass(0);
                dependency.srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
                dependency.srcAccessMask(0);
                dependency.dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
                dependency.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

                VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack);
                renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
                renderPassInfo.pAttachments(colorAttachment);
                renderPassInfo.pSubpasses(subpass);
                renderPassInfo.pDependencies(dependency);

                LongBuffer pRenderPass = stack.mallocLong(1);

                if (VK10.vkCreateRenderPass(this.vkDevice, renderPassInfo, null, pRenderPass) != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to create redner pass");
                }

                this.renderPass = pRenderPass.get(0);
            }
        }

        private void createGraphicsPipeline() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                //Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
                //Check ShaderSPIRVUtils class to see how it can be done
                SPIRV vertShaderSpirv = ShaderSPIRVUtils.compileShaderFile("shaders/shader.vert", ShaderKind.VERTEX_SHADER);
                SPIRV fragShaderSpirv = ShaderSPIRVUtils.compileShaderFile("shaders/shader.frag", ShaderKind.FRAGMENT_SHADER);

                long vertShaderModule = createShaderModule(vertShaderSpirv.bytecode());
                long fragShaderModule = createShaderModule(fragShaderSpirv.bytecode());

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
                rasterizer.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE);
                rasterizer.depthBiasEnable(false);

                // ===> MULTISAMPLING <===
                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
                multisampling.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
                multisampling.sampleShadingEnable(false);
                multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

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

            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pImageView = stack.mallocLong(1);

                for (long swapChainImage : this.swapChainImages) {
                    VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.callocStack(stack);

                    createInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
                    createInfo.image(swapChainImage);
                    createInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
                    createInfo.format(this.swapChainImageFormat);

                    createInfo.components().r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);

                    createInfo.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                    createInfo.subresourceRange().baseMipLevel(0);
                    createInfo.subresourceRange().levelCount(1);
                    createInfo.subresourceRange().baseArrayLayer(0);
                    createInfo.subresourceRange().layerCount(1);

                    if (VK10.vkCreateImageView(this.vkDevice, createInfo, null, pImageView) != VK10.VK_SUCCESS) {
                        throw new RuntimeException("Failed to create image views");
                    }

                    this.swapChainImageViews.add(pImageView.get(0));
                }
            }
        }

        private void recreateSwapChain() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.ints(0);
                IntBuffer height = stack.ints(0);

                while (width.get(0) == 0 && height.get(0) == 0) {
                    GLFW.glfwGetFramebufferSize(this.window, width, height);
                    GLFW.glfwWaitEvents();
                }
            }

            VK10.vkDeviceWaitIdle(this.vkDevice);
            cleanupSwapChain();
            createSwapChainObjects();
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
                createInfoKHR.imageExtent(vkExtent2D);
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

        private PointerBuffer asPointBuffer(Collection<String> collection) {
            MemoryStack stack = MemoryStack.stackGet();

            PointerBuffer buffer = stack.mallocPointer(collection.size());

            collection.stream().map(stack::UTF8).forEach(buffer::put);

            return buffer.rewind();
        }

        private PointerBuffer asPointBuffer(List<? extends Pointer> list) {
            MemoryStack stack = MemoryStack.stackGet();
            PointerBuffer buffer = stack.mallocPointer(list.size());

            list.forEach(buffer::put);

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

            IntBuffer width = MemoryStack.stackGet().ints(0);
            IntBuffer height = MemoryStack.stackGet().ints(0);

            GLFW.glfwGetFramebufferSize(this.window, width, height);

            VkExtent2D actualExtent = VkExtent2D.mallocStack().set(width.get(0), height.get(0));

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
            int fps = 0;
            double lastTime = System.currentTimeMillis();
            while (!GLFW.glfwWindowShouldClose(this.window)) {
                if (System.currentTimeMillis() - lastTime >= 1000) {
                    GLFW.glfwSetWindowTitle(this.window, String.valueOf(fps));
                    lastTime = System.currentTimeMillis();
                    fps = 0;
                }
                GLFW.glfwPollEvents();
                drawFrame();
//                System.out.println("FramesInFLight: " + this.imagesInFlight);
                fps++;
            }

            VK10.vkDeviceWaitIdle(this.vkDevice);
        }

        private void drawFrame() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Frame thisFrame = this.inFlightFrames.get(this.currentFrame);

                VK10.vkWaitForFences(this.vkDevice, thisFrame.pFence(), true, UINT64_MAX);

                IntBuffer pImageIndex = stack.mallocInt(1);

                int vkResult = KHRSwapchain.vkAcquireNextImageKHR(this.vkDevice, this.swapChain, UINT64_MAX, thisFrame.getImageAvailableSemaphore(), VK10.VK_NULL_HANDLE, pImageIndex);

                if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                    recreateSwapChain();
                    return;
                } else if (vkResult != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Cannot get image");
                }

                final int imageIndex = pImageIndex.get(0);

                if (this.imagesInFlight.containsKey(imageIndex)) {
                    VK10.vkWaitForFences(this.vkDevice, this.imagesInFlight.get(imageIndex).getFence(), true, UINT64_MAX);
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

                if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || vkResult == KHRSwapchain.VK_SUBOPTIMAL_KHR || this.frameBufferResize) {
                    this.frameBufferResize = false;
                    recreateSwapChain();
                } else if (vkResult != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to present swap chain image");
                }

                this.currentFrame = (this.currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            }
        }


        private void cleanupSwapChain() {
            this.swapChainFrameBuffers.forEach(frameBuffer -> VK10.vkDestroyFramebuffer(this.vkDevice, frameBuffer, null));
            VK10.vkFreeCommandBuffers(this.vkDevice, this.commandPool, asPointBuffer(this.commandBuffers));
            VK10.vkDestroyPipeline(this.vkDevice, this.graphicsPipeline, null);
            VK10.vkDestroyPipelineLayout(this.vkDevice, this.pipelineLayout, null);
            VK10.vkDestroyRenderPass(this.vkDevice, this.renderPass, null);
            this.swapChainImageViews.forEach(imageView -> VK10.vkDestroyImageView(this.vkDevice, imageView, null));
            KHRSwapchain.vkDestroySwapchainKHR(this.vkDevice, this.swapChain, null);
        }

        private void cleanup() {
            cleanupSwapChain();

            VK10.vkDestroyBuffer(this.vkDevice, this.indexBuffer, null);
            VK10.vkFreeMemory(this.vkDevice, this.indexBufferMemory, null);

            VK10.vkDestroyBuffer(this.vkDevice, this.vertexBuffer, null);
            VK10.vkFreeMemory(this.vkDevice, this.vertexBufferMemory, null);

            this.inFlightFrames.forEach(frame -> {
                VK10.vkDestroySemaphore(this.vkDevice, frame.getRenderFinishedSemaphore(), null);
                VK10.vkDestroySemaphore(this.vkDevice, frame.getImageAvailableSemaphore(), null);
                VK10.vkDestroyFence(this.vkDevice, frame.getFence(), null);
            });
            this.inFlightFrames.clear();

            VK10.vkDestroyCommandPool(this.vkDevice, this.commandPool, null);
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
