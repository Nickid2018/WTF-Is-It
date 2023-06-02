package io.github.nickid2018.mi;

import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
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
                .updater(new RmsProp(learningRate))
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

    public void train(int epoch, int decreaseEpoch, float decreaseRate) {
        for (int i = 0; i < epoch; i++) {
            Simple2048 game = new Simple2048(4);
            float[] labels = new float[4];

            while (game.checkContinue()) {
                Simple2048 prevState = game.copy();
                long prevScore = game.getScore();

                if (random.nextFloat() < epsilon) {
                    int action = random.nextInt(4);
                    while (!game.doMove(MoveDirection.values()[action]))
                        action = random.nextInt(4);
                    doForward(labels, prevScore, action, game);
                } else {
                    float max = Float.NEGATIVE_INFINITY;
                    int maxIndex = -1;
                    for (int j = 0; j < 4; j++) {
                        if (game.doMove(MoveDirection.values()[j])) {
                            Simple2048 gameCopy = game.copy();
                            float score = gameCopy.getScore();
                            if (score > max) {
                                max = score;
                                maxIndex = j;
                            }
                        }
                    }
                    if (!game.doMove(MoveDirection.values()[maxIndex]))
                        continue;
                    doForward(labels, prevScore, maxIndex, game);
                }

                if ((i - 1) % decreaseEpoch == 0)
                    epsilon *= decreaseRate;

                replayMemory[replayMemoryIndex] = serializeState(prevState);
                replayLabels[replayMemoryIndex] = labels;
                replayMemoryIndex++;

                if (replayMemoryIndex == replayMemorySize) {
                    replayMemoryIndex = 0;
                    Collections.shuffle(Arrays.asList(replayMemoryIndexArray), random);
                    INDArray[] inputs = new INDArray[1];
                    INDArray[] outputs = new INDArray[1];
                    for (int index : replayMemoryIndexArray) {
                        inputs[0] = Nd4j.createFromArray(replayMemory[index]).reshape(1, 4, 4, 16);
                        outputs[0] = Nd4j.createFromArray(replayLabels[index]).reshape(1, 4);
                        network.fit(inputs, outputs);
                        inputs[0].close();
                        outputs[0].close();
                    }
                    System.out.println("Epoch: " + i + " Epsilon: " + epsilon + " Score: " + network.score());
                }
            }
        }
    }

    public void saveModel(String path) throws IOException {
        ModelSerializer.writeModel(network, path, false);
    }

    public MoveDirection getBestMove(Simple2048 game) {
        INDArray[] inputs = new INDArray[1];
        inputs[0] = Nd4j.createFromArray(serializeState(game)).reshape(1, 4, 4, 16);
        INDArray output = network.output(inputs)[0];
        int index = output.argMax().getInt(0);
        inputs[0].close();
        output.close();
        return MoveDirection.values()[index];
    }

    private void doForward(float[] labels, long prevScore, int inputDirection, Simple2048 game) {
        labels[inputDirection] = game.getScore() - prevScore;
        INDArray inputs = Nd4j.createFromArray(serializeState(game)).reshape(1, 4, 4, 16);
        INDArray output = network.output(inputs)[0];
        float max = output.maxNumber().floatValue();
        labels[inputDirection] += gamma * max;
        inputs.close();
        output.close();
    }

    public static void main(String[] args) throws IOException {
        QLearning qLearning = new QLearning(0.9f, 0.1f, 0.001f, 2000);
        qLearning.train(1000, 10000, 0.9f);
        qLearning.saveModel("model.zip");
    }
}
