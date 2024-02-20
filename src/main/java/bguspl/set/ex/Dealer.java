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
     * The list of sets that player want to check
     */
    protected List<Integer> setsToBeChecked;
    private final int ZERO = 0;
    private Thread dealerThread;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        dealerThread = Thread.currentThread();
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
        terminate = true;
        terminateAllPlayers();
        dealerThread.interrupt();


        // TODO implement - DONE
    }

    private void terminateAllPlayers() {
        for (int i = 0; i < players.length; i++) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        List<Integer> allCards = deck;
        allCards.addAll(table.giveBackCardsToDealer());
        return terminate || env.util.findSets(allCards, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        while (!setsToBeChecked.isEmpty()) {

        }
//        check while queue is empty
//        handle the set queue
//        take out the first in line and check if it is a set
//        if it is no a set a penalty
//        sometimes should inform the table.
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        while (needAndCanDrawAnotherCard()) {
            table.placeCard(deck.remove(ZERO));
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
        try {
            dealerThread.wait(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        long timeLeft = reshuffleTime - System.currentTimeMillis() > ZERO ? reshuffleTime - System.currentTimeMillis() : ZERO;
        env.ui.setCountdown(timeLeft, timeLeft<env.config.endGamePauseMillies);

        // TODO implement - DONE
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        deck.addAll(table.giveBackCardsToDealer());
        table.removeAllCards();
        shuffleTheDeck();
    }

    /**
     * Get a guess of a player
     */
    private void checkPlayersGuess(Integer playerIdGuess) {
        Integer[] playerSetCards = table.playerToCards();
        if (env.util.testSet(convertIntToInteger(playerSetCards))) {
            players[playerIdGuess].point();
            removeCollisionsForGivenSet(playerSetCards);
        } else {
            players[playerIdGuess].penalty();
        }
    }

    private int[] convertIntToInteger(Integer[] arr) {
        int[] convertedArr = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            convertedArr[i] = arr[i];
        }
        return convertedArr;
    }

    /**
     * In case of a set check whether other players claimed a set with one of the cards
     */
    private void removeCollisionsForGivenSet(Integer[] validatedSet) {
        // Remove the current pair from the list
        setsToBeChecked.removeIf(playerIdToCheck -> checkCollision(validatedSet, playerIdToCheck));
    }

    private boolean checkCollision(Integer[] validatedGuess, Integer playerIdToCheck) {
        Set<Integer> setOfGuess = new HashSet<>(Arrays.asList(validatedGuess));

        for (Integer element : table.playerToCards(playerIdToCheck)) {
            if (setOfGuess.contains(element)) {
                return true; // Collision found
            }
        }
        return false; // No collision
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

    private Pair<Integer, Integer> findPlayersMaxScoreAndQuantity() {
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
        return new Pair<>(maxScore, count);
    }

    public void checkMySet(int id) {
        setsToBeChecked.add(id);
    }
}
