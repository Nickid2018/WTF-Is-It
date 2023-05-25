package io.github.nickid2018.mi;

import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.stb.STBImage.*;

public class Simple2048Renderer {

    public static final int WINDOW_WIDTH = 592;
    public static final int WINDOW_HEIGHT = 592 * 3 / 2;
    public static final int ANIMATION_MOVE = 15;
    public static final int ANIMATION_SPAWN = 5;
    public static final int ANIMATION_FRAME = ANIMATION_MOVE + ANIMATION_SPAWN;
    public static final int MAX_FPS = 180;

    // Window Properties ----------------------------
    private static long windowHandle;
    private static long fpsCounter = 0;
    private static long lastRecordTime = 0;
    private static int programID;
    private static int uniformMatrix;
    private static int globalVAO;
    private static int backgroundVAO;
    private static int[] gridVAO = new int[16];
    private static int backgroundTexture;
    private static int gridTexture;
    private static float[] backgroundTransform;
    private static float[][] gridTransform = new float[16][];

    private static int animationFrame = 0;
    private static boolean gameOver = false;
    private static boolean aiMode = false;

    // Game Properties ------------------------------
    private static Simple2048 game;
    private static MoveData[] moveData = new MoveData[16];
    private static boolean[] stays = new boolean[16];
    private static int spawnSlot = -1;
    private static long spawnValue = 0;

    public static void init() throws Exception {
        glfwInit();
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        windowHandle = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Simple 2048", 0, 0);
        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();
        glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        glfwSetKeyCallback(windowHandle, Simple2048Renderer::keyCallback);
        glfwSwapInterval(1);
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
            else if (animationFrame == 0 && !gameOver && !aiMode) {
                boolean moved = false;
                Arrays.fill(stays, false);
                Arrays.fill(moveData, null);
                if (key == GLFW_KEY_UP)
                    moved = game.doMove(MoveDirection.UP);
                else if (key == GLFW_KEY_DOWN)
                    moved = game.doMove(MoveDirection.DOWN);
                else if (key == GLFW_KEY_LEFT)
                    moved = game.doMove(MoveDirection.LEFT);
                else if (key == GLFW_KEY_RIGHT)
                    moved = game.doMove(MoveDirection.RIGHT);
                if (moved) {
                    animationFrame = ANIMATION_FRAME;
                    if (!game.checkContinue())
                        gameOver = true;
                }
            } else if (key == GLFW_KEY_R) {
                game.reset();
                Arrays.fill(stays, false);
                Arrays.fill(moveData, null);
                gameOver = false;
                animationFrame = 0;
            } else if (animationFrame == 0 && key == GLFW_KEY_A)
                aiMode = !aiMode;
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

    public static int makeTexture(String path) throws Exception {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        byte[] gridImage = IOUtils.toByteArray(Simple2048Renderer.class.getResourceAsStream(path));
        ByteBuffer gridBuffer = BufferUtils.createByteBuffer(gridImage.length);
        gridBuffer.put(gridImage);
        gridBuffer.flip();
        int[] x = new int[1];
        int[] y = new int[1];
        int[] channels = new int[1];
        ByteBuffer byteBuffer = stbi_load_from_memory(gridBuffer, x, y, channels, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, x[0], y[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
        stbi_image_free(byteBuffer);
        return texture;
    }

    public static void uploadTextures() throws Exception {
        stbi_set_flip_vertically_on_load(true);
        gridTexture = makeTexture("/grid_2048.png");
        backgroundTexture = makeTexture("/background.png");
    }

    public static int makeSimpleVAO(float[] data) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        int elementIndex = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementIndex);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[]{
                0, 1, 2, 1, 2, 3
        }, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        return vao;
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
        globalVAO = makeSimpleVAO(new float[]{
                0, 0, 0, 0,
                0, 1, 0, 1,
                1, 0, 1, 0,
                1, 1, 1, 1
        });
        backgroundVAO = makeSimpleVAO(new float[]{
                0, 0, 0, 1 - 592 / 1024f,
                0, 1, 0, 1,
                1, 0, 592 / 1024f, 1 - 592 / 1024f,
                1, 1, 592 / 1024f, 1
        });
        // Grid VAO ------------------------------------
        for (int i = 0; i < 16; i++)
            gridVAO[i] = makeSimpleVAO(new float[]{
                    0, 0, (i % 4) * 0.25f, (3 - i / 4) * 0.25f,
                    0, 1, (i % 4) * 0.25f, (3 - i / 4) * 0.25f + 0.25f,
                    1, 0, (i % 4) * 0.25f + 0.25f, (3 - i / 4) * 0.25f,
                    1, 1, (i % 4) * 0.25f + 0.25f, (3 - i / 4) * 0.25f + 0.25f
            });
        backgroundTransform = new Matrix4f()
                .translate(-1, -1, 0)
                .scale(2, 4 / 3f, 1)
                .get(new float[16]);
        for (int i = 0; i < 16; i++)
            gridTransform[i] = new Matrix4f()
                    .translate(toNDCX(((i % 4) * 144 + 16)), toNDCY(16 + (3 - i / 4) * 144), 0)
                    .scale(128 / (592 / 2f), 128 / (592 * 3 / 4f), 1)
                    .get(new float[16]);
    }

    public static void renderSimple() {
        glUseProgram(programID);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++) {
                long val = game.get(i, j);
                if (val == 0)
                    continue;
                glBindVertexArray(gridVAO[(int) (Math.log(val) / Math.log(2)) - 1]);
                glBindTexture(GL_TEXTURE_2D, gridTexture);
                glUniformMatrix4fv(uniformMatrix, false, gridTransform[i * 4 + j]);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }
    }

    public static void renderAnimation() {
        glUseProgram(programID);
        for (int i = 0; i < 16; i++)
            if (stays[i]) {
                long val = game.get(i / 4, i % 4);
                glBindVertexArray(gridVAO[(int) (Math.log(val) / Math.log(2)) - 1]);
                glBindTexture(GL_TEXTURE_2D, gridTexture);
                glUniformMatrix4fv(uniformMatrix, false, gridTransform[i]);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }
        float progress = 1 - (animationFrame - ANIMATION_SPAWN) / (float) ANIMATION_MOVE;
        if (progress > 1)
            progress = 1;
        for (int i = 0; i < 16; i++) {
            MoveData move = moveData[i];
            if (move == null)
                continue;
            float x = toNDCX((i % 4) * 144 + 16) * (1 - progress) + toNDCX((move.toSlot() % 4) * 144 + 16) * progress;
            float y = toNDCY(16 + (3 - i / 4) * 144) * (1 - progress) + toNDCY(16 + (3 - move.toSlot() / 4) * 144) * progress;
            long val = progress > 0.5 ? move.result() : move.source();
            glBindVertexArray(gridVAO[(int) (Math.log(val) / Math.log(2)) - 1]);
            glBindTexture(GL_TEXTURE_2D, gridTexture);
            glUniformMatrix4fv(uniformMatrix, false, new Matrix4f()
                    .translate(x, y, 0)
                    .scale(128 / (592 / 2f), 128 / (592 * 3 / 4f), 1)
                    .get(new float[16]));
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }
        if (animationFrame < ANIMATION_SPAWN) {
            float spawnProgress = 1 - animationFrame / (float) ANIMATION_SPAWN;
            glBindVertexArray(gridVAO[(int) (Math.log(spawnValue) / Math.log(2)) - 1]);
            glBindTexture(GL_TEXTURE_2D, gridTexture);
            glUniformMatrix4fv(uniformMatrix, false, new Matrix4f()
                    .translate(toNDCX((spawnSlot % 4) * 144 + 16 + 64), toNDCY(16 + (3 - spawnSlot / 4) * 144 + 64), 0)
                    .scale(128 / (592 / 2f) * spawnProgress, 128 / (592 * 3 / 4f) * spawnProgress, 1)
                    .translate(-0.5f, -0.5f, 0)
                    .get(new float[16]));
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }
    }

    public static void renderTable() {
        glUseProgram(programID);
        glBindVertexArray(backgroundVAO);
        glBindTexture(GL_TEXTURE_2D, backgroundTexture);
        glUniformMatrix4fv(uniformMatrix, false, backgroundTransform);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        if (animationFrame == 0)
            renderSimple();
        else
            renderAnimation();
    }

    public static void render() {
        renderTable();
    }

    public static void terminate() {
        glfwTerminate();
    }

    public static void main(String[] args) throws Exception {
        init();
        while (!glfwWindowShouldClose(windowHandle)) {
            long lastRenderTime = System.currentTimeMillis();
            glClearColor(0.2f, 0.3f, 0.3f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            render();
            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
            fpsCounter++;
            if (animationFrame > 0)
                animationFrame--;
            if (System.currentTimeMillis() - lastRecordTime > 1000) {
                glfwSetWindowTitle(windowHandle, "Simple 2048 - " + fpsCounter + " FPS");
                fpsCounter = 0;
                lastRecordTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastRenderTime < 1000 / MAX_FPS)
                Thread.sleep(1000 / MAX_FPS - (System.currentTimeMillis() - lastRenderTime));
        }
        terminate();
    }
}
