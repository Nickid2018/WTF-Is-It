package io.github.nickid2018.mi;

import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.stb.STBImage.*;

public class Simple2048Renderer {

    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 600;
    public static final int ANIMATION_FRAME = 60;

    // Window Properties ----------------------------
    private static long windowHandle;
    private static int animationFrame = 0;
    private static boolean gameOver = false;

    private static int programID;
    private static int uniformMatrix;
    private static int globalVAO;
    private static int[] gridVAO = new int[16];
    private static int gridTexture;

    // Game Properties ------------------------------
    private static Simple2048 game;
    private static MoveData[] moveData = new MoveData[16];
    private static boolean[] stays = new boolean[16];
    private static int spawnSlot = -1;
    private static long spawnValue = 0;

    public static void init() throws Exception {
        glfwInit();
        glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        windowHandle = GLFW.glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "2048", 0, 0);
        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();
        glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        glfwSetKeyCallback(windowHandle, Simple2048Renderer::keyCallback);
        game = new Simple2048(4);
        game.setMoveListener(Simple2048Renderer::moveListener);
        game.setSpawnListener(Simple2048Renderer::spawnListener);
        game.setStayListener(Simple2048Renderer::stayListener);
        uploadTextures();
        compileShadersAndVAO();
    }

    public static void moveListener(int line, int fromSlot, int endSlot, MoveDirection direction, long sourceData, long endData) {
        int slotNow = game.fromLineToSlot(line, fromSlot, direction);
        int slotTo = game.fromLineToSlot(line, endSlot, direction);
        moveData[slotNow] = new MoveData(slotTo, sourceData, endData);
        stays[slotTo] = false;
    }

    public static void spawnListener(int row, int column, long number) {
        spawnSlot = row * game.size() + column;
        spawnValue = number;
    }

    public static void stayListener(int line, int index, MoveDirection direction) {
        stays[game.fromLineToSlot(line, index, direction)] = true;
    }

    public static void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) {
            if (key == GLFW_KEY_ESCAPE)
                glfwSetWindowShouldClose(window, true);
            else if (animationFrame == 0 && !gameOver) {
                Arrays.fill(stays, false);
                Arrays.fill(moveData, null);
                if (key == GLFW_KEY_UP)
                    game.doMove(MoveDirection.UP);
                else if (key == GLFW_KEY_DOWN)
                    game.doMove(MoveDirection.DOWN);
                else if (key == GLFW_KEY_LEFT)
                    game.doMove(MoveDirection.LEFT);
                else if (key == GLFW_KEY_RIGHT)
                    game.doMove(MoveDirection.RIGHT);
                animationFrame = ANIMATION_FRAME;
                if (!game.checkContinue())
                    gameOver = true;
            } else if (animationFrame == 0 && key == GLFW_KEY_R) {
                game.reset();
                gameOver = false;
            }
        }
    }

    public static boolean onAIMove(MoveDirection m) {
        if (animationFrame == 0 && !gameOver) {
            Arrays.fill(stays, false);
            Arrays.fill(moveData, null);
            game.doMove(m);
            animationFrame = ANIMATION_FRAME;
            if (!game.checkContinue()) {
                gameOver = true;
                return false;
            }
            return true;
        }
        return false;
    }

    public static float toNDCX(int x) {
        return (float) x / WINDOW_WIDTH * 2 - 1;
    }

    public static float toNDCY(int y) {
        return (float) y / WINDOW_HEIGHT * 2 - 1;
    }

    public static void uploadTextures() throws Exception {
        stbi_set_flip_vertically_on_load(true);
        gridTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gridTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        byte[] gridImage = IOUtils.toByteArray(Simple2048Renderer.class.getResourceAsStream("/grid_2048.png"));
        ByteBuffer gridBuffer = BufferUtils.createByteBuffer(gridImage.length);
        gridBuffer.put(gridImage);
        gridBuffer.flip();
        int[] x = new int[1];
        int[] y = new int[1];
        int[] channels = new int[1];
        ByteBuffer byteBuffer = stbi_load_from_memory(gridBuffer, x, y, channels, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, x[0], y[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
        stbi_image_free(byteBuffer);
    }

    public static void compileShadersAndVAO() throws Exception {
        // Shader --------------------------------------
        String plainVSH = IOUtils.toString(Simple2048Renderer.class.getResourceAsStream("/plain.vsh"), StandardCharsets.UTF_8);
        String plainFSH = IOUtils.toString(Simple2048Renderer.class.getResourceAsStream("/plain.fsh"), StandardCharsets.UTF_8);
        int plainVSHID = glCreateShader(GL_VERTEX_SHADER);
        int plainFSHID = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(plainVSHID, plainVSH);
        glShaderSource(plainFSHID, plainFSH);
        glCompileShader(plainVSHID);
        if (glGetShaderi(plainVSHID, GL_COMPILE_STATUS) == GL_FALSE)
            throw new Exception(glGetShaderInfoLog(plainVSHID));
        glCompileShader(plainFSHID);
        if (glGetShaderi(plainFSHID, GL_COMPILE_STATUS) == GL_FALSE)
            throw new Exception(glGetShaderInfoLog(plainFSHID));
        programID = glCreateProgram();
        glAttachShader(programID, plainVSHID);
        glAttachShader(programID, plainFSHID);
        glLinkProgram(programID);
        if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE)
            throw new Exception(glGetProgramInfoLog(programID));
        glDeleteShader(plainVSHID);
        glDeleteShader(plainFSHID);
        uniformMatrix = glGetUniformLocation(programID, "transform");
        // VAO -----------------------------------------
        globalVAO = glGenVertexArrays();
        glBindVertexArray(globalVAO);
        int globalVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, globalVBO);
        glBufferData(GL_ARRAY_BUFFER, new float[] {
                0, 0, 0, 0,
                0, 1, 0, 1,
                1, 0, 1, 0,
                1, 1, 1, 1
        }, GL_STATIC_DRAW);
        int elementIndex = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementIndex);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[] {
                0, 1, 2, 1, 2, 3
        }, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        // Grid VAO ------------------------------------
        for (int i = 0; i < 16; i++) {
            gridVAO[i] = glGenVertexArrays();
            glBindVertexArray(gridVAO[i]);
            int gridVBO = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, gridVBO);
            glBufferData(GL_ARRAY_BUFFER, new float[] {
                    0, 0, (i % 4) * 0.25f, (3 - i / 4) * 0.25f,
                    0, 1, (i % 4) * 0.25f, (3 - i / 4) * 0.25f + 0.25f,
                    1, 0, (i % 4) * 0.25f + 0.25f, (3 - i / 4) * 0.25f,
                    1, 1, (i % 4) * 0.25f + 0.25f, (3 - i / 4) * 0.25f + 0.25f
            }, GL_STATIC_DRAW);
            elementIndex = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementIndex);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[] {
                    0, 1, 2, 1, 2, 3
            }, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
            glEnableVertexAttribArray(0);
            glEnableVertexAttribArray(1);
        }
    }

    public static void renderTable() {
        glUseProgram(programID);
        glBindVertexArray(gridVAO[15]);
        glBindTexture(GL_TEXTURE_2D, gridTexture);
        glUniformMatrix4fv(uniformMatrix, false, new Matrix4f().get(new float[16]));
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    public static void render() {
        glClear(GL_COLOR_BUFFER_BIT);
        renderTable();
        glfwSwapBuffers(windowHandle);
        glfwPollEvents();
    }

    public static void terminate() {
        GLFW.glfwTerminate();
    }

    public static void main(String[] args) throws Exception {
        init();
        while (!glfwWindowShouldClose(windowHandle)) {
            render();
            glfwPollEvents();
        }
        terminate();
    }
}
