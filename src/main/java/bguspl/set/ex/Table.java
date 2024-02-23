package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */


public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected ConcurrentLinkedQueue<Integer>[] tokensToPlayers;
    protected List<Integer>[] playersTokensToSlot;

    private ReentrantReadWriteLock rwLock;
    public Lock readLock;
    public Lock writeLock;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokensToPlayers = new ConcurrentLinkedQueue[env.config.tableSize];
        for (int i = 0; i < tokensToPlayers.length; i++) {
            tokensToPlayers[i] = new ConcurrentLinkedQueue<>();
        }
        this.playersTokensToSlot = new ArrayList[env.config.players];
        for (int i = 0; i < playersTokensToSlot.length; i++) {
            playersTokensToSlot[i] = new ArrayList<>();
        }
        rwLock = new ReentrantReadWriteLock(true);
        writeLock = rwLock.writeLock();
        Condition condition = writeLock.newCondition();
        readLock = rwLock.readLock();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);

        // TODO implement - DONE
    }

    public void placeCard(int card) {
        if (hasEmptySlot()) {
            placeCard(card, findEmptySlot());
        }
    }

    public boolean hasEmptySlot() {
        for (Integer slot : slotToCard) {
            if (slot == null) return true;
        }
        return false;
    }

    private int findEmptySlot() {
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] == null) return i;
        }
        throw new NoSuchElementException();
    }


    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;
        env.ui.removeCard(slot);
        env.ui.removeTokens(slot);
        tokensCleaner(slot);

        // TODO implement - DONE

    }

    public void removeAllCards() {
        for (int i = 0; i < env.config.tableSize; i++) {
            removeCard(i);
        }
    }

//    clean all tokens on slot
    private void tokensCleaner(int slot) {
        tokensToPlayers[slot] = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < playersTokensToSlot.length; i++) {
            for (int j = 0; j < playersTokensToSlot[i].size(); j++) {
                if (j == slot) {
                    playersTokensToSlot[i].remove(j);
                    --j;
                }
            }
        }
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        if (slotToCard[slot] == null) return;

        tokensToPlayers[slot].add(player);
        playersTokensToSlot[player].add(slot);
        env.ui.placeToken(player, slot);

        // TODO implement - DONE
    }

    public boolean playerAlreadyPlacedThisToken(int player, int slot) {
        return playersTokensToSlot[player].contains(slot);
    }

    public boolean playerTokensIsFeatureSize(int player) {
        return playersTokensToSlot[player].size() == env.config.featureSize;
    }

    public Integer[] playerToCards(int player) {
        Integer[] cards = new Integer[playersTokensToSlot[player].size()];
        for (int i = 0; i < playersTokensToSlot[player].size(); i++) {
            cards[i] = slotToCard[playersTokensToSlot[player].get(i)];
        }
        return cards;
    }

    public Integer getSlotFromCard(Integer card) {
        if (card > cardToSlot.length) throw new NoSuchElementException();
        return cardToSlot[card];
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (tokensToPlayers[slot].contains(player)) {
            tokensToPlayers[slot].remove(player);
            playersTokensToSlot[player].remove(playersTokensToSlot[player].indexOf(slot));
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;

        // TODO implement - DONE
    }

    public List<Integer> giveBackCardsToDealer() {
        List<Integer> allCardsOnTable = new ArrayList<>();
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] != null) {
                allCardsOnTable.add(slotToCard[i]);
            }
        }
        return allCardsOnTable;
    }


}
