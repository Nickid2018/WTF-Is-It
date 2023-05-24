package io.github.nickid2018;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class FrameTest extends JFrame {

    private BufferedImage background;
    private BufferedImage foreground;
    private BufferedImage mask;

    public FrameTest() throws Exception {
        background = ImageIO.read(new File("C:\\Users\\Nickid2018\\Desktop\\test.png"));
        foreground = ImageIO.read(new File("C:\\Users\\Nickid2018\\Desktop\\test2.png"));
        mask = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics g = mask.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, mask.getWidth(), mask.getHeight());
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }
}
