package org.secuso.privacyfriendlysudoku.controller.qqwing;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class QQWingController {
    final QQWingOptions opts = new QQWingOptions();

    private int[] level;
    private int[] solution;
    private LinkedList<int[]> generated = new LinkedList<>();
    private boolean solveImpossible = false;

    public Generation generate(GameType type, GameDifficulty difficulty) {
        generated.clear();
        opts.gameDifficulty = difficulty;
        opts.action = Action.GENERATE;
        opts.needNow = true;
        opts.printSolution = false;
        opts.threads = Runtime.getRuntime().availableProcessors();
        opts.gameType = type;
        doAction();
        return new Generation(generated.poll(), solution);
    }

    public static record Generation(int[] puzzle, int[] solution) {}

    public LinkedList<int[]> generateMultiple(GameType type, GameDifficulty difficulty, int amount) {
        generated.clear();
        opts.numberToGenerate = amount;
        opts.gameDifficulty = difficulty;
        opts.needNow = true;
        opts.action = Action.GENERATE;
        opts.printSolution = false;
        opts.threads = Runtime.getRuntime().availableProcessors();
        opts.gameType = type;
        doAction();
        return generated;
    }

    /**
     * Generate a new sudoku based on a given seed regardless of outcome difficulty
     * @param seed the seed based on which the sudoku should be calculated
     * @return the generated sudoku
     */
    public int[] generateFromSeed(int seed) {
        return generateFromSeed(seed, 1, 1);
    }

    /**
     * Generate a new sudoku based on a given seed, but only accept challenge sudokus with a certain probability
     * @param seed the seed based on which the sudoku should be calculated
     * @param challengePermission the probability with which a challenge sudoku is accepted upon calculation
     * @param challengeIterations the amount of times a challenge sudoku can be rejected in a row before being
     *                            accepted with a probability of 100%
     * @return the generated sudoku
     */
    public int[] generateFromSeed(int seed, double challengePermission, int challengeIterations) {
        generated.clear();
        QQWing generator = new QQWing(GameType.Default_9x9, GameDifficulty.Unspecified);
        boolean continueSearch = true;
        Random random = new Random(seed);
        int seedFactor = 2;

        while(continueSearch && challengeIterations > 0) {
            seed *= seedFactor;
            generator.setRandom(seed);
            generator.setRecordHistory(true);
            generator.generatePuzzle();

            if (generator.getDifficulty() != GameDifficulty.Challenge || random.nextDouble() < challengePermission) {
                continueSearch = false;
            } else {
                challengeIterations--;
            }
        }

        generated.add(generator.getPuzzle());
        opts.gameType = GameType.Default_9x9;
        opts.gameDifficulty = generator.getDifficulty();
        return generated.poll();
    }

    public int[] solve(int[] board) {
        level = new int[board.length];
        solveImpossible = false;

        for (int i = 0; i < board.length; i++) {
            level[i] = board[i];
        }

        opts.needNow = true;
        opts.action = Action.SOLVE;
        opts.printSolution = true;
        opts.threads = 1;
        opts.gameType = GameType.of(board.length);
        doAction();
        //if(solveImpossible) {
        // Can not occur with normal use of the app.
        //}
        return solution;
    }

    private void doAction() {
        // The number of puzzles solved or generated.
        final AtomicInteger puzzleCount = new AtomicInteger(0);
        final AtomicBoolean done = new AtomicBoolean(false);

        Thread[] threads = new Thread[opts.threads];
        for (int threadCount = 0; threadCount < threads.length; threadCount++) {
            threads[threadCount] = new Thread(
                    new Runnable() {

                        // Create a new puzzle board
                        // and set the options
                        private QQWing ss = createQQWing();

                        private QQWing createQQWing() {
                            QQWing ss = new QQWing(opts.gameType, opts.gameDifficulty);
                            ss.setRecordHistory(opts.printHistory || opts.printInstructions || opts.printStats || opts.gameDifficulty != GameDifficulty.Unspecified);
                            ss.setLogHistory(opts.logHistory);
                            ss.setPrintStyle(opts.printStyle);
                            return ss;
                        }

                        @Override
                        public void run() {
                            try {

                                // Solve puzzle or generate puzzles
                                // until end of input for solving, or
                                // until we have generated the specified number.
                                while (!done.get()) {

                                    // Record whether the puzzle was possible or
                                    // not,
                                    // so that we don't try to solve impossible
                                    // givens.
                                    boolean havePuzzle;

                                    if (opts.action == Action.GENERATE) {
                                        // Generate a puzzle
                                        havePuzzle = ss.generatePuzzleSymmetry(opts.symmetry);

                                    } else {
                                        // Read the next puzzle on STDIN
                                        int[] puzzle = new int[QQWing.BOARD_SIZE];
                                        if (getPuzzleToSolve(puzzle)) {
                                            havePuzzle = ss.setPuzzle(puzzle);
                                            if (havePuzzle) {
                                                puzzleCount.getAndDecrement();
                                            } else {
                                                // Puzzle to solve is impossible.
                                                solveImpossible = true;
                                            }
                                        } else {
                                            // Set loop to terminate when nothing is
                                            // left on STDIN
                                            havePuzzle = false;
                                            done.set(true);
                                        }
                                        puzzle = null;
                                    }

                                    int solutions = 0;

                                    if (havePuzzle) {

                                        // Count the solutions if requested.
                                        // (Must be done before solving, as it would
                                        // mess up the stats.)
                                        //if (opts.countSolutions) {
                                        //    solutions = ss.countSolutions();
                                        //}

                                        // Solve the puzzle
                                        if (opts.printSolution || opts.printHistory || opts.printStats || opts.printInstructions || opts.gameDifficulty != GameDifficulty.Unspecified) {
                                            ss.solve();
                                            solution = ss.getSolution();
                                        }

                                        // Bail out if it didn't meet the difficulty
                                        // standards for generation
                                        if (opts.action == Action.GENERATE) {
                                            if (opts.gameDifficulty != GameDifficulty.Unspecified && opts.gameDifficulty != ss.getDifficulty()) {
                                                havePuzzle = false;
                                                // check if other threads have
                                                // finished the job
                                                if (puzzleCount.get() >= opts.numberToGenerate) done.set(true);
                                            } else {
                                                int numDone = puzzleCount.incrementAndGet();
                                                if (numDone >= opts.numberToGenerate) done.set(true);
                                                if (numDone > opts.numberToGenerate) havePuzzle = false;
                                            }
                                        }
                                        if(havePuzzle) {
                                            generated.add(ss.getPuzzle());
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                return;
                            }
                        }

                    }
            );
            threads[threadCount].start();
        }

        if(opts.needNow) {
            for (int i = 0; i < threads.length; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean isImpossible() {
        return solveImpossible;
    }

    private static class QQWingOptions {
        // defaults for options
        boolean needNow = false;
        boolean printPuzzle = false;
        boolean printSolution = false;
        boolean printHistory = false;
        boolean printInstructions = false;
        boolean timer = false;
        boolean countSolutions = false;
        Action action = Action.NONE;
        boolean logHistory = false;
        PrintStyle printStyle = PrintStyle.READABLE;
        int numberToGenerate = 1;
        boolean printStats = false;
        GameDifficulty gameDifficulty = GameDifficulty.Unspecified;
        GameType gameType = GameType.Unspecified;
        Symmetry symmetry = Symmetry.NONE;
        int threads = Runtime.getRuntime().availableProcessors();
    }

    private boolean getPuzzleToSolve(int[] puzzle) {
        if(level != null) {
            if(puzzle.length == level.length) {
                for(int i = 0; i < level.length; i++) {
                    puzzle[i] = level[i];
                }
            }
            level = null;
            return true;
        }
        return false;
    }
}