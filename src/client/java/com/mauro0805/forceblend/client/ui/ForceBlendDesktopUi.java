package com.mauro0805.forceblend.client.ui;

import com.mauro0805.forceblend.client.ForceBlendClient;
import com.mauro0805.forceblend.config.ForceBlendConfig;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Minecraft-class-free desktop UI used by every supported runtime namespace.
 *
 * <p>Minecraft 1.18-1.21 uses intermediary client classes while 26.x exposes
 * Mojang's unobfuscated classes. Keeping this boundary on the JDK desktop API
 * prevents either namespace from entering shared bytecode.</p>
 */
public final class ForceBlendDesktopUi {
    private static final String TITLE = "ForceBlend";
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());
    private static volatile boolean lookAndFeelInitialized;

    private ForceBlendDesktopUi() {
    }

    public static boolean isAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    public static WorldOpenDecision chooseWorldAction(
        String worldName,
        WorldAnalysisView analysis,
        boolean preferBackup
    ) {
        if (!isAvailable()) {
            ForceBlendClient.LOGGER.warn(
                "A desktop UI is unavailable; ForceBlend will leave world '{}' unchanged for this opening",
                worldName
            );
            return WorldOpenDecision.ENTER_UNCHANGED;
        }

        try {
            return onEventDispatchThread(() -> showChoiceDialog(worldName, analysis, preferBackup));
        } catch (RuntimeException exception) {
            ForceBlendClient.LOGGER.error(
                "ForceBlend could not display its confirmation dialog; world '{}' will be left unchanged",
                worldName,
                exception
            );
            return WorldOpenDecision.ENTER_UNCHANGED;
        }
    }

    public static PreAnalysisDecision choosePreAnalysisAction(String worldName) {
        if (!isAvailable()) {
            ForceBlendClient.LOGGER.warn(
                "A desktop UI is unavailable; ForceBlend will leave world '{}' unchanged for this opening",
                worldName
            );
            return PreAnalysisDecision.ENTER_UNCHANGED;
        }
        try {
            return onEventDispatchThread(() -> showPreAnalysisDialog(worldName));
        } catch (RuntimeException exception) {
            ForceBlendClient.LOGGER.error(
                "ForceBlend could not display its analysis prompt; world '{}' will be left unchanged",
                worldName,
                exception
            );
            return PreAnalysisDecision.ENTER_UNCHANGED;
        }
    }

    public static void showNoChanges(String worldName) {
        ForceBlendProgressWindow.showNotification(
            "ForceBlend",
            "No terrain borders need preparation in " + worldName + "."
        );
    }

    public static ForceBlendProgressWindow openProgress(String worldName, boolean visible) {
        if (!visible || !isAvailable()) {
            return ForceBlendProgressWindow.hidden();
        }
        try {
            initializeLookAndFeel();
            return ForceBlendProgressWindow.open(worldName);
        } catch (RuntimeException exception) {
            ForceBlendClient.LOGGER.warn("Could not open the ForceBlend progress window", exception);
            return ForceBlendProgressWindow.hidden();
        }
    }

    public static Optional<ForceBlendConfig> editConfiguration(ForceBlendConfig config) {
        if (!isAvailable()) {
            ForceBlendClient.LOGGER.warn("A desktop UI is unavailable; ForceBlend settings were not changed");
            return Optional.empty();
        }
        try {
            return onEventDispatchThread(() -> showConfigurationDialog(config));
        } catch (RuntimeException exception) {
            ForceBlendClient.LOGGER.error("Could not display the ForceBlend settings dialog", exception);
            return Optional.empty();
        }
    }

    public static void showFailure(String worldName, Throwable failure) {
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        ForceBlendClient.LOGGER.error("ForceBlend stopped before opening world '{}'", worldName, failure);
        if (!isAvailable()) {
            return;
        }
        SwingUtilities.invokeLater(() -> showMessage(
            "ForceBlend could not safely prepare this world.\n\n"
                + detail + "\n\nNo further processing will continue and the world will not be opened. "
                + "If the failure happened during application, an earlier region may already have completed. "
                + "Check the log and restore your backup if needed.",
            JOptionPane.ERROR_MESSAGE
        ));
    }

    public static void showSettingsFailure(Throwable failure) {
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (!isAvailable()) {
            return;
        }
        SwingUtilities.invokeLater(() -> showMessage(
            "ForceBlend could not save its settings.\n\n"
                + detail + "\n\nNo world files were changed. Check the log for details.",
            JOptionPane.ERROR_MESSAGE
        ));
    }

    public static String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        if (seconds < 1L) {
            return "less than a second";
        }
        if (seconds < 60L) {
            return "about " + seconds + (seconds == 1L ? " second" : " seconds");
        }
        long minutes = Math.max(1L, Math.round(seconds / 60.0));
        return "about " + minutes + (minutes == 1L ? " minute" : " minutes");
    }

    private static WorldOpenDecision showChoiceDialog(
        String worldName,
        WorldAnalysisView analysis,
        boolean preferBackup
    ) {
        initializeLookAndFeel();

        Object[] options = {
            "Apply and enter",
            "Back up, apply and enter",
            "Enter unchanged",
            "Cancel"
        };
        int preferredOption = preferBackup ? 1 : 0;
        int result = showOptions(
            confirmationPanel(worldName, analysis, preferBackup),
            JOptionPane.QUESTION_MESSAGE,
            options,
            preferredOption
        );
        return switch (result) {
            case 0 -> WorldOpenDecision.APPLY;
            case 1 -> WorldOpenDecision.APPLY_WITH_BACKUP;
            case 2 -> WorldOpenDecision.ENTER_UNCHANGED;
            default -> WorldOpenDecision.CANCEL;
        };
    }

    private static PreAnalysisDecision showPreAnalysisDialog(String worldName) {
        initializeLookAndFeel();
        Object[] options = {"Analyze with ForceBlend", "Enter unchanged", "Cancel"};
        JPanel message = new JPanel();
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        message.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JLabel heading = new JLabel("Automatic ForceBlend analysis is disabled.");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.add(heading);
        message.add(Box.createVerticalStrut(8));
        message.add(line("World", worldName));
        JLabel detail = new JLabel("No world file has been changed. Analyze it for this opening?");
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.add(detail);

        int result = showOptions(
            message,
            JOptionPane.QUESTION_MESSAGE,
            options,
            0
        );
        return switch (result) {
            case 0 -> PreAnalysisDecision.ANALYZE;
            case 1 -> PreAnalysisDecision.ENTER_UNCHANGED;
            default -> PreAnalysisDecision.CANCEL;
        };
    }

    private static Component confirmationPanel(
        String worldName,
        WorldAnalysisView analysis,
        boolean automaticBackupEnabled
    ) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel heading = new JLabel("This world has terrain borders ForceBlend can prepare.");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(10));
        panel.add(line("World", worldName));
        panel.add(line("Chunks to prepare", formatInteger(analysis.selectedChunks())));
        panel.add(line("Affected regions", formatInteger(analysis.affectedRegions())));
        panel.add(line("Chunks scanned", formatInteger(analysis.scannedChunks())));
        panel.add(line("Estimated time", formatDuration(analysis.estimatedDuration())));
        if (analysis.deferredChunks() > 0) {
            panel.add(line("Deferred by this run's limit", formatInteger(analysis.deferredChunks())));
        }
        if (automaticBackupEnabled) {
            panel.add(Box.createVerticalStrut(8));
            JLabel backupNotice = new JLabel(
                "Automatic backups are enabled; either Apply option will create a backup."
            );
            backupNotice.setFont(backupNotice.getFont().deriveFont(Font.ITALIC));
            backupNotice.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(backupNotice);
        }
        panel.add(Box.createVerticalStrut(10));
        JLabel prompt = new JLabel("How would you like to continue?");
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(prompt);
        return panel;
    }

    private static Optional<ForceBlendConfig> showConfigurationDialog(ForceBlendConfig config) {
        initializeLookAndFeel();
        JCheckBox automaticProcessing = new JCheckBox(
            "Analyze automatically after selecting a world",
            config.automaticProcessingEnabled()
        );
        JCheckBox automaticBackup = new JCheckBox(
            "Always create a backup before applying changes",
            config.automaticBackupEnabled()
        );
        JCheckBox detailedLogging = new JCheckBox(
            "Write detailed region and chunk diagnostics to the log",
            config.detailedLoggingEnabled()
        );

        JComboBox<ChunkLimitChoice> chunkLimit = new JComboBox<>(chunkLimitChoices(config));
        selectChunkLimit(chunkLimit, config.maximumChunksPerRun());
        JComboBox<ThreadChoice> processingThreads = new JComboBox<>(threadChoices(config));
        selectThreadCount(processingThreads, config.processingThreads());

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        fields.add(automaticProcessing);
        fields.add(automaticBackup);
        fields.add(detailedLogging);
        fields.add(Box.createVerticalStrut(10));
        fields.add(labeledField("Maximum chunks per opening", chunkLimit));
        fields.add(Box.createVerticalStrut(6));
        fields.add(labeledField("Processing threads", processingThreads));

        Object[] options = {"Save", "Cancel"};
        int result = showOptions(
            fields,
            JOptionPane.PLAIN_MESSAGE,
            options,
            0
        );
        if (result != 0) {
            return Optional.empty();
        }

        ChunkLimitChoice selectedLimit = (ChunkLimitChoice) chunkLimit.getSelectedItem();
        ThreadChoice selectedThreads = (ThreadChoice) processingThreads.getSelectedItem();
        if (selectedLimit == null || selectedThreads == null) {
            return Optional.empty();
        }
        return Optional.of(new ForceBlendConfig(
            automaticProcessing.isSelected(),
            automaticBackup.isSelected(),
            detailedLogging.isSelected(),
            selectedLimit.value(),
            selectedThreads.value()
        ));
    }

    private static JPanel labeledField(String labelText, Component field) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(new JLabel(labelText + ":"), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static ChunkLimitChoice[] chunkLimitChoices(ForceBlendConfig config) {
        java.util.List<ChunkLimitChoice> choices = new java.util.ArrayList<>();
        choices.add(new ChunkLimitChoice(ForceBlendConfig.UNLIMITED_CHUNKS, "No limit (default)"));
        for (long value : new long[]{100L, 500L, 1_000L, 5_000L}) {
            choices.add(new ChunkLimitChoice(value, formatInteger((int) value)));
        }
        if (config.hasChunkLimit() && choices.stream().noneMatch(choice -> choice.value() == config.maximumChunksPerRun())) {
            choices.add(new ChunkLimitChoice(
                config.maximumChunksPerRun(),
                formatLong(config.maximumChunksPerRun()) + " (current)"
            ));
        }
        return choices.toArray(ChunkLimitChoice[]::new);
    }

    private static ThreadChoice[] threadChoices(ForceBlendConfig config) {
        int available = Math.min(
            Runtime.getRuntime().availableProcessors(),
            ForceBlendConfig.MAX_CONFIGURED_THREADS
        );
        java.util.List<ThreadChoice> choices = new java.util.ArrayList<>();
        choices.add(new ThreadChoice(ForceBlendConfig.ALL_AVAILABLE_THREADS, "All available (default)"));
        for (int value = 1; value <= available; value++) {
            choices.add(new ThreadChoice(value, Integer.toString(value)));
        }
        if (!config.usesAllAvailableThreads()
            && choices.stream().noneMatch(choice -> choice.value() == config.processingThreads())) {
            choices.add(new ThreadChoice(config.processingThreads(), config.processingThreads() + " (current)"));
        }
        return choices.toArray(ThreadChoice[]::new);
    }

    private static void selectChunkLimit(JComboBox<ChunkLimitChoice> choices, long value) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (choices.getItemAt(index).value() == value) {
                choices.setSelectedIndex(index);
                return;
            }
        }
    }

    private static void selectThreadCount(JComboBox<ThreadChoice> choices, int value) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (choices.getItemAt(index).value() == value) {
                choices.setSelectedIndex(index);
                return;
            }
        }
    }

    private static int showOptions(Component message, int messageType, Object[] options, int preferredOption) {
        JOptionPane pane = new JOptionPane(
            message,
            messageType,
            JOptionPane.DEFAULT_OPTION,
            null,
            options,
            options[preferredOption]
        );
        JDialog dialog = pane.createDialog(null, TITLE);
        dialog.setAlwaysOnTop(true);
        dialog.setModal(true);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
        Object selectedValue = pane.getValue();
        for (int index = 0; index < options.length; index++) {
            if (java.util.Objects.equals(options[index], selectedValue)) {
                return index;
            }
        }
        return -1;
    }

    private static void showMessage(Object message, int messageType) {
        Object[] options = {"OK"};
        JLabel label = new JLabel("<html>" + escapeHtml(message.toString()).replace("\n", "<br>") + "</html>");
        showOptions(label, messageType, options, 0);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JLabel line(String name, String value) {
        JLabel label = new JLabel(name + ":  " + value);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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

    private static void initializeLookAndFeel() {
        if (lookAndFeelInitialized) {
            return;
        }
        synchronized (ForceBlendDesktopUi.class) {
            if (lookAndFeelInitialized) {
                return;
            }
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
                ForceBlendClient.LOGGER.debug("Using the default Swing look and feel", exception);
            }
            lookAndFeelInitialized = true;
        }
    }

    private static <T> T onEventDispatchThread(Callable<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return action.call();
            } catch (Exception exception) {
                throw new IllegalStateException("Desktop UI action failed", exception);
            }
        }

        FutureTask<T> task = new FutureTask<>(action);
        try {
            SwingUtilities.invokeAndWait(task);
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the desktop UI", exception);
        } catch (InvocationTargetException | java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Desktop UI action failed", exception.getCause());
        }
    }

    private record ChunkLimitChoice(long value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ThreadChoice(int value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
