package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.UUID;

/**
 * Test fixture covering column-default resolution. Most columns derive their {@code DEFAULT} from the
 * field's initialised value (booleans, numbers, strings, enums); {@code motto} exercises string-quote
 * escaping, {@code title} carries an explicit {@link Column#defaultValue()} that must win over the
 * initialiser, and {@code bio} (no initialiser) plus {@code id} (database-assigned) produce no default.
 *
 * <p>Used by {@code EntityParserDefaultValueTest} and {@code DialectDefaultValueTest}.</p>
 */
@Entity
@Table(name = "players")
public class Player {

    public enum Rank { BRONZE, SILVER, GOLD }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "notify", nullable = false)
    private boolean notify = false;

    @Column(name = "premium", nullable = false)
    private boolean premium = true;

    @Column(name = "coins", nullable = false)
    private int coins = 100;

    @Column(name = "balance", nullable = false)
    private double balance = 4.5;

    @Column(name = "nickname")
    private String nickname = "unknown";

    @Column(name = "motto")
    private String motto = "it's me";

    @Column(name = "rank", nullable = false)
    private Rank rank = Rank.BRONZE;

    @Column(name = "title", defaultValue = "'rookie'")
    private String title = "ignored";

    @Column(name = "bio")
    private String bio;

    public UUID getId() {
        return id;
    }

    public boolean isNotify() {
        return notify;
    }

    public boolean isPremium() {
        return premium;
    }

    public int getCoins() {
        return coins;
    }

    public double getBalance() {
        return balance;
    }

    public String getNickname() {
        return nickname;
    }

    public String getMotto() {
        return motto;
    }

    public Rank getRank() {
        return rank;
    }

    public String getTitle() {
        return title;
    }

    public String getBio() {
        return bio;
    }
}
