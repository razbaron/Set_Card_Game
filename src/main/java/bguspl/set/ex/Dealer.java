package bguspl.set.ex;

import bguspl.set.Env;
import com.sun.tools.javac.util.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        while(needAndCanDrawAnotherCard()){
            table.placeCard(deck.remove(0));
        }
    }

    private boolean needAndCanDrawAnotherCard() {
        return table.hasEmptySlot() && (!deck.isEmpty());
    }

    private void shuffleTheDeck() {
        Random rand = new Random();
        for (int i = 0; i < deck.size(); i++) {
            int j = rand.nextInt(deck.size());
            int temp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, temp);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        deck.addAll(table.giveBackCardsToDealer());
        shuffleTheDeck();
    }

    /**
    * Get a guess of a player
    * */
    private void checkPlayersGuess(Pair<Integer, int[]> playersGuess) {
        if (env.util.testSet(playersGuess.snd)) {
            players[(playersGuess.fst)].point();
        } else {
            players[playersGuess.fst].penalty();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        Pair<Integer, Integer> maxAndQuantity = findPlayersMaxScoreAndQuantity();
        int[] winnersId = new int[maxAndQuantity.snd];
        int availableIndex = 0;

        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == maxAndQuantity.fst) {
                winnersId[availableIndex] = i;
                availableIndex++;
            }
        }
        env.ui.announceWinner(winnersId);
    }

    private Pair<Integer, Integer> findPlayersMaxScoreAndQuantity(){
        int maxScore = players[0].score();
        int count = 1;

        for (int i = 1; i < players.length; i++) {
            int score = players[i].score();
            if (score > maxScore) {
                maxScore = score;
                count = 1;
            } else if (score == maxScore) {
                count++;
            }
        }
        return new Pair<>(maxScore,count);
    }

    public void checkMySet(int id, Integer[] playerToSlots) {
    }
}
