package dev.automata.automata.service;
import java.util.*;

public class Hospital {


    static List<List<Integer>> graph;
    static boolean[] visited;


    private static int findComponentCost(int node, int[] A) {
        List<Integer> componentNodes = new ArrayList<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(node);
        visited[node] = true;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            componentNodes.add(u);
            for (int v : graph.get(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    queue.add(v);
                }
            }
        }
        int totalSum = 0;
        for (int city : componentNodes) totalSum += A[city];
        int minCost = Integer.MAX_VALUE;

        for (int city : componentNodes) {
            minCost = Math.min(minCost, totalSum - A[city]);
        }

        return minCost;
    }

    public static int min_cost(int N, int M, int[] A, int[][] roads) {
        graph = new ArrayList<>();
        for (int i = 0; i < N; i++) graph.add(new ArrayList<>());
        for (int[] road : roads) {
            int u = road[0] - 1; // Convert 1-based to 0-based
            int v = road[1] - 1;
            graph.get(u).add(v);
            graph.get(v).add(u);
        }
        visited = new boolean[N];
        int totalCost = 0;

        for (int i = 0; i < N; i++) {
            if (!visited[i]) {
                totalCost += findComponentCost(i, A);
            }
        }

        return totalCost;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int T = sc.nextInt(); // Number of test cases
        while (T-- > 0) {
            int N = sc.nextInt();
            int M = sc.nextInt();
            int[] A = new int[N];
            for (int i = 0; i < N; i++) A[i] = sc.nextInt();

            int[][] roads = new int[M][2];
            for (int i = 0; i < M; i++) {
                roads[i][0] = sc.nextInt();
                roads[i][1] = sc.nextInt();
            }

            System.out.println(min_cost(N, M, A, roads));
        }
        sc.close();
    }
}
