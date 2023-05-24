package io.github.nickid2018.mi;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BPNetworkAlgorithmND {

    public interface ActivationFunction {
        INDArray activate(INDArray x);

        INDArray derivative(INDArray y);
        String name();
    }

    public static final ActivationFunction SIGMOID = new ActivationFunction() {
        @Override
        public INDArray activate(INDArray x) {
            return Transforms.sigmoid(x);
        }

        @Override
        public INDArray derivative(INDArray y) {
            return y.mul(y.rsub(1));
        }

        @Override
        public String name() {
            return "sigmoid";
        }
    };

    public static final ActivationFunction TANH = new ActivationFunction() {
        @Override
        public INDArray activate(INDArray x) {
            return Transforms.tanh(x);
        }

        @Override
        public INDArray derivative(INDArray y) {
            return Transforms.hardTanhDerivative(y);
        }

        @Override
        public String name() {
            return "tanh";
        }
    };

    public static final ActivationFunction RELU = new ActivationFunction() {
        @Override
        public INDArray activate(INDArray x) {
            return Transforms.relu(x);
        }

        @Override
        public INDArray derivative(INDArray y) {
            return Transforms.sign(y).gt(0);
        }

        @Override
        public String name() {
            return "relu";
        }
    };

    public static final Map<String, ActivationFunction> ACTIVATION_FUNCTION_MAP = Map.of(
            "sigmoid", SIGMOID,
            "tanh", TANH,
            "relu", RELU
    );

    public interface LossFunction {
        double loss(INDArray x, INDArray y);

        INDArray derivative(INDArray x, INDArray y);
        String name();
    }

    public static final LossFunction MSE = new LossFunction() {
        @Override
        public double loss(INDArray x, INDArray y) {
            return Transforms.pow(x.sub(y), 2).sumNumber().doubleValue() / 2;
        }

        @Override
        public INDArray derivative(INDArray x, INDArray y) {
            return x.sub(y);
        }

        @Override
        public String name() {
            return "mse";
        }
    };

    public static final Map<String, LossFunction> LOSS_FUNCTION_MAP = Map.of(
            "mse", MSE
    );

    public static class HiddenNetLayer {
        protected final int prevNodeCount;
        protected final int nodeCount;
        protected final ActivationFunction activationFunction;
        protected INDArray weightMatrix;
        protected INDArray biasMatrix;

        public HiddenNetLayer(int prevNodeCount, int nodeCount, ActivationFunction activationFunction) {
            this.prevNodeCount = prevNodeCount;
            this.nodeCount = nodeCount;
            this.activationFunction = activationFunction;
            weightMatrix = Nd4j.rand(nodeCount, prevNodeCount).div(100);
            biasMatrix = Nd4j.rand(nodeCount, 1).div(100);
        }

        public HiddenNetLayer(int prevNodeCount, int nodeCount, ActivationFunction activationFunction, INDArray weightMatrix, INDArray biasMatrix) {
            this.prevNodeCount = prevNodeCount;
            this.nodeCount = nodeCount;
            this.activationFunction = activationFunction;
            this.weightMatrix = weightMatrix;
            this.biasMatrix = biasMatrix;
        }

        public INDArray forward(INDArray prevLayer) {
            return activationFunction.activate(weightMatrix.mmul(prevLayer).add(biasMatrix));
        }

        public INDArray backward(INDArray thisReturn, INDArray nextDelta) {
            return weightMatrix.transpose().mmul(nextDelta).mul(activationFunction.derivative(thisReturn));
        }

        public void update(INDArray thisReturn, INDArray delta, double learningRate) {
            INDArray deltaWeight = delta.mmul(thisReturn.transpose()).mul(learningRate);
            INDArray deltaBias = delta.mul(learningRate);
            weightMatrix = weightMatrix.sub(deltaWeight);
            biasMatrix = biasMatrix.sub(deltaBias);
        }
    }

    public static class OutputLayer extends HiddenNetLayer {

        public OutputLayer(int prevNodeCount, int nodeCount, ActivationFunction activationFunction) {
            super(prevNodeCount, nodeCount, activationFunction);
        }

        public OutputLayer(int prevNodeCount, int nodeCount, ActivationFunction activationFunction, INDArray weightMatrix, INDArray biasMatrix) {
            super(prevNodeCount, nodeCount, activationFunction, weightMatrix, biasMatrix);
        }

        public INDArray computeOutputLoss(INDArray output, INDArray target, LossFunction lossFunction) {
            return lossFunction.derivative(output, target).mul(activationFunction.derivative(output));
        }
    }

    public static class BPNetwork {
        private final LossFunction lossFunction;
        private final int inputNodeCount;
        private final List<HiddenNetLayer> layers = new ArrayList<>();
        private OutputLayer outputLayer;

        public BPNetwork(int inputNodeCount, LossFunction lossFunction) {
            this.inputNodeCount = inputNodeCount;
            this.lossFunction = lossFunction;
        }

        public void addLayer(int nodeCount, ActivationFunction activationFunction) {
            layers.add(new HiddenNetLayer(layers.isEmpty() ? inputNodeCount : layers.get(layers.size() - 1).nodeCount,
                    nodeCount, activationFunction));
        }

        public void addOutputLayer(int nodeCount, ActivationFunction activationFunction) {
            outputLayer = new OutputLayer(layers.get(layers.size() - 1).nodeCount, nodeCount, activationFunction);
        }

        public double loss(INDArray input, INDArray output) {
            INDArray matrix = predict(input);
            return lossFunction.loss(matrix, output);
        }

        public INDArray predict(INDArray input) {
            INDArray matrix = input;
            for (HiddenNetLayer layer : layers)
                matrix = layer.forward(matrix);
            matrix = outputLayer.forward(matrix);
            return matrix;
        }

        public void train(INDArray input, INDArray output, double learningRate) {
            INDArray[] returns = new INDArray[layers.size() + 1];
            INDArray[] deltas = new INDArray[layers.size() + 1];
            for (int i = 0; i < layers.size(); i++)
                returns[i] = layers.get(i).forward(i == 0 ? input : returns[i - 1]);
            returns[layers.size()] = outputLayer.forward(returns[layers.size() - 1]);
            deltas[layers.size()] = outputLayer.computeOutputLoss(returns[layers.size()], output, lossFunction);
            deltas[layers.size() - 1] = outputLayer.backward(returns[layers.size() - 1], deltas[layers.size()]);
            for (int i = layers.size() - 1; i >= 1; i--)
                deltas[i - 1] = layers.get(i).backward(returns[i - 1], deltas[i]);
            layers.get(0).update(input, deltas[0], learningRate);
            for (int i = 1; i < layers.size(); i++)
                layers.get(i).update(returns[i - 1], deltas[i], learningRate);
            outputLayer.update(returns[returns.length - 2], deltas[deltas.length - 1], learningRate);
        }
    }

    public static class BPNetworkSerializer {
        public static void save(BPNetwork network, String fileName) throws IOException {
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write("BPNN".getBytes());
            try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeInt(network.inputNodeCount);
                oos.writeUTF(network.lossFunction.name());
                oos.writeInt(network.layers.size());
                for (HiddenNetLayer layer : network.layers) {
                    oos.writeInt(layer.nodeCount);
                    oos.writeUTF(layer.activationFunction.name());
                    oos.writeObject(layer.weightMatrix);
                    oos.writeObject(layer.biasMatrix);
                }
                oos.writeInt(network.outputLayer.nodeCount);
                oos.writeUTF(network.outputLayer.activationFunction.name());
                oos.writeObject(network.outputLayer.weightMatrix);
                oos.writeObject(network.outputLayer.biasMatrix);
            }
        }

        public static BPNetwork load(String fileName) throws IOException, ClassNotFoundException {
            FileInputStream fis = new FileInputStream(fileName);
            byte[] bytes = new byte[4];
            fis.read(bytes, 0, 4);
            if (!"BPNN".equals(new String(bytes)))
                throw new IOException("Please select the correct file!");
            else {
                try (ObjectInputStream ois = new ObjectInputStream(fis)) {
                    int inputNodeCount = ois.readInt();
                    LossFunction lossFunction = LOSS_FUNCTION_MAP.get(ois.readUTF());
                    BPNetwork network = new BPNetwork(inputNodeCount, lossFunction);
                    int layerCount = ois.readInt();
                    for (int i = 0; i < layerCount; i++) {
                        int nodeCount = ois.readInt();
                        ActivationFunction activationFunction = ACTIVATION_FUNCTION_MAP.get(ois.readUTF());
                        HiddenNetLayer layer = new HiddenNetLayer(network.layers.isEmpty() ? inputNodeCount :
                                network.layers.get(network.layers.size() - 1).nodeCount, nodeCount, activationFunction,
                                (INDArray) ois.readObject(), (INDArray) ois.readObject());
                        network.layers.add(layer);
                    }
                    int nodeCount = ois.readInt();
                    ActivationFunction activationFunction = ACTIVATION_FUNCTION_MAP.get(ois.readUTF());
                    network.outputLayer = new OutputLayer(network.layers.get(network.layers.size() - 1).nodeCount,
                            nodeCount, activationFunction, (INDArray) ois.readObject(), (INDArray) ois.readObject());
                    return network;
                }
            }
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(aByte & 0xFF);
            if (hex.length() < 2)
                sb.append(0);
            sb.append(hex);
        }
        return sb.toString();
    }

    public static INDArray[] getImages(String fileName) {
        double[][] x;
        try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(fileName))) {
            byte[] bytes = new byte[4];
            bin.read(bytes, 0, 4);
            if (!"00000803".equals(bytesToHex(bytes)))
                throw new RuntimeException("Please select the correct file!");
            else {
                bin.read(bytes, 0, 4);
                int number = Integer.parseInt(bytesToHex(bytes), 16);
                bin.read(bytes, 0, 4);
                int xPixel = Integer.parseInt(bytesToHex(bytes), 16);
                bin.read(bytes, 0, 4);
                int yPixel = Integer.parseInt(bytesToHex(bytes), 16);
                x = new double[number][xPixel * yPixel];
                for (int i = 0; i < number; i++) {
                    double[] element = new double[xPixel * yPixel];
                    for (int j = 0; j < xPixel * yPixel; j++)
                        element[j] = bin.read() / 255.0;
                    x[i] = element;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        INDArray[] result = new INDArray[x.length];
        for (int i = 0; i < x.length; i++)
            result[i] = Nd4j.create(x[i], x[i].length, 1);
        return result;
    }

    public static INDArray getLabels(String fileName) {
        double[][] y;
        try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(fileName))) {
            byte[] bytes = new byte[4];
            bin.read(bytes, 0, 4);
            if (!"00000801".equals(bytesToHex(bytes)))
                throw new RuntimeException("Please select the correct file!");
            else {
                bin.read(bytes, 0, 4);
                int number = Integer.parseInt(bytesToHex(bytes), 16);
                y = new double[number][1];
                for (int i = 0; i < number; i++) {
                    y[i][0] = bin.read();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Nd4j.create(y);
    }


    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Nd4j.setDefaultDataTypes(DataType.INT32, DataType.DOUBLE);
//        BPNetwork network = new BPNetwork(784, MSE);
//        network.addLayer(256, TANH);
//        network.addOutputLayer(10, RELU);
        BPNetwork network = BPNetworkSerializer.load("D:\\Download\\256relu+10relu.ser");
        IntList order = new IntArrayList();
        for (int i = 0; i < 60000; i++)
            order.add(i);
        for (int i = 0; i < 20; i++) {
            INDArray[] trainImages = getImages("D:\\Download\\train-images.idx3-ubyte");
            INDArray trainLabels = getLabels("D:\\Download\\train-labels.idx1-ubyte");
            INDArray output = Nd4j.zeros(10, 1);
            Collections.shuffle(order);
            int count = 0;
            for (int index : order) {
                count++;
                double label = trainLabels.getDouble(index, 0);
                output.putScalar((int) label, 0, 1);
                network.train(trainImages[index], output, 1e-4);
                if (count % 1000 == 0)
                    System.out.println("Epoch " + i + "/" + count + ": " + network.loss(trainImages[index], output));
                output.putScalar((int) label, 0, 0);
            }
            try {
                BPNetworkSerializer.save(network, "D:\\Download\\network" + i + ".ser");
            } catch (IOException e) {
                e.printStackTrace();
            }
            INDArray[] testImages = getImages("D:\\Download\\t10k-images.idx3-ubyte");
            INDArray testLabels = getLabels("D:\\Download\\t10k-labels.idx1-ubyte");
            int correct = 0;
            for (int index = 0; index < 10000; index++) {
                output = network.predict(testImages[index]).reshape(10);
                int label = 0;
                double max = output.getDouble(0);
                for (int j = 1; j < 10; j++) {
                    if (output.getDouble(j) > max) {
                        max = output.getDouble(j);
                        label = j;
                    }
                }
                if (label == testLabels.getDouble(index, 0))
                    correct++;
            }
            System.out.println("Accuracy: " + correct / 10000.0);
        }
        System.out.println("Training complete!");
    }
}
