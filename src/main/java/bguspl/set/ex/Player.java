package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private final Dealer dealer;
    protected final BlockingQueue<Integer> inputQueue;
    protected long freezeTimeLeft;
    private final int ZERO = 0;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.inputQueue = new ArrayBlockingQueue<>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try {
                if (handleKeyPress()) {
                    handleFreeze();
                    inputQueue.clear();
                }
            } catch (InterruptedException ignored) {
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random r = new Random();
                keyPressed(r.nextInt(env.config.tableSize));
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if (!human) aiThread.interrupt();
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

        try {
            inputQueue.put(slot);
        } catch (InterruptedException ignored) {
        }


        // TODO implement - DONE - to add locks
    }

    private boolean handleKeyPress() throws InterruptedException {
        int slot = inputQueue.take();

        if (table.playerAlreadyPlacedThisToken(id, slot)) {
            table.removeToken(id, slot);
            return false;
        } else {
            table.placeToken(id, slot);
            if (table.playerTokensIsFeatureSize(id)) {
                dealer.checkMySet(id, table.playerToCards(id));
                return true;
            }

        }
        return false;
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        setFreeze(env.config.pointFreezeMillis);

        // TODO implement - DONE
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        setFreeze(env.config.penaltyFreezeMillis);
        // TODO implement - DONE
    }

    public int score() {
        return score;
    }

    private void handleFreeze() throws InterruptedException {
        while (freezeTimeLeft > ZERO) {
            env.ui.setFreeze(id, freezeTimeLeft);
            long sleepTime = Math.min(freezeTimeLeft, env.config.pointFreezeMillis);
            setFreeze(freezeTimeLeft - sleepTime);
            Thread.sleep(sleepTime);
        }
        env.ui.setFreeze(id, ZERO);
    }

    public void setFreeze(long time) {
        freezeTimeLeft = time;
    }
}
