package com.mauro0805.forceblend.client.ui;

import com.mauro0805.forceblend.client.ForceBlendClient;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ForceBlendProgressWindow implements AutoCloseable {
    private static final long UPDATE_INTERVAL_NANOS = 100_000_000L;
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());
    private static final NumberFormat SPEED_FORMAT = NumberFormat.getNumberInstance(Locale.getDefault());
    private static final ForceBlendProgressWindow HIDDEN = new ForceBlendProgressWindow();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong nextUpdateNanos = new AtomicLong();
    private final JDialog dialog;
    private final JLabel phaseLabel;
    private final JLabel chunksLabel;
    private final JLabel etaLabel;
    private final JLabel regionLabel;
    private final JLabel speedLabel;
    private final JProgressBar progressBar;

    private ForceBlendProgressWindow() {
        dialog = null;
        phaseLabel = null;
        chunksLabel = null;
        etaLabel = null;
        regionLabel = null;
        speedLabel = null;
        progressBar = null;
        closed.set(true);
    }

    private ForceBlendProgressWindow(String worldName) {
        dialog = new JDialog(null, "ForceBlend — " + worldName, Dialog.ModalityType.MODELESS);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        phaseLabel = new JLabel("Preparing terrain...");
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.BOLD, 15.0F));
        chunksLabel = new JLabel("Waiting for processing data");
        etaLabel = new JLabel("ETA: calculating...");
        regionLabel = new JLabel("Region: waiting...");
        speedLabel = new JLabel("Speed: calculating...");
        progressBar = new JProgressBar(0, 1_000);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);

        JPanel details = new JPanel();
        details.setLayout(new javax.swing.BoxLayout(details, javax.swing.BoxLayout.Y_AXIS));
        details.add(chunksLabel);
        details.add(etaLabel);
        details.add(regionLabel);
        details.add(speedLabel);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        content.add(phaseLabel, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.CENTER);
        content.add(details, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setMinimumSize(new Dimension(460, 190));
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    public static ForceBlendProgressWindow hidden() {
        return HIDDEN;
    }

    static ForceBlendProgressWindow open(String worldName) {
        if (SwingUtilities.isEventDispatchThread()) {
            return new ForceBlendProgressWindow(worldName);
        }
        java.util.concurrent.FutureTask<ForceBlendProgressWindow> task =
            new java.util.concurrent.FutureTask<>(() -> new ForceBlendProgressWindow(worldName));
        try {
            SwingUtilities.invokeAndWait(task);
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening the progress window", exception);
        } catch (java.lang.reflect.InvocationTargetException | java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Could not open the progress window", exception.getCause());
        }
    }

    public static void showNotification(String title, String message) {
        if (!SystemTray.isSupported()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                TrayIcon icon = new TrayIcon(notificationImage(), "ForceBlend");
                icon.setImageAutoSize(true);
                tray.add(icon);
                icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
                Timer removeTimer = new Timer(5_000, ignored -> tray.remove(icon));
                removeTimer.setRepeats(false);
                removeTimer.start();
            } catch (java.awt.AWTException | RuntimeException exception) {
                ForceBlendClient.LOGGER.debug("Desktop completion notification is unavailable", exception);
            }
        });
    }

    private static BufferedImage notificationImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(36, 125, 70));
            graphics.fillRoundRect(0, 0, 32, 32, 8, 8);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.drawString("F", 10, 23);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static String formatInteger(int value) {
        synchronized (INTEGER_FORMAT) {
            return INTEGER_FORMAT.format(value);
        }
    }

    private static String formatLong(long value) {
        synchronized (INTEGER_FORMAT) {
            return INTEGER_FORMAT.format(value);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1_024L) {
            return bytes + " B";
        }
        double kibibytes = bytes / 1_024.0;
        if (kibibytes < 1_024.0) {
            return String.format(Locale.getDefault(), "%.1f KiB", kibibytes);
        }
        double mebibytes = kibibytes / 1_024.0;
        if (mebibytes < 1_024.0) {
            return String.format(Locale.getDefault(), "%.1f MiB", mebibytes);
        }
        return String.format(Locale.getDefault(), "%.2f GiB", mebibytes / 1_024.0);
    }

    private static String formatSpeed(double value) {
        synchronized (SPEED_FORMAT) {
            SPEED_FORMAT.setMaximumFractionDigits(value < 100.0 ? 1 : 0);
            return SPEED_FORMAT.format(value);
        }
    }

    public boolean isVisible() {
        return dialog != null && !closed.get();
    }

    public void update(ProcessingProgressView progress) {
        if (!isVisible()) {
            return;
        }
        long now = System.nanoTime();
        long next = nextUpdateNanos.get();
        if (now < next || !nextUpdateNanos.compareAndSet(next, now + UPDATE_INTERVAL_NANOS)) {
            return;
        }
        SwingUtilities.invokeLater(() -> apply(progress));
    }

    public void updateBackup(
        long totalFiles,
        long totalBytes,
        long completedFiles,
        long completedBytes,
        String currentEntry
    ) {
        if (!isVisible()) {
            return;
        }
        long now = System.nanoTime();
        long next = nextUpdateNanos.get();
        if (now < next || !nextUpdateNanos.compareAndSet(next, now + UPDATE_INTERVAL_NANOS)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!isVisible()) {
                return;
            }
            double fraction = totalBytes > 0L
                ? (double) completedBytes / totalBytes
                : totalFiles > 0L ? (double) completedFiles / totalFiles : 0.0;
            progressBar.setIndeterminate(totalBytes == 0L && totalFiles == 0L);
            progressBar.setValue((int) Math.round(fraction * progressBar.getMaximum()));
            progressBar.setString((int) Math.round(fraction * 100.0) + "%");
            phaseLabel.setText("Creating a world backup...");
            chunksLabel.setText(
                formatLong(completedFiles) + " / " + formatLong(totalFiles) + " files backed up"
            );
            etaLabel.setText(formatBytes(completedBytes) + " / " + formatBytes(totalBytes));
            regionLabel.setText("File: " + currentEntry);
            speedLabel.setText("The world will only be changed after this backup finishes.");
        });
    }

    public void complete(String summary) {
        if (isVisible()) {
            SwingUtilities.invokeLater(() -> {
                phaseLabel.setText("Terrain preparation complete");
                chunksLabel.setText(summary);
                etaLabel.setText("Entering world...");
                progressBar.setIndeterminate(false);
                progressBar.setValue(progressBar.getMaximum());
                progressBar.setString("100%");
                Timer closeTimer = new Timer(650, ignored -> close());
                closeTimer.setRepeats(false);
                closeTimer.start();
            });
        }
        showNotification("ForceBlend", summary);
    }

    @Override
    public void close() {
        if (dialog == null || !closed.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(dialog::dispose);
    }

    private void apply(ProcessingProgressView progress) {
        if (!isVisible()) {
            return;
        }
        double fraction = progress.fraction();
        int percentage = (int) Math.round(fraction * 100.0);
        progressBar.setIndeterminate(progress.totalChunks() <= 0 && progress.totalRegions() <= 0);
        progressBar.setValue((int) Math.round(fraction * progressBar.getMaximum()));
        progressBar.setString(percentage + "%");
        phaseLabel.setText(progress.phase());
        if (progress.totalChunks() > 0) {
            chunksLabel.setText(
                formatInteger(progress.completedChunks()) + " / " + formatInteger(progress.totalChunks()) + " chunks"
            );
        } else {
            chunksLabel.setText(
                formatInteger(progress.completedRegions()) + " / " + formatInteger(progress.totalRegions())
                    + " regions"
            );
        }
        etaLabel.setText(
            progress.completedChunks() == 0 && progress.completedRegions() == 0
                ? "ETA: calculating..."
                : "ETA: " + ForceBlendDesktopUi.formatDuration(progress.estimatedRemaining())
        );
        regionLabel.setText("Region: " + progress.currentRegion());
        speedLabel.setText(
            progress.completedChunks() == 0 && progress.completedRegions() == 0
                ? "Speed: calculating..."
                : "Speed: " + formatSpeed(progress.chunksPerSecond()) + " chunks/s"
        );
    }
}
