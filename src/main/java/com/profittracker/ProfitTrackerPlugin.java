package com.profittracker;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import net.runelite.api.events.*;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
        name = "Profit Tracker"
)


public class ProfitTrackerPlugin extends Plugin
{
    ProfitTrackerGoldDrops goldDropsObject;
    ProfitTrackerInventoryValue inventoryValueObject;
    @Inject
    private ConfigManager configManager;
    private static final BufferedImage ICON = ImageUtil.loadImageResource(ProfitTrackerPlugin.class, "gp.jpg");

    private static final String CONFIG_GROUP = "profittracker";
    private static final String TOTAL_PROFIT_KEY = "totalProfit";
    private static final String SECONDS_ELAPSED_KEY  = "secondsElapsed";
    private static final String START_TICK_MILLIS  = "startTickMillis";
    @Getter @Setter
    private boolean isRunning = false;

    // the profit will be calculated against this value
    private long prevInventoryValue;
    private long totalProfit;
    @Getter @Setter
    private long startTickMillis;
    @Getter @Setter
    private long secondsElapsed;

    private boolean skipTickForProfitCalculation;
    private boolean inventoryValueChanged;
    private boolean inProfitTrackSession;
    private boolean resetFlag = false;

    @Inject
    private Client client;

    @Inject
    private ProfitTrackerConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ProfitTrackerOverlay overlay;

    @Inject
    private ClientToolbar clientToolbar;

    private ProfitTrackerPanel panel;

    private NavigationButton navButton;

    @Subscribe
    public void onGameStateChanged(net.runelite.api.events.GameStateChanged event) throws Exception {
        GameState state = event.getGameState();

        if (state == GameState.LOGGED_IN)
        {
            // Player just logged in
            System.out.println("Player logged in!");
            this.startUp();
        }
        else if (state == GameState.LOGIN_SCREEN)
        {
            // Player just logged out
            System.out.println("Player logged out!");
            this.shutDown();
        }
    }
    @Override
    protected void startUp()
    {
        // Add the inventory overlay
        overlayManager.add(overlay);
        this.panelStartUp();
        isRunning = true;

        goldDropsObject = new ProfitTrackerGoldDrops(client, itemManager);

        inventoryValueObject = new ProfitTrackerInventoryValue(client, itemManager);

        initializeVariables();

        // start tracking only if plugin was re-started mid game
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            startProfitTrackingSession();
        }

    }


    private void panelStartUp(){
        panel = new ProfitTrackerPanel(this);

        navButton = NavigationButton.builder()
                .tooltip("Profit Tracker")
                .icon(ICON) // your Image here (BufferedImage)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    public void resetProgress()
    {
        this.setSecondsElapsed(0);
        overlay.setSecondsElapsed(0);
        overlay.updateProfitValue(0);
        totalProfit = 0;
        startTickMillis = 0;
        resetFlag = true;

        configManager.unsetConfiguration(CONFIG_GROUP, "secondsElapsed");
        configManager.unsetConfiguration(CONFIG_GROUP, "profitValue");
        configManager.unsetConfiguration(CONFIG_GROUP, "startTickMillis");
        this.shutDown();
        this.startUp();

    }

    private long loadProfit()
    {
        Long savedProfit = configManager.getConfiguration(CONFIG_GROUP, TOTAL_PROFIT_KEY, Long.class);
        return (savedProfit == null) ? 0 : savedProfit;
    }

//    private long loadStartTickMillis()
//    {
//        Long savedTime = configManager.getConfiguration(CONFIG_GROUP, START_TICK_MILLIS, Long.class);
//        return (savedTime == null) ? 0 : savedTime;
//    }


    private void loadTime()
    {
        long savedTime = configManager.getConfiguration(CONFIG_GROUP, SECONDS_ELAPSED_KEY, Long.class);
        if(savedTime > 0){
            overlay.setSecondsElapsed(savedTime);
            this.setSecondsElapsed(savedTime);
        }
    }

    private void saveProgress()
    {
        long savedSeconds = overlay.getSecondsElapsed();
        configManager.setConfiguration(CONFIG_GROUP, TOTAL_PROFIT_KEY, totalProfit);
        configManager.setConfiguration(CONFIG_GROUP, SECONDS_ELAPSED_KEY, savedSeconds);
        //configManager.setConfiguration(CONFIG_GROUP, START_TICK_MILLIS, startTickMillis);

    }

    private void initializeVariables()
    {
        // value here doesn't matter, will be overwritten
        prevInventoryValue = -1;

        // profit begins at 0 of course
        if(!resetFlag){
            totalProfit = loadProfit();
        }
        else{
            totalProfit = 0;
            resetFlag = false;
        }
        loadTime();
        // this will be filled with actual information in startProfitTrackingSession
        startTickMillis = System.currentTimeMillis();
        // skip profit calculation for first tick, to initialize first inventory value
        skipTickForProfitCalculation = true;

        inventoryValueChanged = false;

        inProfitTrackSession = false;

    }

    private void startProfitTrackingSession()
    {
        /*
        Start tracking profit from now on
         */

        //initializeVariables();

        // initialize timer
        //startTickMillis = System.currentTimeMillis();
        overlay.updateStartTimeMillies(startTickMillis);

        overlay.startSession();

        inProfitTrackSession = true;
    }

    @Override
    protected void shutDown()
    {
        // Remove the inventory overlay
        overlayManager.remove(overlay);
        saveProgress();
        isRunning = false;


    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        /*
        Main plugin logic here

        1. If inventory changed,
            - calculate profit (inventory value difference)
            - generate gold drop (nice animation for showing gold earn or loss)

        2. Calculate profit rate and update in overlay

        */

        long tickProfit;

        if (!inProfitTrackSession)
        {
            return;
        }

        if (inventoryValueChanged)
        {
            tickProfit = calculateTickProfit();

            // accumulate profit
            totalProfit += tickProfit;

            overlay.updateProfitValue(totalProfit);

            // generate gold drop
            if (config.goldDrops() && tickProfit != 0)
            {
                goldDropsObject.requestGoldDrop(tickProfit);
            }

            inventoryValueChanged = false;
        }

    }

    private long calculateTickProfit()
    {
        /*
        Calculate and return the profit for this tick
        if skipTickForProfitCalculation is set, meaning this tick was bank / deposit
        so return 0

         */
        long newInventoryValue;
        long newProfit;

        // calculate current inventory value
        newInventoryValue = inventoryValueObject.calculateInventoryAndEquipmentValue();

        if (!skipTickForProfitCalculation)
        {
            // calculate new profit
            newProfit = newInventoryValue - prevInventoryValue;

        }
        else
        {
            /* first time calculation / banking / equipping */
            log.info("Skipping profit calculation!");

            skipTickForProfitCalculation = false;

            // no profit this tick
            newProfit = 0;
        }

        // update prevInventoryValue for future calculations anyway!
        prevInventoryValue = newInventoryValue;

        return newProfit;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        /*
        this event tells us when inventory has changed
        and when banking/equipment event occured this tick
         */
        log.info("onItemContainerChanged container id: " + event.getContainerId());

        int containerId = event.getContainerId();

        if( containerId == InventoryID.INVENTORY.getId() ||
            containerId == InventoryID.EQUIPMENT.getId()) {
            // inventory has changed - need calculate profit in onGameTick
            inventoryValueChanged = true;

        }

        // in these events, inventory WILL be changed but we DON'T want to calculate profit!
        if(     containerId == InventoryID.BANK.getId()) {
            // this is a bank interaction.
            // Don't take this into account
            skipTickForProfitCalculation = true;

        }

    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        /* for ignoring deposit in deposit box */
        log.info(String.format("Click! ID: %d, actionParam: %d ,menuOption: %s, menuTarget: %s, widgetId: %d",
                event.getId(), event.getActionParam(), event.getMenuOption(), event.getMenuTarget(), event.getWidgetId()));

        if (event.getId() == ObjectID.BANK_DEPOSIT_BOX) {
            // we've interacted with a deposit box. Don't take this tick into account for profit calculation
            skipTickForProfitCalculation = true;
        }


    }

    @Provides
    ProfitTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ProfitTrackerConfig.class);
    }


    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        goldDropsObject.onScriptPreFired(scriptPreFired);
    }
}
