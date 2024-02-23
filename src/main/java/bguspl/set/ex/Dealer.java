package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private final int ZERO = 0;
    private final int ONE = 1;
    private Thread dealerThread;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    ConcurrentLinkedQueue<Integer> playersIdWithSet;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playersIdWithSet = new ConcurrentLinkedQueue<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        dealerThread = Thread.currentThread();

        table.writeLock.lock();
        createPlayersThread();

        while (!shouldFinish()) {
            shuffleTheDeck();
            placeCardsOnTable();
            table.writeLock.unlock();
            updateTimerDisplay(true);
            timerLoop();
            updateTimerDisplay(false);
            table.writeLock.lock();
            removeAllCardsFromTable();
        }
        table.writeLock.unlock();

        announceWinners();
        terminate();
        joinPlayers();

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            table.writeLock.lock();
            removeCardsFromTable();
            placeCardsOnTable();
            table.writeLock.unlock();
            updateTimerDisplay(false);
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

    private void createPlayersThread() {
        for (int i = 0; i < players.length; i++) {
            new Thread(players[i], "player " + i).start();
        }
    }

    private void terminateAllPlayers() {
        for (int i = players.length - ONE; i >= ZERO; i--) {
            players[i].terminate();
        }
    }

    private void joinPlayers() {
        for (int i = 0; i < players.length; i++) {
            players[i].joinPlayerThread();
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
        return terminate || env.util.findSets(allCards, 1).size() == ZERO;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private synchronized void removeCardsFromTable() {
        while (!playersIdWithSet.isEmpty()) {
            Integer playerId = playersIdWithSet.remove();
            Integer[] playerSetCards = table.playerToCards(playerId);
            if (checkPlayersGuess(playerSetCards)) {
                players[playerId].point();
                updateTableSetWasClaimed(playerSetCards);
                removeCollisionsInListForGivenSet(playerSetCards);
                updateTimerDisplay(true);
            } else {
                players[playerId].penalty();
            }
        }
        // TODO implement - Done?
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.writeLock.lock();
        while (needAndCanDrawAnotherCard()) {
            table.placeCard(deck.remove(ZERO));
        }
        table.writeLock.unlock();
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
    private synchronized void sleepUntilWokenOrTimeout() {
        try {
            this.wait(env.config.tableDelayMillis);
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
        env.ui.setCountdown(timeLeft, timeLeft < env.config.endGamePauseMillies);

        // TODO implement - DONE
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        deck.addAll(table.giveBackCardsToDealer());
        table.writeLock.lock();
        table.removeAllCards();
        table.writeLock.unlock();
        shuffleTheDeck();
    }

    /**
     * Check if a guess of a player forms a set
     */
    private boolean checkPlayersGuess(Integer[] playerGuess) {
        return env.util.testSet(convertIntegerToInt(playerGuess));
    }

    private void updateTableSetWasClaimed(Integer[] cards) {
        table.writeLock.lock();
        for (Integer card :
                cards) {
            table.removeCard(table.getSlotFromCard(card));
        }
        table.writeLock.unlock();
    }

    private int[] convertIntegerToInt(Integer[] arr) {
        int[] convertedArr = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            convertedArr[i] = arr[i];
        }
        return convertedArr;
    }

    /**
     * In case of a set check whether other players claimed a set with one of the cards
     */
    private void removeCollisionsInListForGivenSet(Integer[] validatedSet) {
        playersIdWithSet.removeIf(playerIdToCheck -> checkCollision(validatedSet, playerIdToCheck));
    }

    private boolean checkCollision(Integer[] validatedGuess, Integer playerIdToCheck) {
        Set<Integer> setOfGuess = new HashSet<>(Arrays.asList(validatedGuess));

        for (Integer element : table.playerToCards(playerIdToCheck)) {
            if (setOfGuess.contains(element)) {
                players[playerIdToCheck].notify();
                return true; // Collision found
            }
        }
        return false; // No collision
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = findPlayersMaxScore();
        ArrayList<Integer> winnersId = new ArrayList<>();
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == maxScore) {
                winnersId.add(players[i].id);
            }
        }
        env.ui.announceWinner(convertArrayListToArr(winnersId));
    }

    private int[] convertArrayListToArr(ArrayList<Integer> winnersId) {
        int[] arr = new int[winnersId.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = winnersId.get(i);
        }
        return arr;
    }


    private int findPlayersMaxScore() {
        int maxScore = players[0].score();
        for (int i = 1; i < players.length; i++) {
            int score = players[i].score();
            if (score > maxScore) {
                maxScore = score;
            }
        }
        return maxScore;
    }

    public synchronized void checkMySet(int id) {
        playersIdWithSet.add(id);
        notifyAll();
    }
}
