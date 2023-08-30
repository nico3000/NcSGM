package dev.nicotopia.ncsgm.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import dev.nicotopia.ncsgm.model.Backup;
import dev.nicotopia.ncsgm.model.Configuration;
import dev.nicotopia.ncsgm.model.FolderBackupManager;
import dev.nicotopia.ncsgm.model.FolderWatcher;

public class MainFrame extends JFrame {
    private final FolderBackupManager folderBackupManager;
    private final FolderWatcher folderWatcher;
    private JList<Backup> backupList;
    private JButton createBtn;
    private JButton deleteBtn;
    private JButton restoreBtn;
    private JButton renameBtn;
    private BackupDetailPanel detailPnl;

    public MainFrame(Configuration config, Image iconImage) {
        super("NcSGM | " + config.name());
        this.folderBackupManager = new FolderBackupManager(config, 10);
        this.folderWatcher = new FolderWatcher(config.pathToWatch());

        this.setIconImage(iconImage);
        this.buildFrame();
        this.buildComponentLogic();
        this.pack();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                MainFrame.this.folderWatcher.interrupt(true);
            }
        });
        this.folderWatcher.start(() -> SwingUtilities.invokeLater(this::createBackup));

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setVisible(true);
    }

    private void buildFrame() {
        this.backupList = new JList<>();
        this.backupList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.createBtn = new JButton("Create backup");
        this.deleteBtn = new JButton("Delete");
        this.restoreBtn = new JButton("Restore");
        this.renameBtn = new JButton("Rename");

        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(this.createBtn);
        btnPanel.add(this.deleteBtn);
        btnPanel.add(this.restoreBtn);
        btnPanel.add(this.renameBtn);

        this.detailPnl = new BackupDetailPanel();

        var leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(new JScrollPane(this.backupList), BorderLayout.CENTER);
        leftPanel.add(btnPanel, BorderLayout.SOUTH);

        var rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.add(detailPnl);

        this.getContentPane().setLayout(new BoxLayout(this.getContentPane(), BoxLayout.X_AXIS));
        this.add(leftPanel);
        this.add(rightPanel);
    }

    private void buildComponentLogic() {
        Runnable restoreBackup = () -> {
            var selectedIndices = backupList.getSelectedIndices();
            if (selectedIndices.length == 1 && JOptionPane.showConfirmDialog(this,
                    "Do you want to restore the following backup?\n"
                            + folderBackupManager.getElementAt(selectedIndices[0]),
                    "Confirm restore", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                synchronized (this.folderBackupManager.getConfiguration().pathToWatch()) {
                    try {
                        folderBackupManager.restoreBackup(selectedIndices[0]);
                    } catch (IOException ex) {
                        MainFrame.this.showError("Backup restoration failed", ex.getMessage());
                    }
                    MainFrame.this.folderWatcher.updateLastModified();
                }
            }
        };

        this.createBtn.addActionListener(e -> this.createBackup());

        this.deleteBtn.addActionListener(e -> {
            int selectedIndices[] = this.backupList.getSelectedIndices();
            if (selectedIndices.length != 0) {
                String list = Arrays.stream(selectedIndices).mapToObj(this.folderBackupManager::getElementAt)
                        .map(Backup::getName).collect(Collectors.joining("\n"));
                if (JOptionPane.showConfirmDialog(this,
                        String.format("Do you really want to delete following backups?\n%s", list), "Confirm delete",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    for (int i = 0; i < selectedIndices.length; ++i) {
                        this.folderBackupManager.deleteBackup(selectedIndices[selectedIndices.length - 1 - i]);
                    }
                }
            }
        });

        this.restoreBtn.addActionListener(e -> restoreBackup.run());

        this.renameBtn.addActionListener(e -> {
            var selectedIndices = this.backupList.getSelectedIndices();
            if (selectedIndices.length == 1) {
                Backup backup = this.folderBackupManager.getElementAt(selectedIndices[0]);
                String newName = JOptionPane.showInputDialog(this, "New name", backup.getName());
                if (newName != null && !newName.isEmpty() && !newName.equals(backup.getName())) {
                    try {
                        this.folderBackupManager.renameBackup(selectedIndices[0], newName, () -> {
                            return JOptionPane.showConfirmDialog(this,
                                    "A backup with that name already exists.\nDo you want to overwrite it?",
                                    "Confirm overwrite", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                        });
                    } catch (FolderBackupManager.RenameFailedException ex) {
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        this.backupList.setModel(this.folderBackupManager);
        this.backupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    restoreBackup.run();
                }
            }
        });
        this.backupList.addListSelectionListener(e -> {
            int selectedIndices[] = backupList.getSelectedIndices();
            this.deleteBtn.setEnabled(selectedIndices.length != 0);
            this.restoreBtn.setEnabled(selectedIndices.length == 1);
            this.renameBtn.setEnabled(selectedIndices.length == 1);
            this.updateDetailPanel();
        });
        this.backupList.clearSelection();
        this.renameBtn.setEnabled(false);
        this.restoreBtn.setEnabled(false);
        this.deleteBtn.setEnabled(false);

        this.folderBackupManager.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                MainFrame.this.updateDetailPanel();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                MainFrame.this.updateDetailPanel();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
            }
        });
    }

    public void createBackup() {
        try {
            int idx = this.folderBackupManager.createBackup();
            if (idx != -1) {
                this.backupList.setSelectedIndex(idx);
            }
        } catch (IOException ex) {
            MainFrame.this.showError("Backup creation failed", ex.getMessage());
        }
    }

    private void updateDetailPanel() {
        var selectedIndices = this.backupList.getSelectedIndices();
        this.detailPnl.setBackup(
                selectedIndices.length == 1 ? this.folderBackupManager.getElementAt(selectedIndices[0]) : null);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
