import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public class CommandRunnerMontoya implements BurpExtension
{
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;
    private List<String> savedCommands = new ArrayList<>();
    private Map<String, TabState> tabs = new HashMap<>();
    private static final String COMMANDS_FILE = "commands.txt";
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api)
    {
        this.api = api;

        // --- UNLOAD HANDLER: Clean up all running processes on unload ---
        api.extension().registerUnloadingHandler(() -> {
            for (TabState tab : tabs.values()) {
                if (tab.process != null) {
                    try {
                        tab.process.destroy();
                    } catch (Exception ex) {
                        // Optionally log or ignore
                    }
                }
            }
        });

        loadCommands();

        SwingUtilities.invokeLater(() -> {
            mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel headerPanel = new JPanel(new BorderLayout());
            JLabel headerLabel = new JLabel("Command Runner", JLabel.CENTER);
            headerLabel.setFont(new Font("Sans-Serif", Font.BOLD, 16));
            headerLabel.setBorder(new EmptyBorder(8, 10, 8, 10));
            headerPanel.add(headerLabel, BorderLayout.CENTER);

            tabbedPane = new JTabbedPane();
            tabbedPane.setBorder(new EmptyBorder(10, 0, 0, 0));

            JButton addTabButton = new JButton("+ New Tab");
            addTabButton.setFont(new Font("Sans-Serif", Font.PLAIN, 13));
            addTabButton.setBorder(new EmptyBorder(8, 15, 8, 15));
            addTabButton.addActionListener(e -> addTab());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(addTabButton);
            headerPanel.add(buttonPanel, BorderLayout.EAST);

            mainPanel.add(headerPanel, BorderLayout.NORTH);
            mainPanel.add(tabbedPane, BorderLayout.CENTER);

            addTab(); // create first tab

            // Register tab with Montoya
            api.userInterface().registerSuiteTab("Command Runner", mainPanel);
        });
    }

    private void addTab() {
        String tabId = UUID.randomUUID().toString();
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Command configuration panel ---
        JPanel commandPanel = new JPanel();
        commandPanel.setLayout(new BoxLayout(commandPanel, BoxLayout.Y_AXIS));
        commandPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(Color.GRAY), "Command Configuration"),
                new EmptyBorder(10, 10, 10, 10)));

        // Command input
        JPanel cmdInputPanel = new JPanel(new BorderLayout());
        JLabel cmdLabel = new JLabel("Command:");
        cmdLabel.setFont(new Font("Sans-Serif", Font.PLAIN, 12));
        cmdInputPanel.add(cmdLabel, BorderLayout.WEST);
        JTextField cmdInput = new JTextField("echo Hello from tab");
        cmdInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cmdInput.setBorder(new CompoundBorder(
                new LineBorder(Color.LIGHT_GRAY),
                new EmptyBorder(5, 5, 5, 5)));
        cmdInputPanel.add(cmdInput, BorderLayout.CENTER);
        commandPanel.add(cmdInputPanel);
        commandPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Saved commands
        JPanel savedCmdPanel = new JPanel(new BorderLayout());
        savedCmdPanel.add(new JLabel("Saved Commands:"), BorderLayout.WEST);
        JComboBox<String> cmdCombo = new JComboBox<>(savedCommands.toArray(new String[0]));
        cmdCombo.setFont(new Font("Sans-Serif", Font.PLAIN, 12));
        cmdCombo.setEditable(false);
        cmdCombo.setPreferredSize(new Dimension(300, 30));
        cmdCombo.addActionListener(e -> {
            if (cmdCombo.getSelectedItem() != null)
                cmdInput.setText(cmdCombo.getSelectedItem().toString());
        });
        savedCmdPanel.add(cmdCombo, BorderLayout.CENTER);

        JPanel cmdButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = styledButton("Save Command");
        saveButton.addActionListener(e -> saveCommand(cmdInput.getText(), cmdCombo));
        JButton deleteButton = styledButton("Delete Command");
        deleteButton.addActionListener(e -> deleteCommand(cmdCombo));
        cmdButtonPanel.add(saveButton);
        cmdButtonPanel.add(deleteButton);
        savedCmdPanel.add(cmdButtonPanel, BorderLayout.EAST);
        commandPanel.add(savedCmdPanel);
        commandPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runButton = styledButton("Run command");
        JButton cancelButton = styledButton("Cancel");
        cancelButton.setEnabled(false);
        JButton closeButton = styledButton("Close Tab");
        actionPanel.add(runButton);
        actionPanel.add(cancelButton);
        actionPanel.add(closeButton);
        commandPanel.add(actionPanel);

        // --- Output panel ---
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(Color.GRAY), "Command Output"),
                new EmptyBorder(10, 10, 10, 10)));

        JTextPane outputArea = new JTextPane();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        outputArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        ((DefaultCaret) outputArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setPreferredSize(new Dimension(100, 400));
        outputPanel.add(outputScroll, BorderLayout.CENTER);

        // Interactive input field
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JButton sendButton = styledButton("Send Input");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        outputPanel.add(inputPanel, BorderLayout.SOUTH);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, commandPanel, outputPanel);
        splitPane.setResizeWeight(0.3);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerSize(8);
        panel.add(splitPane, BorderLayout.CENTER);

        // --- Tab state ---
        TabState tabState = new TabState();
        tabState.panel = panel;
        tabState.cmdInput = cmdInput;
        tabState.cmdCombo = cmdCombo;
        tabState.runButton = runButton;
        tabState.cancelButton = cancelButton;
        tabState.closeButton = closeButton;
        tabState.outputArea = outputArea;
        tabState.inputField = inputField;
        tabState.sendButton = sendButton;
        tabState.process = null;
        tabs.put(tabId, tabState);

        // --- Button actions ---
        runButton.addActionListener(e -> runCommand(tabId));
        cancelButton.addActionListener(e -> cancelCommand(tabId));
        closeButton.addActionListener(e -> closeTab(tabId));
        sendButton.addActionListener(e -> sendInput(tabId));
        inputField.addActionListener(e -> sendInput(tabId)); // Enter key

        tabbedPane.addTab("Tab", panel);
        int idx = tabbedPane.indexOfComponent(panel);
        tabbedPane.setTitleAt(idx, "Tab " + (idx + 1));
        tabbedPane.setSelectedIndex(idx);
    }

    private JButton styledButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Sans-Serif", Font.PLAIN, 12));
        b.setBorder(new EmptyBorder(6, 12, 6, 12));
        return b;
    }

    private void closeTab(String tabId) {
        TabState tab = tabs.get(tabId);
        if (tab != null) {
            int idx = tabbedPane.indexOfComponent(tab.panel);
            if (idx != -1) tabbedPane.remove(idx);
            cancelCommand(tabId);
            tabs.remove(tabId);
        }
    }

    private void saveCommand(String cmd, JComboBox<String> combo) {
        cmd = cmd.trim();
        if (!cmd.isEmpty() && !savedCommands.contains(cmd)) {
            savedCommands.add(cmd);
            for (TabState t : tabs.values())
                t.cmdCombo.addItem(cmd);
            saveCommands();
        }
    }

    private void deleteCommand(JComboBox<String> combo) {
        int idx = combo.getSelectedIndex();
        if (idx >= 0 && idx < savedCommands.size()) {
            String cmd = combo.getItemAt(idx);
            savedCommands.remove(cmd);
            for (TabState t : tabs.values())
                t.cmdCombo.removeItem(cmd);
            saveCommands();
        }
    }

    private void loadCommands() {
        savedCommands.clear();
        File f = new File(COMMANDS_FILE);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !savedCommands.contains(line))
                        savedCommands.add(line);
                }
            } catch (Exception e) {
                System.out.println("[Command Runner] Error loading commands: " + e);
            }
        }
    }

    private void saveCommands() {
        try (PrintWriter w = new PrintWriter(new FileWriter(COMMANDS_FILE))) {
            for (String cmd : savedCommands)
                w.println(cmd);
        } catch (Exception e) {
            System.out.println("[Command Runner] Error saving commands: " + e);
        }
    }

    private void runCommand(String tabId) {
        TabState tab = tabs.get(tabId);
        if (tab == null) return;
        String cmdText = tab.cmdInput.getText().trim();
        if (cmdText.isEmpty()) return;

        tab.runButton.setEnabled(false);
        tab.cancelButton.setEnabled(true);
        tab.outputArea.setText("> Running: " + cmdText + "\n\n");

        Runnable runner = () -> {
            Process process = null;
            try {
                ProcessBuilder pb;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb = new ProcessBuilder("cmd.exe", "/c", cmdText);
                } else {
                    // Use 'script' for PTY-like behavior on Unix
                    String wrappedCmd = String.format("script -q -c \"%s\" /dev/null", cmdText.replace("\"", "\\\""));
                    pb = new ProcessBuilder("bash", "-c", wrappedCmd);
                }
                pb.redirectErrorStream(true);
                process = pb.start();
                tab.process = process;

                InputStream in = process.getInputStream();

                // Read output byte by byte for prompt detection
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int b;
                while ((b = in.read()) != -1) {
                    buffer.write(b);
                    if (b == '\n' || b == '\r') {
                        String line = buffer.toString(StandardCharsets.UTF_8.name());
                        SwingUtilities.invokeLater(() -> appendAnsi(tab.outputArea, line));
                        buffer.reset();
                    } else {
                        String bufStr = buffer.toString(StandardCharsets.UTF_8.name());
                        if (bufStr.endsWith("[Y/n] ") || bufStr.endsWith("? ")) {
                            SwingUtilities.invokeLater(() -> appendAnsi(tab.outputArea, bufStr));
                            buffer.reset();
                        }
                    }
                }
                // Flush remaining buffer
                if (buffer.size() > 0) {
                    String line = buffer.toString(StandardCharsets.UTF_8.name());
                    SwingUtilities.invokeLater(() -> appendAnsi(tab.outputArea, line));
                }
                in.close();
                process.waitFor();
                int code = process.exitValue();
                SwingUtilities.invokeLater(() -> appendAnsi(tab.outputArea, "\nCommand exited with code: " + code + "\n"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendAnsi(tab.outputArea, "\nError: " + ex.getMessage() + "\n"));
            } finally {
                tab.process = null;
                SwingUtilities.invokeLater(() -> {
                    tab.runButton.setEnabled(true);
                    tab.cancelButton.setEnabled(false);
                });
            }
        };
        new Thread(runner).start();
    }

    private void cancelCommand(String tabId) {
        TabState tab = tabs.get(tabId);
        if (tab != null && tab.process != null) {
            try {
                tab.process.destroy();
                appendAnsi(tab.outputArea, "\nCommand was cancelled\n");
            } catch (Exception ex) {
                appendAnsi(tab.outputArea, "\nError cancelling command: " + ex.getMessage() + "\n");
            } finally {
                tab.process = null;
                tab.runButton.setEnabled(true);
                tab.cancelButton.setEnabled(false);
            }
        }
    }

    private void sendInput(String tabId) {
        TabState tab = tabs.get(tabId);
        if (tab != null && tab.process != null) {
            try {
                String userInput = tab.inputField.getText();
                OutputStream out = tab.process.getOutputStream();
                out.write((userInput + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                appendAnsi(tab.outputArea, "> " + userInput + "\n");
                tab.inputField.setText("");
            } catch (Exception ex) {
                appendAnsi(tab.outputArea, "\nError sending input: " + ex.getMessage() + "\n");
            }
        } else if (tab != null) {
            appendAnsi(tab.outputArea, "\nNo running process to send input to.\n");
        }
    }

    // --- ANSI color parsing and output ---
    private static final Pattern ANSI_PATTERN = Pattern.compile("(\u001B\\[[;\\d]*m)");

    private void appendAnsi(JTextPane pane, String text) {
        StyledDocument doc = pane.getStyledDocument();
        List<AnsiFragment> fragments = parseAnsiFragments(text);
        try {
            for (AnsiFragment frag : fragments) {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                if (frag.color != null)
                    StyleConstants.setForeground(attrs, frag.color);
                doc.insertString(doc.getLength(), frag.text, attrs);
            }
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // Minimal ANSI color code parser (expand as needed)
    private List<AnsiFragment> parseAnsiFragments(String text) {
        List<AnsiFragment> fragments = new ArrayList<>();
        Matcher m = ANSI_PATTERN.matcher(text);
        int last = 0;
        Color current = Color.WHITE;
        while (m.find()) {
            if (m.start() > last)
                fragments.add(new AnsiFragment(text.substring(last, m.start()), current));
            String code = m.group(1);
            current = ansiToColor(code, current);
            last = m.end();
        }
        if (last < text.length())
            fragments.add(new AnsiFragment(text.substring(last), current));
        return fragments;
    }

    // Map a few common ANSI codes to Java colors
    private Color ansiToColor(String code, Color current) {
        switch (code) {
            case "\u001B[30m": return Color.BLACK;
            case "\u001B[31m": return Color.RED;
            case "\u001B[32m": return new Color(0, 128, 0);
            case "\u001B[33m": return Color.ORANGE;
            case "\u001B[34m": return Color.BLUE;
            case "\u001B[35m": return new Color(128, 0, 128);
            case "\u001B[36m": return Color.CYAN;
            case "\u001B[37m": return Color.LIGHT_GRAY;
            case "\u001B[0m":  return Color.WHITE;
            default: return current;
        }
    }

    // --- Helper classes ---
    private static class TabState {
        JPanel panel;
        JTextField cmdInput;
        JComboBox<String> cmdCombo;
        JButton runButton, cancelButton, closeButton;
        JTextPane outputArea;
        JTextField inputField;
        JButton sendButton;
        Process process;
    }
    private static class AnsiFragment {
        String text; Color color;
        AnsiFragment(String t, Color c) { text = t; color = c; }
    }
}

