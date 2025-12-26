package io.jettra.memory.shell;

import io.jettra.memory.driver.JettraMemoryDriver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Scanner;

@Command(name = "jettra-memory-shell", mixinStandardHelpOptions = true, version = "1.0",
         description = "Shell to interact with JettraMemoryDB")
public class JettraMemoryShell implements Runnable {

    @Option(names = {"-n", "--name"}, description = "Database name to connect to", defaultValue = "default")
    private String dbName;

    private final JettraMemoryDriver driver;

    public JettraMemoryShell() {
        this.driver = new JettraMemoryDriver();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JettraMemoryShell()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Connecting to JettraMemoryDB [" + dbName + "]...");
        driver.connect(dbName);
        System.out.println("Connected. Type 'exit' to quit.");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("jettra-memory> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(line)) {
                break;
            }
            processCommand(line);
        }
        driver.close();
    }

    private void processCommand(String line) {
        // Implement parsing of commands like INSERT, SELECT, etc.
        // For now, just echo or show status
        if ("status".equalsIgnoreCase(line)) {
            System.out.println("DB Active: " + (driver.getDB() != null));
        } else {
            System.out.println("Unknown command: " + line);
        }
    }
}
