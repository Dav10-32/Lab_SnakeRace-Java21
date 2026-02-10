package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.concurrent.ThreadLocalRandom;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 40;
  private final int turboSleepMs = 20;
  private int turboTicks = 0;

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private volatile boolean actuallyPaused = false;

    public SnakeRunner(Snake snake, Board board) {
    this.snake = snake;
    this.board = board;
  }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                synchronized (pauseLock) {
                    while (paused) {
                        actuallyPaused = true;
                        pauseLock.wait();
                    }
                    actuallyPaused = false;
                }

                maybeTurn();
                var res = board.step(snake);

                if (res == Board.MoveResult.HIT_OBSTACLE) {
                    randomTurn();
                } else if (res == Board.MoveResult.ATE_TURBO) {
                    turboTicks = 100;
                }

                int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
                if (turboTicks > 0) turboTicks--;
                Thread.sleep(sleep);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isActuallyPaused() {
        return actuallyPaused;
    }

    public void pause() {
        synchronized (pauseLock) {
            paused = true;
        }
    }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }
    private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
