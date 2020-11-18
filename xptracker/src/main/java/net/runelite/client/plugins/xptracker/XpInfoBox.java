/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2020, Anthony <https://github.com/while-loop>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.xptracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;
import net.runelite.client.ui.components.PopupMenuOwner;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

class XpInfoBox extends JPanel implements PopupMenuOwner
{
	static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");

	static
	{
		TWO_DECIMAL_FORMAT.setRoundingMode(RoundingMode.DOWN);
	}

	// Templates
	private static final String HTML_TOOL_TIP_TEMPLATE =
		"<html>%s %s done<br/>"
			+ "%s %s/hr<br/>"
			+ "%s %s</html>";
	private static final String HTML_LABEL_TEMPLATE =
		"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

	private static final String REMOVE_STATE = "Remove from canvas";
	private static final String ADD_STATE = "Add to canvas";

	// Instance members
	private final JComponent panel;
	private final XpTrackerConfig config;

	@Getter(AccessLevel.PACKAGE)
	private final Skill skill;

	/* The tracker's wrapping container */
	private final JPanel container = new JPanel();

	/* Contains the skill icon */
	private final JPanel skillWrapper = new JPanel();

	/* Contains all the skill information (exp gained, per hour, etc) */
	private final JPanel statsPanel = new JPanel();

	private final JPanel progressWrapper = new JPanel();
	private final ProgressBar progressBar = new ProgressBar();
	private final JPopupMenu popupMenu = new JPopupMenu();

	private final JLabel topLeftStat = new JLabel();
	private final JLabel bottomLeftStat = new JLabel();
	private final JLabel topRightStat = new JLabel();
	private final JLabel bottomRightStat = new JLabel();
	private final JMenuItem pauseSkill = new JMenuItem("Pause");
	private final JMenuItem canvasItem = new JMenuItem(ADD_STATE);

	private boolean paused = false;

	private Style style = Style.FULL;

	private enum Style
	{
		FULL,
		SIMPLE
	}

	XpInfoBox(XpTrackerPlugin xpTrackerPlugin, XpTrackerConfig xpTrackerConfig, Client client, JComponent panel, Skill skill, SkillIconManager iconManager)
	{
		this.config = xpTrackerConfig;
		this.panel = panel;
		this.skill = skill;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 0, 0));

		container.setLayout(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Create open xp tracker menu
		final JMenuItem openXpTracker = new JMenuItem("Open Wise Old Man");
		openXpTracker.addActionListener(e -> LinkBrowser.browse(XpPanel.buildXpTrackerUrl(
			client.getLocalPlayer(), skill, client.getWorldType().contains(WorldType.LEAGUE))));

		// Create reset menu
		final JMenuItem reset = new JMenuItem("Reset");
		reset.addActionListener(e -> xpTrackerPlugin.resetSkillState(skill));

		// Create reset others menu
		final JMenuItem resetOthers = new JMenuItem("Reset others");
		resetOthers.addActionListener(e -> xpTrackerPlugin.resetOtherSkillState(skill));

		// Create reset others menu
		pauseSkill.addActionListener(e -> xpTrackerPlugin.pauseSkill(skill, !paused));

		// Create popup menu
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(openXpTracker);
		popupMenu.add(reset);
		popupMenu.add(resetOthers);
		popupMenu.add(pauseSkill);
		popupMenu.add(canvasItem);
		popupMenu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent)
			{
				canvasItem.setText(xpTrackerPlugin.hasOverlay(skill) ? REMOVE_STATE : ADD_STATE);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent popupMenuEvent)
			{
			}
		});

		skillWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		skillWrapper.setLayout(new BorderLayout());
		skillWrapper.setBorder(new EmptyBorder(0, 5, 0, 0));

		canvasItem.addActionListener(e ->
		{
			if (canvasItem.getText().equals(REMOVE_STATE))
			{
				xpTrackerPlugin.removeOverlay(skill);
			}
			else
			{
				xpTrackerPlugin.addOverlay(skill);
			}
		});

		JLabel skillIcon = new JLabel(new ImageIcon(iconManager.getSkillImage(skill)));
		skillIcon.setHorizontalAlignment(SwingConstants.CENTER);
		skillIcon.setVerticalAlignment(SwingConstants.CENTER);
		skillIcon.setPreferredSize(new Dimension(30, 30));

		skillWrapper.add(skillIcon, BorderLayout.NORTH);

		statsPanel.setLayout(new DynamicGridLayout(2, 2));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(6, 5, 0, 2));

		topLeftStat.setFont(FontManager.getRunescapeSmallFont());
		bottomLeftStat.setFont(FontManager.getRunescapeSmallFont());
		topRightStat.setFont(FontManager.getRunescapeSmallFont());
		bottomRightStat.setFont(FontManager.getRunescapeSmallFont());

		statsPanel.add(topLeftStat);     // top left
		statsPanel.add(topRightStat);    // top right
		statsPanel.add(bottomLeftStat);  // bottom left
		statsPanel.add(bottomRightStat); // bottom right

		progressWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressWrapper.setLayout(new BorderLayout());

		progressBar.setMaximumValue(100);
		progressBar.setBackground(new Color(61, 56, 49));
		progressBar.setForeground(SkillColor.find(skill).getColor());
		progressBar.setDimmedText("Paused");

		progressWrapper.add(progressBar, BorderLayout.NORTH);

		// progressBar's tooltip text consumes mouse events from parent, and so requires setComponentPopupMenu
		progressBar.setComponentPopupMenu(popupMenu);

		MouseListener mouseListener = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					toggleStyle();
				}
			}
		};

		progressBar.addMouseListener(mouseListener);

		add(container, BorderLayout.NORTH);
	}

	private void setStyle(Style style)
	{
		container.removeAll();

		if (style == Style.SIMPLE)
		{
			progressWrapper.setBorder(new EmptyBorder(7, 7, 7, 7));
			container.add(skillWrapper, BorderLayout.WEST);
			container.add(progressWrapper, BorderLayout.CENTER);
		}
		else
		{
			progressWrapper.setBorder(new EmptyBorder(4, 7, 7, 7));
			container.add(skillWrapper, BorderLayout.WEST);
			container.add(statsPanel, BorderLayout.CENTER);
			container.add(progressWrapper, BorderLayout.SOUTH);
		}

		panel.revalidate();
		this.style = style;
	}

	void reset()
	{
		canvasItem.setText(ADD_STATE);
		panel.remove(this);
		panel.revalidate();
	}

	void update(boolean updated, boolean paused, XpSnapshotSingle xpSnapshotSingle)
	{
		SwingUtilities.invokeLater(() -> rebuildAsync(updated, paused, xpSnapshotSingle));
	}

	private void rebuildAsync(boolean updated, boolean skillPaused, XpSnapshotSingle xpSnapshotSingle)
	{
		if (updated)
		{
			if (getParent() != panel)
			{
				panel.add(this);
				setStyle(style);
			}

			if (config.prioritizeRecentXpSkills())
			{
				panel.setComponentZOrder(this, 0);
			}

			paused = skillPaused;

			// Update progress bar
			progressBar.setValue((int) xpSnapshotSingle.getSkillProgressToGoal());
			progressBar.setCenterLabel(config.progressBarLabel().getValueFunc().apply(xpSnapshotSingle));
			progressBar.setLeftLabel("Lvl. " + xpSnapshotSingle.getStartLevel());
			progressBar.setRightLabel(xpSnapshotSingle.getEndGoalXp() == Experience.MAX_SKILL_XP
				? "200M"
				: "Lvl. " + xpSnapshotSingle.getEndLevel());

			// Add intermediate level positions to progressBar
			if (config.showIntermediateLevels() && xpSnapshotSingle.getEndLevel() - xpSnapshotSingle.getStartLevel() > 1)
			{
				final List<Integer> positions = new ArrayList<>();

				for (int level = xpSnapshotSingle.getStartLevel() + 1; level < xpSnapshotSingle.getEndLevel(); level++)
				{
					double relativeStartExperience = Experience.getXpForLevel(level) - xpSnapshotSingle.getStartGoalXp();
					double relativeEndExperience = xpSnapshotSingle.getEndGoalXp() - xpSnapshotSingle.getStartGoalXp();
					positions.add((int) (relativeStartExperience / relativeEndExperience * 100));
				}

				progressBar.setPositions(positions);
			}
			else
			{
				progressBar.setPositions(Collections.emptyList());
			}

			XpProgressBarLabel tooltipLabel = config.progressBarTooltipLabel();

			progressBar.setToolTipText(String.format(
				HTML_TOOL_TIP_TEMPLATE,
				xpSnapshotSingle.getActionsInSession(),
				xpSnapshotSingle.getActionType().getLabel(),
				xpSnapshotSingle.getActionsPerHour(),
				xpSnapshotSingle.getActionType().getLabel(),
				tooltipLabel.getValueFunc().apply(xpSnapshotSingle),
				tooltipLabel == XpProgressBarLabel.PERCENTAGE ? "of goal" : "till goal lvl"));

			progressBar.setDimmed(skillPaused);

			progressBar.repaint();
		}
		else if (!paused && skillPaused)
		{
			// React to the skill state now being paused
			progressBar.setDimmed(true);
			progressBar.repaint();
			paused = true;
			pauseSkill.setText("Unpause");
		}
		else if (paused && !skillPaused)
		{
			// React to the skill being unpaused (without update)
			progressBar.setDimmed(false);
			progressBar.repaint();
			paused = false;
			pauseSkill.setText("Pause");
		}

		// Update information labels
		// Update exp per hour separately, every time (not only when there's an update)
		topLeftStat.setText(htmlLabel(config.xpPanelLabel1(), xpSnapshotSingle));
		topRightStat.setText(htmlLabel(config.xpPanelLabel2(), xpSnapshotSingle));
		bottomLeftStat.setText(htmlLabel(config.xpPanelLabel3(), xpSnapshotSingle));
		bottomRightStat.setText(htmlLabel(config.xpPanelLabel4(), xpSnapshotSingle));
	}

	static String htmlLabel(XpPanelLabel panelLabel, XpSnapshotSingle xpSnapshotSingle)
	{
		String key = panelLabel.getActionKey(xpSnapshotSingle) + ": ";
		String value = panelLabel.getValueFunc().apply(xpSnapshotSingle);
		return htmlLabel(key, value);
	}

	private void toggleStyle()
	{
		if (style == Style.FULL)
		{
			setStyle(Style.SIMPLE);
		}
		else
		{
			setStyle(Style.FULL);
		}
	}

	@Override
	public JPopupMenu getPopupMenu()
	{
		return popupMenu;
	}

	static String htmlLabel(String key, int value)
	{
		String valueStr = QuantityFormatter.quantityToRSDecimalStack(value, true);
		return htmlLabel(key, valueStr);
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}

	public void toggleCanvasItemText()
	{
		if (canvasItem.getText().equals(ADD_STATE))
		{
			canvasItem.setText(REMOVE_STATE);
		}
		else
		{
			canvasItem.setText(ADD_STATE);
		}
	}
}
