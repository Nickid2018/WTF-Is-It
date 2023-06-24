package io.github.nickid2018.mi;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.apache.commons.lang3.ArrayUtils;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.ReshapeVertex;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.schedule.ScheduleType;
import org.nd4j.linalg.schedule.StepSchedule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class QLearning {

    public static final double LOG2 = Math.log(2);

    private final float gamma;
    private float epsilon;

    private final float[][][][] replayMemory;
    private final float[][] replayLabels;
    private final int replayMemorySize;
    private final int[] replayMemoryIndexArray;
    private int replayMemoryIndex;
    private final Random random = new Random();

    private final ComputationGraph network;

    public QLearning(float gamma, float epsilon, float learningRate, int replayMemorySize) {
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.replayMemorySize = replayMemorySize;
        replayMemory = new float[replayMemorySize][][][];
        replayLabels = new float[replayMemorySize][4];
        replayMemoryIndexArray = new int[replayMemorySize];
        for (int i = 0; i < replayMemorySize; i++)
            replayMemoryIndexArray[i] = i;

        ComputationGraphConfiguration computationGraphConfiguration = new NeuralNetConfiguration.Builder()
                .updater(new RmsProp(new StepSchedule(ScheduleType.ITERATION, learningRate, 0.9, 1000)))
                .weightInit(WeightInit.XAVIER)
                .graphBuilder()
                .addInputs("input")
                .addLayer("conv1l", new ConvolutionLayer.Builder(1, 2).dataFormat(CNN2DFormat.NHWC).nIn(16).nOut(128).build(), "input")
                .addLayer("conv1r", new ConvolutionLayer.Builder(2, 1).dataFormat(CNN2DFormat.NHWC).nIn(16).nOut(128).build(), "input")
                .addLayer("conv2l1", new ConvolutionLayer.Builder(1, 2).dataFormat(CNN2DFormat.NHWC).nIn(128).nOut(128).build(), "conv1l")
                .addLayer("conv2l2", new ConvolutionLayer.Builder(2, 1).dataFormat(CNN2DFormat.NHWC).nIn(128).nOut(128).build(), "conv1l")
                .addLayer("conv2r1", new ConvolutionLayer.Builder(1, 2).dataFormat(CNN2DFormat.NHWC).nIn(128).nOut(128).build(), "conv1r")
                .addLayer("conv2r2", new ConvolutionLayer.Builder(2, 1).dataFormat(CNN2DFormat.NHWC).nIn(128).nOut(128).build(), "conv1r")
                .addVertex("reConv1l", new ReshapeVertex(1, 128 * 4 * 3), "conv1l")
                .addVertex("reConv1r", new ReshapeVertex(1, 128 * 3 * 4), "conv1r")
                .addVertex("reConv2l1", new ReshapeVertex(1, 128 * 4 * 2), "conv2l1")
                .addVertex("reConv2l2", new ReshapeVertex(1, 128 * 3 * 3), "conv2l2")
                .addVertex("reConv2r1", new ReshapeVertex(1, 128 * 3 * 3), "conv2r1")
                .addVertex("reConv2r2", new ReshapeVertex(1, 128 * 2 * 4), "conv2r2")
                .addLayer("dense", new DenseLayer.Builder().nIn(7424).nOut(256).build(),
                        "reConv1l", "reConv1r", "reConv2l1", "reConv2l2", "reConv2r1", "reConv2r2")
                .addLayer("output", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(256).nOut(4).activation(Activation.IDENTITY).build(), "dense")
                .setOutputs("output")
                .build();
        network = new ComputationGraph(computationGraphConfiguration);
        network.init();
    }

    public QLearning(float gamma, float epsilon, int replayMemorySize, String path) throws IOException {
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.replayMemorySize = replayMemorySize;
        replayMemory = new float[replayMemorySize][][][];
        replayLabels = new float[replayMemorySize][4];
        replayMemoryIndexArray = new int[replayMemorySize];
        for (int i = 0; i < replayMemorySize; i++)
            replayMemoryIndexArray[i] = i;
        network = ModelSerializer.restoreComputationGraph(path);
    }

    private float[][][] serializeState(Simple2048 game) {
        float[][][] state = new float[4][4][16];
        for (int i = 0; i < 16; i++) {
            int row = i / 4;
            int col = i % 4;
            long val = game.get(row, col);
            if (val == 0)
                state[row][col][0] = 1;
            else
                state[row][col][(int) (Math.log(val) / LOG2)] = 1;
        }
        return state;
    }

    private void rotateState(float[][][] state, float[] label) {
        float[][][] newState = new float[4][4][16];
        for (int i = 0; i < 16; i++) {
            int row = i / 4;
            int col = i % 4;
            newState[col][3 - row] = state[row][col];
        }
        float first = label[0];
        for (int i = 0; i < 3; i++)
            label[i] = label[i + 1];
        label[3] = first;
        System.arraycopy(newState, 0, state, 0, 4);
    }

    public void train(int epoch, int decreaseTimes, float decreaseRate) {
        int total = 0;
        int maxSteps = 0;
        long maxValue = 0;
        for (int i = 0; i < epoch; i++) {
            Simple2048 game = new Simple2048(4);
            int rotate = random.nextInt(4);
            for (int j = 0; j < rotate; j++)
                game.rotateRight();
            LongList mergeList = new LongArrayList();

            int steps = 0;
            while (game.checkContinue()) {
                steps++;
                total++;
                Simple2048 prevState = game.copy();
                long prevScore = game.getScore();
                long prevMax = game.getMaxValue();
                long prevSpare = game.getSpareCount();

                INDArray inputsA = Nd4j.createFromArray(serializeState(game)).reshape(1, 4, 4, 16);
                INDArray output = network.output(inputsA)[0];
                float[] labels = output.toFloatVector();
                inputsA.close();
                output.close();

                if (random.nextFloat() < epsilon) {
                    int action = random.nextInt(4);
                    while (!game.doMove(MoveDirection.values()[action], mergeList))
                        action = random.nextInt(4);
                    doForward(labels, prevScore, prevMax, prevSpare, mergeList, action, game);
                    mergeList.clear();
                } else {
                    Simple2048 next = null;
                    MoveDirection[] dirs = getMoveLow(game);
                    for (MoveDirection dir : dirs) {
                        Simple2048 tmp = game.copy();
                        if (tmp.doMove(dir, mergeList)) {
                            doForward(labels, prevScore, prevMax, prevSpare, mergeList, dir.ordinal(), tmp);
                            mergeList.clear();
                            next = tmp;
                        } else
                            labels[dir.ordinal()] = 0;
                    }
                    if (next != null)
                        game = next;
                }

                if (total % decreaseTimes == 0)
                    epsilon *= decreaseRate;

                float[][][] state = serializeState(prevState);
                replayMemory[replayMemoryIndex] = state;
                replayLabels[replayMemoryIndex] = labels;
                replayMemoryIndex++;

                if (replayMemoryIndex == replayMemorySize || (epoch == i + 1 && !game.checkContinue())) {
                    Collections.shuffle(Arrays.asList(replayMemoryIndexArray), random);
                    INDArray[] inputs = new INDArray[1];
                    INDArray[] outputs = new INDArray[1];
                    for (int index : replayMemoryIndexArray) {
                        if (index >= replayMemoryIndex)
                            break;
                        inputs[0] = Nd4j.createFromArray(replayMemory[index]).reshape(1, 4, 4, 16);
                        outputs[0] = Nd4j.createFromArray(replayLabels[index]).reshape(1, 4);
                        network.fit(inputs, outputs);
                        inputs[0].close();
                        outputs[0].close();
                    }
                    System.out.println("Game: " + i + " Epsilon: " + epsilon + " Score: " + network.score()
                            + " Max: " + maxValue + " Steps: " + maxSteps);
                    Simple2048 tmp = new Simple2048(4);
                    int steps2 = 0;
                    while (tmp.checkContinue()) {
                        steps2++;
                        MoveDirection[] direction = getMoveLow(game);
                        for (int j = 3; j >= 0; j--)
                            if (tmp.doMove(direction[j]))
                                break;
                    }
                    System.out.println("Test: " + tmp.getScore() + " " + tmp.getMaxValue() + " " + steps2);
                    replayMemoryIndex = 0;
                }
            }
            if (steps > maxSteps)
                maxSteps = steps;
            if (game.getMaxValue() > maxValue)
                maxValue = game.getMaxValue();
        }
    }

    public void saveModel(String path) throws IOException {
        ModelSerializer.writeModel(network, path, false);
    }

    public MoveDirection[] getMoveLow(Simple2048 game) {
        INDArray inputs = Nd4j.createFromArray(serializeState(game)).reshape(1, 4, 4, 16);
        INDArray output = network.output(inputs)[0];
        float[] values = output.toFloatVector();
        inputs.close();
        output.close();
        MoveDirection[] directions = MoveDirection.values();
        for (int i = 0; i < 4; i++)
            for (int j = i + 1; j < 4; j++)
                if (values[i] > values[j]) {
                    ArrayUtils.swap(values, i, j);
                    ArrayUtils.swap(directions, i, j);
                }
        return directions;
    }

    private void doForward(float[] labels, long prevScore, long prevMax, long prevSpare, LongList mergeList, int inputDirection, Simple2048 game) {
        long maxValue = game.getMaxValue();
        long spareCount = game.getSpareCount() - prevSpare + 1;
        long score = game.getScore() - prevScore;
        if (game.checkContinue()) {
            labels[inputDirection] = (float) (Math.log(maxValue) * 0.02);
            if (mergeList.size() > 0) {
                labels[inputDirection] += mergeList.size() * 0.025f;
                labels[inputDirection] += (float) (Math.log(mergeList.longStream().max().orElse(1)) * 0.01);
            }
            INDArray inputs = Nd4j.createFromArray(serializeState(game)).reshape(1, 4, 4, 16);
            INDArray output = network.output(inputs)[0];
            float max = output.maxNumber().floatValue();
            labels[inputDirection] += gamma * max;
            inputs.close();
            output.close();
        } else
            labels[inputDirection] = 0;
    }

    private void test() {
        long max = 0;
        int maxSteps = 0;
        long maxVal = 0;
        int[] count = new int[16];
        for (int i = 0; i < 1000; i++) {
            if (i % 100 == 0)
                System.out.println(i);
            Simple2048 game = new Simple2048(4);
            int steps = 0;
            while (game.checkContinue()) {
                MoveDirection[] direction = getMoveLow(game);
                for (int j = 3; j >= 0; j--)
                    if (game.doMove(direction[j])) {
                        steps++;
                        break;
                    }
            }
            if (steps > maxSteps)
                maxSteps = steps;
            if (game.getMaxValue() > maxVal)
                maxVal = game.getMaxValue();
            if (game.getScore() > max)
                max = game.getScore();
            count[(int) (Math.log(game.getMaxValue()) / LOG2)]++;
        }
        System.out.println("Max: " + max + " MaxVal: " + maxVal + " MaxSteps: " + maxSteps);
        for (int i = 0; i < 16; i++)
            System.out.println(Math.pow(2, i) + ": " + count[i]);
    }

    public static void main(String[] args) throws IOException {
        QLearning qLearning = new QLearning(0.5f, 0.9f, 1e-4f, 2000);
        System.out.println("Start");
        for (int i = 0; i < 100000; i++) {
            System.out.println("Epoch: " + i);
            qLearning.train(20, 1000, 0.995f);
            qLearning.saveModel("model.zip");
        }
        QLearning qLearning2 = new QLearning(0.9f, 0.1f, 2000, "model.zip");
        qLearning2.test();
    }
}
