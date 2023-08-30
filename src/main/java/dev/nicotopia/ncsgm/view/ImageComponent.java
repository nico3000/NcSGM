package dev.nicotopia.ncsgm.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JComponent;

public class ImageComponent extends JComponent {
    private Image image;

    public void setImage(Image image) {
        this.image = image;
        if (this.image != null) {
            this.setPreferredSize(new Dimension(image.getWidth(this), this.image.getHeight(this)));
        }
        this.repaint();
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
        g.setColor(Color.GRAY);
        int x = 0;
        int y = 0;
        int stripeWidth = 16;
        while (x < 2 * this.getWidth() || y < 2 * this.getWidth()) {
            int[] xPoints = { x, x + stripeWidth, 0, 0 };
            int[] yPoints = { 0, 0, y + stripeWidth, y };
            g.fillPolygon(xPoints, yPoints, 4);
            x += 2 * stripeWidth;
            y += 2 * stripeWidth;
        }
        if (this.image != null) {
            int imgWidth = this.image.getWidth(this);
            int imgHeight = this.image.getHeight(this);
            int compWidth = this.getWidth();
            int compHeight = this.getHeight();
            if (compWidth < compHeight * imgWidth / imgHeight) {
                int drawHeight = compWidth * imgHeight / imgWidth;
                g.drawImage(image, 0, (compHeight - drawHeight) / 2, compWidth, drawHeight, this);
            } else {
                int drawWidth = compHeight * imgWidth / imgHeight;
                g.drawImage(image, (compWidth - drawWidth) / 2, 0, drawWidth, compHeight, this);
            }
        }
        super.paint(g);
    }
}
