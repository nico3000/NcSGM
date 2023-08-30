package dev.nicotopia.ncsgm.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import dev.nicotopia.ncsgm.model.Backup;

public class BackupDetailPanel extends JPanel implements PropertyChangeListener {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private Backup backup;
    private final ImageComponent imgComp = new ImageComponent();
    private final JPanel propertyPnl = new JPanel(new GridBagLayout());

    public BackupDetailPanel() {
        this.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        this.imgComp.setPreferredSize(new Dimension(640, 360));
        this.imgComp.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        this.addProperty("Name");
        this.addProperty("Timestamp");
        this.addProperty("File size");

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.add(this.imgComp);
        var temp = new JPanel(new BorderLayout());
        temp.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        temp.add(this.propertyPnl, BorderLayout.NORTH);
        this.add(temp);
    }

    private void addProperty(String name) {
        var gbc = new GridBagConstraints();

        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridx = 0;
        gbc.gridy = this.propertyPnl.getComponentCount() / 2;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        this.propertyPnl.add(new JLabel(name + ":"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        this.propertyPnl.add(new JLabel(""), gbc);
    }

    private void setProperty(int idx, String value) {
        if (this.propertyPnl.getComponent(2 * idx + 1) instanceof JLabel label) {
            label.setText(value == null ? "" : value);
        }
    }

    public void setBackup(Backup backup) {
        if (this.backup != backup) {
            if (this.backup != null) {
                this.backup.removePropertyChangeListener(this);
            }
            this.backup = backup;
            this.updateView();
            if (this.backup != null) {
                this.backup.addPropertyChangeListener(this);
            }
        }
    }

    private static String formatByteWidth(long byteWidth) {
        String units[] = { "B", "KiB", "MiB", "GiB" };
        int unitIdx = (int) Math.floor(Math.log((double) byteWidth) / Math.log(1024.0));
        double v = (long) byteWidth / Math.pow(1024, unitIdx);
        return String.format("%.1f %s", v, units[unitIdx]);
    }

    private void updateView() {
        this.imgComp.setImage(this.backup == null ? null : this.backup.getImage());
        this.setProperty(0, this.backup == null ? "" : this.backup.getName());
        this.setProperty(1,
                this.backup == null ? "" : FORMATTER.format(Instant.ofEpochMilli(this.backup.getLastModified())));
        this.setProperty(2, this.backup == null ? "" : formatByteWidth(this.backup.getFileByteWidth()));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        this.updateView();
    }
}
