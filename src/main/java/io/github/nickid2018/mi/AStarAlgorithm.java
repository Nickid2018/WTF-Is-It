package io.github.nickid2018.mi;

import java.util.*;

public class AStarAlgorithm {

    public interface AStarNode {
        AStarNode getParent();
        double getG();
        double getH();
        default double getF() {
            return getG() + getH();
        }
        boolean nodeEquals(AStarNode node);
    }

    public static class AStarNodeEvaluator<T extends AStarNode> {
        public void evaluate(T fromNode, T targetNode, List<T> nextNodes, Set<T> closedNodes) {
            removeClosedNodes(nextNodes, closedNodes);
        }
        public void removeClosedNodes(List<T> nextNodes, Set<T> closedNodes) {
            nextNodes.removeIf(node -> closedNodes.stream().anyMatch(node::nodeEquals));
        }
    }

    public static <T extends AStarNode> T findPath(T startNode, T targetNode, AStarNodeEvaluator<T> evaluator) {
        PriorityQueue<T> openNodes = new PriorityQueue<>((o1, o2) -> (int) (o1.getF() - o2.getF()));
        Set<T> closedNodes = new HashSet<>();
        List<T> nextNodes = new LinkedList<>();
        openNodes.add(startNode);
        while (!openNodes.isEmpty()) {
            T node = openNodes.poll();
            if (node.nodeEquals(targetNode))
                return node;
            closedNodes.add(node);
            evaluator.evaluate(node, targetNode, nextNodes, closedNodes);
            for (T nextNode : nextNodes) {
                if (openNodes.contains(nextNode))
                    openNodes.stream().filter(n -> n.nodeEquals(node)).findFirst().ifPresent(oldNode -> {
                        if (oldNode.getF() > nextNode.getF()) {
                            openNodes.remove(oldNode);
                            openNodes.add(nextNode);
                        }
                    });
                else
                    openNodes.add(nextNode);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends AStarNode> List<T> reconstructPath(T node) {
        List<T> path = new ArrayList<>();
        while (node != null) {
            path.add(node);
            node = (T) node.getParent();
        }
        Collections.reverse(path);
        return path;
    }

    public static class EightNumberNode implements AStarNode {
        public static final int[] TARGET = new int[]{1, 2, 3, 8, 0, 4, 7, 6, 5};
        private final int[] numbers;
        private final int g;
        private final int h;
        private final EightNumberNode parent;

        public EightNumberNode(EightNumberNode parent, int[] numbers) {
            this.numbers = numbers;
            this.parent = parent;
            this.g = parent == null ? 0 : parent.g + 1;
            int notMatch = 0;
            for (int i = 0; i < 9; i++)
                if (numbers[i] != 0 && numbers[i] != TARGET[i])
                    notMatch++;
            this.h = notMatch;
        }

        @Override
        public AStarNode getParent() {
            return parent;
        }

        @Override
        public double getG() {
            return g;
        }

        @Override
        public double getH() {
            return h;
        }

        @Override
        public boolean nodeEquals(AStarNode node) {
            return Arrays.equals(numbers, ((EightNumberNode) node).numbers);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(numbers);
        }
    }

    public static void main(String[] args) {
        EightNumberNode start = new EightNumberNode(null, new int[]{2, 8, 3, 1, 6, 4, 7, 0, 5});
        EightNumberNode target = new EightNumberNode(null, EightNumberNode.TARGET);
        EightNumberNode end = findPath(start, target, new AStarNodeEvaluator<>() {
            @Override
            public void evaluate(EightNumberNode fromNode, EightNumberNode targetNode,
                                 List<EightNumberNode> nextNodes, Set<EightNumberNode> closedNodes) {
                int[] numbers = fromNode.numbers;
                int zeroIndex = 0;
                for (int i = 0; i < 9; i++)
                    if (numbers[i] == 0) {
                        zeroIndex = i;
                        break;
                    }
                if (zeroIndex % 3 != 0) {
                    int[] newNumbers = Arrays.copyOf(numbers, 9);
                    newNumbers[zeroIndex] = newNumbers[zeroIndex - 1];
                    newNumbers[zeroIndex - 1] = 0;
                    nextNodes.add(new EightNumberNode(fromNode, newNumbers));
                }
                if (zeroIndex % 3 != 2) {
                    int[] newNumbers = Arrays.copyOf(numbers, 9);
                    newNumbers[zeroIndex] = newNumbers[zeroIndex + 1];
                    newNumbers[zeroIndex + 1] = 0;
                    nextNodes.add(new EightNumberNode(fromNode, newNumbers));
                }
                if (zeroIndex / 3 != 0) {
                    int[] newNumbers = Arrays.copyOf(numbers, 9);
                    newNumbers[zeroIndex] = newNumbers[zeroIndex - 3];
                    newNumbers[zeroIndex - 3] = 0;
                    nextNodes.add(new EightNumberNode(fromNode, newNumbers));
                }
                if (zeroIndex / 3 != 2) {
                    int[] newNumbers = Arrays.copyOf(numbers, 9);
                    newNumbers[zeroIndex] = newNumbers[zeroIndex + 3];
                    newNumbers[zeroIndex + 3] = 0;
                    nextNodes.add(new EightNumberNode(fromNode, newNumbers));
                }
                super.evaluate(fromNode, targetNode, nextNodes, closedNodes);
            }
        });
        List<EightNumberNode> path = reconstructPath(end);
        for (EightNumberNode node : path) {
            int[] numbers = node.numbers;
            for (int i = 0; i < 9; i++) {
                System.out.print(numbers[i]);
                if (i % 3 == 2)
                    System.out.println();
                else
                    System.out.print(" ");
            }
            System.out.println();
        }
    }
}
