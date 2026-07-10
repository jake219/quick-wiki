package com.example;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ItemInfoPanel extends PluginPanel
{
    private final JButton backToTopButton = new JButton("Back to top");
    private final JLabel nameLabel = new JLabel();
    private final JLabel iconLabel = new JLabel();
    private final JTextArea descriptionArea = new JTextArea();
    private final JCheckBox fullDescriptionCheckbox = new JCheckBox("Show full description", false);

    private JPanel infoTable;
    private JPanel descriptionPanel;

    private String lastFullDescription = "";

    public ItemInfoPanel()
    {
        setLayout(new BorderLayout());

        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(25f));
        descriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        fullDescriptionCheckbox.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);

        fullDescriptionCheckbox.addActionListener(e -> refreshDescriptionText());
        fullDescriptionCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);

        backToTopButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        backToTopButton.setFocusable(false);
        backToTopButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        backToTopButton.addActionListener(e -> scrollToTop());

        iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel iconNamePanel = new JPanel();
        iconNamePanel.setLayout(new BoxLayout(iconNamePanel, BoxLayout.Y_AXIS));
        iconNamePanel.add(iconLabel);
        iconNamePanel.add(nameLabel);
        iconNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconNamePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        infoTable = createCard();
        infoTable.setLayout(new GridBagLayout());
        infoTable.setVisible(false);

        descriptionPanel = createCard();
        descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.Y_AXIS));
        descriptionPanel.add(fullDescriptionCheckbox);
        descriptionPanel.add(Box.createVerticalStrut(6));
        descriptionPanel.add(descriptionArea);
        descriptionPanel.add(Box.createVerticalStrut(10));
        descriptionPanel.add(backToTopButton);
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
                new RoundedBorder(new Color(255, 255, 255, 30), 10),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
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
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
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
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect(0, 0, boxSize, boxSize, 6, 6);
        g.setColor(new Color(255, 255, 255, 120));
        g.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        g.drawString("...", 14, 38);
        g.dispose();

        iconLabel.setIcon(new ImageIcon(placeholder));
    }

    private String formatPrice(int value)
    {
        return String.format("%,d", value);
    }

    /**
     * Adds one label/value row to the info table using GridBagLayout,
     * so the value column stays neatly aligned across every row.
     */
    private void addTableRow(int row, String label, String value)
    {
        Font labelFont = new Font("Segoe UI", Font.BOLD, 12);
        Font valueFont = new Font("Segoe UI", Font.PLAIN, 12);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);

        JLabel valueComp = new JLabel("<html><div style='width: 100%;'>" + value + "</div></html>");
        valueComp.setFont(valueFont);

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
            addTableRow(row++, "GE Price:", formatPrice(price) + " gp");
            if (highAlch > 0)
            {
                addTableRow(row++, "High alch:", formatPrice(highAlch) + " gp");
            }
            if (lowAlch > 0)
            {
                addTableRow(row++, "Low alch:", formatPrice(lowAlch) + " gp");
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
        addTableRow(currentRowCount(), "Released:", released);
        addTableRow(currentRowCount(), "Members:", members);
        addTableRow(currentRowCount(), "Quest item:", questItem);
        infoTable.setVisible(true);
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setProperties(String tradeable, String equipable, String stackable, String noteable, String options)
    {
        addTableRow(currentRowCount(), "Tradeable:", tradeable);
        addTableRow(currentRowCount(), "Equipable:", equipable);
        addTableRow(currentRowCount(), "Stackable:", stackable);
        addTableRow(currentRowCount(), "Noteable:", noteable);
        addTableRow(currentRowCount(), "Options:", options);
        infoTable.setVisible(true);
        revalidate();
        repaint();
        scrollToTop();
    }

    public void setValues(String value, String weight)
    {
        String formattedValue = formatValueString(value) + " gp";
        addTableRow(currentRowCount(), "Value:", formattedValue);
        addTableRow(currentRowCount(), "Weight:", weight + " kg");
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
        if (fullDescriptionCheckbox.isSelected() || lastFullDescription.length() <= 300)
        {
            descriptionArea.setText(lastFullDescription);
        }
        else
        {
            int cut = lastFullDescription.lastIndexOf('.', 300);
            String shortText = (cut > 0 ? lastFullDescription.substring(0, cut + 1) : lastFullDescription.substring(0, 300)) + "..";
            descriptionArea.setText(shortText);
        }
        descriptionArea.setCaretPosition(0);
        revalidate();
        repaint();
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
        int x = (boxSize - scaledWidth) / 2;
        int y = (boxSize - scaledHeight) / 2;
        g.drawImage(scaledImage, x, y, null);
        g.dispose();

        iconLabel.setIcon(new ImageIcon(canvas));
        scrollToTop();
    }
}