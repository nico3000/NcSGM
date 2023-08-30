package dev.nicotopia.ncsgm;

import java.io.IOException;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.IntelliJTheme;

import dev.nicotopia.ncsgm.model.Configuration;
import dev.nicotopia.ncsgm.view.ConfigFrame;

public class App {
    public static void main(String[] args) {
        System.out.println(App.class.getPackage().getImplementationVersion());
        SwingUtilities.invokeLater(App::new);
    }

    private App() {
        IntelliJTheme.setup(App.class.getResourceAsStream("/palenight-theme.json"));
        try {
            new ConfigFrame(Configuration.loadFromJsonResource("/presets.json"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}