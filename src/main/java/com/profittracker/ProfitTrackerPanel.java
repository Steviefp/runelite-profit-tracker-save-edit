package com.profittracker;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProfitTrackerPanel extends PluginPanel {
    private final JButton resetButton = new JButton("Reset");
    private final ProfitTrackerPlugin plugin;



    public ProfitTrackerPanel(ProfitTrackerPlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Example: add the button to the top
        resetButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                onResetButtonPressed();
            }
        });

        add(resetButton, BorderLayout.NORTH);
    }

    private void onResetButtonPressed()
    {
        System.out.println("Reset button clicked!");
        plugin.resetProgress();
    }
}
