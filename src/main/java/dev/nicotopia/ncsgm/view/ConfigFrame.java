package dev.nicotopia.ncsgm.view;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import dev.nicotopia.ncsgm.App;
import dev.nicotopia.ncsgm.model.Configuration;

public class ConfigFrame extends JFrame {
    private String currentConfigName;
    private JTextField pathToWatchTextField;
    private JTextField backupFolderTextField;

    public ConfigFrame(Map<String, Configuration> presets) {
        super("NcSGM | Configuration");
        try {
            this.setIconImage(ImageIO.read(App.class.getResourceAsStream("/nicotopia_transparent.png")));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        var lastConfig = this.getLastConfig();

        this.currentConfigName = lastConfig == null ? "Custom" : lastConfig.name();
        this.pathToWatchTextField = new JTextField(lastConfig == null ? "" : lastConfig.pathToWatch().toString());
        this.pathToWatchTextField.setPreferredSize(new Dimension(320,
                this.pathToWatchTextField.getPreferredSize().height));
        var pathToWatchBtn = new JButton("...");

        this.backupFolderTextField = new JTextField(lastConfig == null ? "" : lastConfig.backupFolder().toString());
        this.backupFolderTextField.setPreferredSize(new Dimension(320,
                this.backupFolderTextField.getPreferredSize().height));
        var backupFolderBtn = new JButton("...");

        var okBtn = new JButton("Start");
        var cancelBtn = new JButton("Exit");

        var menuBar = new JMenuBar();
        this.setJMenuBar(menuBar);

        var pathToWatchPanel = new JPanel(new FlowLayout());
        pathToWatchPanel.setBorder(BorderFactory.createTitledBorder("Path to watch"));
        pathToWatchPanel.add(this.pathToWatchTextField);
        pathToWatchPanel.add(pathToWatchBtn);

        var backupFolderPanel = new JPanel(new FlowLayout());
        backupFolderPanel.setBorder(BorderFactory.createTitledBorder("Backup folder"));
        backupFolderPanel.add(this.backupFolderTextField);
        backupFolderPanel.add(backupFolderBtn);

        var btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        this.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.Y_AXIS));
        this.add(pathToWatchPanel);
        this.add(backupFolderPanel);
        this.add(btnPanel);

        this.pack();
        this.setResizable(false);

        pathToWatchBtn.addActionListener(a -> {
            var jfc = new JFileChooser(pathToWatchTextField.getText());
            jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathToWatchTextField.setText(jfc.getSelectedFile().toString());
            }
        });
        
        backupFolderBtn.addActionListener(a -> {
            var jfc = new JFileChooser(backupFolderTextField.getText());
            jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                backupFolderTextField.setText(jfc.getSelectedFile().toString());
            }
        });

        okBtn.addActionListener(e -> {
            Configuration config;
            try {
                config = new Configuration(this.currentConfigName,
                        Path.of(this.pathToWatchTextField.getText()), Path.of(this.backupFolderTextField.getText()));
            } catch (InvalidPathException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (this.checkConfig(config)) {
                this.dispose();
                this.updateLastConfig(config);
                new MainFrame(config, this.getIconImage());
            }
        });

        cancelBtn.addActionListener(e -> {
            this.dispose();
        });

        var presetsMenu = new JMenu("Presets");
        menuBar.add(presetsMenu);
        for (var preset : presets.values()) {
            JMenuItem presetMenuItem = new JMenuItem(preset.name());
            presetMenuItem.addActionListener(a -> {
                this.currentConfigName = preset.name();
                this.pathToWatchTextField.setText(preset.pathToWatch().toString());
                this.backupFolderTextField.setText(preset.backupFolder().toString());
            });
            presetsMenu.add(presetMenuItem);
        }

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setVisible(true);
    }

    private boolean checkConfig(Configuration config) {
        File backupFolder = config.backupFolder().toFile();
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Creation of backup folder failed.\n" + backupFolder.getPath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        } else if (!backupFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Backup path is no folder.\n" + backupFolder.getPath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private Configuration getLastConfig() {
        String name = Preferences.userNodeForPackage(App.class).get("name", null);
        String pathToWatch = Preferences.userNodeForPackage(App.class).get("pathToWatch", null);
        String backupFolder = Preferences.userNodeForPackage(App.class).get("backupFolder", null);
        if (name != null && pathToWatch != null && backupFolder != null) {
            try {
                return new Configuration(name, Path.of(pathToWatch), Path.of(backupFolder));
            } catch (InvalidPathException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private void updateLastConfig(Configuration config) {
        Preferences.userNodeForPackage(App.class).put("name", config.name());
        Preferences.userNodeForPackage(App.class).put("pathToWatch", config.pathToWatch().toString());
        Preferences.userNodeForPackage(App.class).put("backupFolder", config.backupFolder().toString());
    }
}