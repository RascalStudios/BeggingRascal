import org.dreambot.api.input.Keyboard;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.trade.Trade;
import org.dreambot.api.methods.trade.TradeUser;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.utilities.Timer;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.widgets.message.Message;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@ScriptManifest(author = "Rascal Scripts", name = "Begging Rascal", version = 1.2, description = "Roams populated areas and begs for coins", category = Category.MISC)
public class BeggingRascal extends AbstractScript implements ChatListener {

    private enum State {
        WALK_TO_BEGGING_AREA,
        FIND_VICTIM,
        CHECK_VICTIM_ACTIVE,
        BEG,
        WAIT_FOR_TRADE,
        ACCEPT_TRADE
    }

    private State state = State.WALK_TO_BEGGING_AREA;
    private final Area varrock_west = new Area(3180, 3433, 3192, 3445);
    private final Area Grand_Exchange = new Area(3160, 3480, 3170, 3490);
    private final Timer runtimeTimer = new Timer();
    private Timer begTimer;
    private String victimName;
    private boolean victimResponded = false;
    private Timer messageTimer = new Timer();
    private boolean messageSent = false;
    private Timer tradeOpenTimer = new Timer();
    private final Random rnd = new Random();

    private boolean tradeRequested = false;
    private String traderName = "";

    private ArrayList<String> alreadyBegged = new ArrayList<String>();

    private Area beggingArea = Grand_Exchange;
    private int beggingIndex = 0;

    int totalBegs = 0;

    private static final String [] NO_MESSAGES = {
            "No",
            "nah",
            "No sorry",
            "Nope",
            "nah sorry",
            "fuck off",
            "no way"
    };

    @Override
    public void onStart() {
        log("Begging Rascal by Jake has started.");
        Mouse.setAlwaysHop(true);
        runtimeTimer.reset();
        begTimer = new Timer();
    }

    private void setState(State newState) {
        log("Changing state to: " + newState);
        this.state = newState;
    }

    @Override
    public int onLoop() {
        // If out of area and not trading, walk back
//        if (!beggingArea.contains(Players.getLocal()) && state != State.WALK_TO_BEGGING_AREA && !Trade.isOpen()) {
//            setState(State.WALK_TO_BEGGING_AREA);
//        }

        switch (state) {
            case WALK_TO_BEGGING_AREA:


                if (beggingArea.contains(Players.getLocal())) {
                    setState(State.FIND_VICTIM);
                } else {
                    Walking.walk(beggingArea.getRandomTile());
                    Sleep.sleep(1800, 2500);
                }
                break;

            case FIND_VICTIM:
                victimResponded = false;
                Player victim = Players.all().stream()
                        .filter(p -> p != null && p.exists() && p.getLevel() > 50 && !p.equals(Players.getLocal()) && alreadyBegged.contains(p.getName()) == false)
                        .findFirst().orElse(null);

                if (victim != null) {
                    victimName = victim.getName();
                    log("Found victim: " + victimName);
                    alreadyBegged.add(victimName);
                    messageSent = false;
                    //if (victim.isOnScreen()) {
                    victim.interact("Follow");
                    Sleep.sleepUntil(() -> Players.getLocal().isMoving(), 3000);
                    //}
                    setState(State.CHECK_VICTIM_ACTIVE);
                } else {
                    Sleep.sleep(1000, 1500);
                    //switch begging area
                    log("No suitable victim found, walking to begging area again.");
                    beggingIndex++;
                    setState(State.WALK_TO_BEGGING_AREA);
                }
                break;

            case CHECK_VICTIM_ACTIVE:
                if (!messageSent) {

                    String messageName = victimName.split("_")[0];
                    totalBegs++;
                    if (totalBegs % 5 == 0) {
                        beggingArea = (beggingIndex++ % 2 == 0) ? Grand_Exchange : varrock_west;
                    }
                    Keyboard.type(randomVictimMessage(messageName), true);
                    messageSent = true;
                    messageTimer.reset();
                    setState(State.BEG);
                }
                break;

            case BEG:
                //check if the victim types anything in chat
                if (messageTimer.elapsed() > 35_000 || victimResponded) {
                    if (!victimResponded) {
                        log("Victim did not respond, finding new victim");
                        setState(State.WALK_TO_BEGGING_AREA);
                        return 300;
                    } else {
                        log("Victim responded, proceeding to beg");

                    }
                    Keyboard.type("Hey!", true);
                    Keyboard.type("Sorry to ask, any chance you can spare 100k?", true);
                    begTimer.reset();
                    setState(State.WAIT_FOR_TRADE);
                }
                return 300;

            case WAIT_FOR_TRADE:
                // 2) Handle incoming trade first
                //if beg timer is greater than 30 seconds, find a new victim
                if (begTimer.elapsed() > 90_000) {
                    log("Begging timeout, finding new victim");
                    setState(State.WALK_TO_BEGGING_AREA);
                    return 300;
                }
                if (tradeRequested || Trade.isOpen(1) || Trade.isOpen(2)) {
                    tradeRequested = false;
                    log("Trading with {}", traderName);

                    Trade.tradeWithPlayer(traderName);
                    Sleep.sleepUntil(() -> Trade.isOpen(1), 15_000);
                    Sleep.sleepUntil(() -> Trade.hasAcceptedTrade(TradeUser.THEM), 30_000);

                    if (Trade.canAccept()) {
                        Trade.acceptTrade(1);
                        log("First screen accepted");
                    } else {
                        log("First screen not ready!");
                        return 300;
                    }

                    Sleep.sleepUntil(() -> Trade.isOpen(2), 15_000);
                    Sleep.sleepUntil(Trade::canAccept, 15_000);
                    Trade.acceptTrade(2);
                    log("Second screen accepted");
                    Sleep.sleepUntil(() -> !Trade.isOpen(), 15_000);

                    // Bank everything
                    if (Bank.open()) {
                        Sleep.sleepUntil(Bank::isOpen, 15_000);
                        Bank.depositAllItems();
                        Bank.close();
                        log("All items banked");
                        //find victim again

                    }
                    //find the next victim
                    setState(State.WALK_TO_BEGGING_AREA);
                }
                break;


        }

        return 600;
    }

    @Override
    public void onPaint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(10, 10, 220, 100, 10, 10);

        g2.setColor(Color.WHITE);
        g2.drawString("Begging Rascal by Jake", 20, 30);
        g2.drawString("Status: " + state.name().replace("_", " "), 20, 50);
        g2.drawString("Time Running: " + runtimeTimer.formatTime(), 20, 70);
        g2.drawString("Victim: " + (victimName != null ? victimName : "None"), 20, 90);
    }

    public String randomVictimMessage(String victimName) {
        List<String> messages = Arrays.asList(
                "Hey " + victimName + "!",
                "Hello " + victimName + ", you there??",
                victimName,
                victimName + ", You there?"
        );
        return messages.get(rnd.nextInt(messages.size()));

    }

    @Override
    public void onTradeMessage(Message message) {
        tradeRequested = true;
        traderName  = message.getUsername();
        log("Trade request from {}", traderName);
        begTimer.reset();
        setState(State.WAIT_FOR_TRADE);
    }

    @Override
    public void onMessage(Message message) {
        String username = message.getUsername();
        if (username.equalsIgnoreCase(victimName)) {
            victimResponded = true;
            if (state == State.WAIT_FOR_TRADE) {
                for (int i = 0; i < NO_MESSAGES.length; i++) {
                    if (message.getMessage().toLowerCase().contains(NO_MESSAGES[i].toLowerCase())) {
                        log("Victim declined the trade request with message: " + message.getMessage());
                        setState(State.WALK_TO_BEGGING_AREA);
                        return;
                    }
                }

            }
        }
    }

    @Override
    public void onExit() {
        log("Begging Rascal script ended.");
    }
}