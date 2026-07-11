package com.jake219.quickwiki;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class ItemInfoPanel extends PluginPanel
{
    private final JLabel nameLabel = new JLabel();
    private final JLabel iconLabel = new JLabel();
    private final JLabel descriptionHeaderLabel = new JLabel("DESCRIPTION");
    private final JTextArea descriptionArea = new JTextArea();
    private final JLabel readMoreLabel = new JLabel("Read more");
    private final JLabel backToTopLabel = new JLabel("Back to top \u2191");

    private JPanel infoTable;
    private JPanel descriptionPanel;

    private String lastFullDescription = "";
    private boolean showFullDescription = false;
    private boolean readMoreHovering = false;

    private static final Color GOLD = new Color(224, 168, 58);
    private static final Color GOLD_HOVER = new Color(240, 195, 110);
    private static final Color BLUE = new Color(90, 184, 224);
    private static final Color GREEN = new Color(90, 214, 130);
    private static final Color RED = new Color(214, 100, 90);
    private static final Color NEUTRAL = new Color(160, 160, 160);
    private static final Color NEUTRAL_HOVER = new Color(215, 215, 215);

    public ItemInfoPanel()
    {
        setLayout(new BorderLayout());

        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(25f));

        descriptionHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        descriptionHeaderLabel.setForeground(new Color(150, 150, 150));
        descriptionHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        descriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(255, 255, 255, 40)),
                BorderFactory.createEmptyBorder(0, 8, 0, 0)
        ));

        readMoreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        readMoreLabel.setForeground(GOLD);
        readMoreLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        readMoreLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                showFullDescription = !showFullDescription;
                refreshDescriptionText();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                readMoreHovering = true;
                updateReadMoreLabel();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                readMoreHovering = false;
                updateReadMoreLabel();
            }
        });

        backToTopLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        backToTopLabel.setForeground(new Color(150, 150, 150));
        backToTopLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backToTopLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                scrollToTop();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                backToTopLabel.setForeground(NEUTRAL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                backToTopLabel.setForeground(new Color(150, 150, 150));
            }
        });

        iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel iconNamePanel = new JPanel();
        iconNamePanel.setLayout(new BoxLayout(iconNamePanel, BoxLayout.Y_AXIS));
        iconNamePanel.add(iconLabel);
        iconNamePanel.add(Box.createVerticalStrut(8));
        iconNamePanel.add(nameLabel);
        iconNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconNamePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        infoTable = createCard();
        infoTable.setLayout(new GridBagLayout());
        infoTable.setVisible(false);

        JPanel actionsRow = new JPanel(new BorderLayout());
        actionsRow.setOpaque(false);
        actionsRow.add(readMoreLabel, BorderLayout.WEST);
        actionsRow.add(backToTopLabel, BorderLayout.EAST);
        actionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        descriptionPanel = createCard();
        descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.Y_AXIS));
        descriptionPanel.add(descriptionHeaderLabel);
        descriptionPanel.add(Box.createVerticalStrut(6));
        descriptionPanel.add(descriptionArea);
        descriptionPanel.add(Box.createVerticalStrut(8));
        descriptionPanel.add(actionsRow);
        descriptionPanel.setVisible(false);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        header.add(iconNamePanel);
        header.add(Box.createVerticalStrut(10));
        header.add(infoTable);
        header.add(Box.createVerticalStrut(10));
        header.add(descriptionPanel);

        add(header, BorderLayout.NORTH);
    }

    private JPanel createCard()
    {
        JPanel card = new JPanel();
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(255, 255, 255, 45), 14),
                BorderFactory.createEmptyBorder(11, 13, 11, 13)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private static class RoundedBorder extends javax.swing.border.AbstractBorder
    {
        private final Color color;
        private final int radius;

        RoundedBorder(Color color, int radius)
        {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Faint outer line for a soft, low-contrast edge.
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, color.getAlpha() - 25)));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);

            // Slightly brighter inner line, inset by 1px, for a subtle sense of depth.
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x + 1, y + 1, width - 3, height - 3, radius - 2, radius - 2);

            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c)
        {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }

    private JScrollPane findScrollPane()
    {
        Container parent = getParent();
        while (parent != null && !(parent instanceof JScrollPane))
        {
            parent = parent.getParent();
        }
        return (parent instanceof JScrollPane) ? (JScrollPane) parent : null;
    }

    private void scrollToTop()
    {
        SwingUtilities.invokeLater(() ->
                SwingUtilities.invokeLater(() ->
                {
                    JScrollPane sp = findScrollPane();
                    if (sp != null)
                    {
                        sp.getViewport().setViewPosition(new Point(0, 0));
                    }
                    else
                    {
                        scrollRectToVisible(new Rectangle(0, 0, 1, 1));
                    }
                })
        );
    }

    private void showLoadingImage()
    {
        final int boxSize = 60;
        BufferedImage placeholder = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        g.setColor(new Color(255, 255, 255, 120));
        g.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        g.drawString("...", 14, 38);

        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(placeholder));
    }

    private String formatPrice(int value)
    {
        return String.format("%,d", value);
    }

    /**
     * Draws a small side-view coin stack (flattened discs stacked vertically, like a
     * short roll of coins) used for money-related rows: GE price, alch values, item value.
     */
    private Icon createCoinIcon(Color color)
    {
        final int width = 14;
        final int height = 14;
        Color rim = color.darker();
        Color highlight = new Color(
                Math.min(255, color.getRed() + 45),
                Math.min(255, color.getGreen() + 40),
                Math.min(255, color.getBlue() + 10));

        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int discW = width;
                int discH = 6;

                // Two flattened "edge" discs stacked beneath, showing the side of the stack.
                for (int i = 2; i >= 1; i--)
                {
                    int cy = y + 3 + i * 3;
                    g2.setColor(rim);
                    g2.fillOval(x, cy, discW, discH);
                }

                // Top disc: the visible coin face, with a highlight arc for shine.
                g2.setColor(rim);
                g2.fillOval(x, y + 2, discW, discH + 2);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 2, discW - 2, discH);
                g2.setColor(highlight);
                g2.fillOval(x + 3, y + 3, discW / 3, discH / 2);

                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return width;
            }

            @Override
            public int getIconHeight()
            {
                return height;
            }
        };
    }

    /**
     * Draws a small calendar icon (rounded rectangle with a header bar and two hanger
     * tabs) used for the Released row.
     */
    private Icon createCalendarIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(x, y + 1, size, size - 2, 2, 2);
                g2.setColor(new Color(30, 30, 30));
                g2.fillRect(x + 1, y + 3, size - 2, 2);
                g2.fillRect(x + 2, y, 2, 2);
                g2.fillRect(x + size - 4, y, 2, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small balance-scale icon (two circles on a horizontal bar) used for
     * the Weight row.
     */
    private Icon createScaleIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + 1, y + 2, x + size - 1, y + 2);
                g2.drawLine(x + size / 2, y + 2, x + size / 2, y + size - 3);
                g2.drawOval(x, y + 3, 4, 4);
                g2.drawOval(x + size - 4, y + 3, 4, 4);
                g2.fillRect(x + size / 2 - 3, y + size - 3, 6, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small checkmark used for "Yes" boolean rows (tradeable, equipable, etc).
     */
    private Icon createCheckIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 2, y + 6, x + 5, y + 9);
                g2.drawLine(x + 5, y + 9, x + 10, y + 3);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small X used for "No" boolean rows.
     */
    private Icon createCrossIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 2, y + 2, x + 10, y + 10);
                g2.drawLine(x + 10, y + 2, x + 2, y + 10);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Draws a small three-line "list" icon used for rows that aren't a single yes/no
     * or number, like the Options row.
     */
    private Icon createListIcon(Color color)
    {
        final int size = 12;
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRect(x + 1, y + 2, size - 2, 2);
                g2.fillRect(x + 1, y + 5, size - 2, 2);
                g2.fillRect(x + 1, y + 8, size - 2, 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return size;
            }

            @Override
            public int getIconHeight()
            {
                return size;
            }
        };
    }

    /**
     * Picks the right icon shape for a boolean-style value: checkmark for Yes,
     * cross for No, list icon for anything else.
     */
    private Icon yesNoIcon(String value, Color color)
    {
        if ("Yes".equalsIgnoreCase(value))
        {
            return createCheckIcon(color);
        }
        if ("No".equalsIgnoreCase(value))
        {
            return createCrossIcon(color);
        }
        return createListIcon(color);
    }

    /**
     * Returns green for "Yes", a muted red for "No", or neutral gray for anything else -
     * used to color-code boolean-style stats (Tradeable, Equipable, Members, etc).
     */
    private Color yesNoColor(String value)
    {
        if ("Yes".equalsIgnoreCase(value))
        {
            return GREEN;
        }
        if ("No".equalsIgnoreCase(value))
        {
            return RED;
        }
        return NEUTRAL;
    }

    /**
     * Adds one label/value row to the info table using GridBagLayout, with a small
     * category icon and the value text tinted to match. Pass null for icon/accentColor
     * to fall back to the default (uncolored, iconless) look.
     */
    private void addTableRow(int row, String label, String value, Icon icon, Color accentColor)
    {
        Font labelFont = new Font("Segoe UI", Font.BOLD, 12);
        Font valueFont = new Font("Segoe UI", Font.PLAIN, 12);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);
        if (icon != null)
        {
            labelComp.setIcon(icon);
            labelComp.setIconTextGap(6);
        }

        JLabel valueComp = new JLabel("<html><div style='width: 100%;'>" + value + "</div></html>");
        valueComp.setFont(valueFont);
        if (accentColor != null)
        {
            valueComp.setForeground(accentColor);
        }

        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.gridx = 0;
        gcLabel.gridy = row;
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.insets = new Insets(2, 0, 2, 8);

        GridBagConstraints gcValue = new GridBagConstraints();
        gcValue.gridx = 1;
        gcValue.gridy = row;
        gcValue.anchor = GridBagConstraints.NORTHWEST;
        gcValue.weightx = 1.0;
        gcValue.fill = GridBagConstraints.HORIZONTAL;
        gcValue.insets = new Insets(2, 0, 2, 0);

        infoTable.add(labelComp, gcLabel);
        infoTable.add(valueComp, gcValue);
    }

    private void addTableRow(int row, String label, String value)
    {
        addTableRow(row, label, value, null, null);
    }

    public void showItem(String name, BufferedImage image, int price, int highAlch, int lowAlch)
    {
        nameLabel.setText("<html><div style='text-align: left; width: 140px;'>" + name + "</div></html>");
        if (image != null)
        {
            setImage(image);
        }
        else
        {
            showLoadingImage();
        }

        infoTable.removeAll();
        int row = 0;
        if (price > 0)
        {
            addTableRow(row++, "GE Price:", formatPrice(price) + " gp", createCoinIcon(GOLD), GOLD);
            if (highAlch > 0)
            {
                addTableRow(row++, "High alch:", formatPrice(highAlch) + " gp", createCoinIcon(GOLD), GOLD);
            }
            if (lowAlch > 0)
            {
                addTableRow(row++, "Low alch:", formatPrice(lowAlch) + " gp", createCoinIcon(GOLD), GOLD);
            }
        }
        infoTable.setVisible(row > 0);

        lastFullDescription = "Loading description...";
        descriptionArea.setText(lastFullDescription);
        descriptionPanel.setVisible(true);

        revalidate();
        repaint();
        scrollToTop();
    }

    public void setInfobox(String released, String members, String questItem)
    {
        addTableRow(currentRowCount(), "Released:", released, createCalendarIcon(BLUE), BLUE);
        addTableRow(currentRowCount(), "Members:", members, yesNoIcon(members, yesNoColor(members)), yesNoColor(members));
        addTableRow(currentRowCount(), "Quest item:", questItem, yesNoIcon(questItem, yesNoColor(questItem)), yesNoColor(questItem));
        infoTable.setVisible(true);
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setProperties(String tradeable, String equipable, String stackable, String noteable, String options)
    {
        addTableRow(currentRowCount(), "Tradeable:", tradeable, yesNoIcon(tradeable, yesNoColor(tradeable)), yesNoColor(tradeable));
        addTableRow(currentRowCount(), "Equipable:", equipable, yesNoIcon(equipable, yesNoColor(equipable)), yesNoColor(equipable));
        addTableRow(currentRowCount(), "Stackable:", stackable, yesNoIcon(stackable, yesNoColor(stackable)), yesNoColor(stackable));
        addTableRow(currentRowCount(), "Noteable:", noteable, yesNoIcon(noteable, yesNoColor(noteable)), yesNoColor(noteable));
        addTableRow(currentRowCount(), "Options:", options, createListIcon(NEUTRAL), NEUTRAL);
        infoTable.setVisible(true);
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setValues(String value, String weight)
    {
        String formattedValue = formatValueString(value) + " gp";
        addTableRow(currentRowCount(), "Value:", formattedValue, createCoinIcon(GOLD), GOLD);
        addTableRow(currentRowCount(), "Weight:", weight + " kg", createScaleIcon(BLUE), BLUE);
        infoTable.setVisible(true);
        revalidate();
        repaint();
        scrollToTop();
    }

    private String formatValueString(String raw)
    {
        try
        {
            int parsed = Integer.parseInt(raw.trim().replaceAll("[^0-9-]", ""));
            return formatPrice(parsed);
        }
        catch (NumberFormatException e)
        {
            return raw;
        }
    }

    private int currentRowCount()
    {
        // Each row adds 2 components (label + value), so divide by 2 for the next free row index.
        return infoTable.getComponentCount() / 2;
    }

    public void clearInfobox()
    {
        infoTable.removeAll();
        infoTable.setVisible(false);
        revalidate();
        repaint();
    }

    public void showNonItem(String name)
    {
        nameLabel.setText("<html><div style='text-align: left; width: 140px;'>" + name + "</div></html>");
        showLoadingImage();
        infoTable.removeAll();
        infoTable.setVisible(false);
        lastFullDescription = "Loading description...";
        descriptionArea.setText(lastFullDescription);
        descriptionPanel.setVisible(true);

        revalidate();
        repaint();
        scrollToTop();
    }

    public void setDescription(String description)
    {
        lastFullDescription = description;
        refreshDescriptionText();
        revalidate();
        repaint();
        scrollToTop();
    }

    private void refreshDescriptionText()
    {
        boolean needsTruncation = lastFullDescription.length() > 300;

        if (showFullDescription || !needsTruncation)
        {
            descriptionArea.setText(lastFullDescription);
        }
        else
        {
            int cut = lastFullDescription.lastIndexOf('.', 300);
            String shortText = (cut > 0 ? lastFullDescription.substring(0, cut + 1) : lastFullDescription.substring(0, 300)) + "..";
            descriptionArea.setText(shortText);
        }

        readMoreLabel.setVisible(needsTruncation);
        updateReadMoreLabel();

        descriptionArea.setCaretPosition(0);
        revalidate();
        repaint();
    }

    /**
     * Renders the read-more/read-less label with the correct text for the current
     * expand state, plus a brighter color while the mouse is hovering it.
     */
    private void updateReadMoreLabel()
    {
        String text = showFullDescription ? "Read less" : "Read more";
        readMoreLabel.setForeground(readMoreHovering ? GOLD_HOVER : GOLD);
        readMoreLabel.setText(text);
    }

    public void setImage(BufferedImage image)
    {
        if (image == null)
        {
            iconLabel.setIcon(null);
            return;
        }

        final int boxSize = 60;
        int origWidth = image.getWidth();
        int origHeight = image.getHeight();

        double scale = Math.min((double) boxSize / origWidth, (double) boxSize / origHeight);
        int scaledWidth = Math.max(1, (int) (origWidth * scale));
        int scaledHeight = Math.max(1, (int) (origHeight * scale));

        Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);

        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D frameShape = new RoundRectangle2D.Float(0.5f, 0.5f, boxSize - 1, boxSize - 1, 10, 10);

        // Subtle card-style background behind the item so smaller/odd-shaped sprites
        // still sit inside a clean, consistent frame.
        g.setColor(new Color(255, 255, 255, 18));
        g.fill(frameShape);

        // Clip to the rounded frame so the image itself picks up rounded corners.
        g.setClip(frameShape);
        int x = (boxSize - scaledWidth) / 2;
        int y = (boxSize - scaledHeight) / 2;
        g.drawImage(scaledImage, x, y, null);
        g.setClip(null);

        // Soft border on top to finish the frame.
        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(frameShape);

        g.dispose();

        iconLabel.setIcon(new ImageIcon(canvas));
        scrollToTop();
    }
}