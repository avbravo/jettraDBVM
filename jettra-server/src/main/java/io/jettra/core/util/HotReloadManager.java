package io.jettra.core.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utility to handle Hot-Reload/Restart of the JettraDB node across different environments.
 */
public class HotReloadManager {
    private static final Logger LOGGER = Logger.getLogger(HotReloadManager.class.getName());

    /**
     * Triggers a restart of the application.
     * Logic varies depending on the environment (Docker, Kubernetes, supervised run.sh, or standalone).
     */
    public static void restart() {
        LOGGER.info("Hot-Reload requested. Detecting environment for optimal restart strategy...");

        new Thread(() -> {
            try {
                // Wait for any pending network responses or file I/O to stabilize
                Thread.sleep(2000);

                if (isContainerized()) {
                    LOGGER.info("Environment: CONTAINER (Docker/K8s). Exiting with code 3. Orchestrator will handle the restart.");
                    System.exit(3);
                } else if (isSupervised()) {
                    LOGGER.info("Environment: SUPERVISED (run.sh). Exiting with code 3. Script loop will handle the restart.");
                    System.exit(3);
                } else if (isNativeImage()) {
                    LOGGER.info("Environment: NATIVE IMAGE. Exiting with code 3. External supervisor expected.");
                    System.exit(3);
                } else {
                    LOGGER.info("Environment: STANDALONE. Attempting self-relaunch...");
                    if (relaunch()) {
                        LOGGER.info("Self-relaunch successful. Exiting current process.");
                        System.exit(0);
                    } else {
                        LOGGER.warning("Self-relaunching not supported or failed. Exiting with code 3 as fallback.");
                        System.exit(3);
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Critical error during hot-reload: " + e.getMessage());
                System.exit(3); // Hard stop with restart signal as last resort
            }
        }, "HotReload-Thread").start();
    }

    private static boolean isContainerized() {
        // Detect Kubernetes or Docker
        return System.getenv("KUBERNETES_SERVICE_HOST") != null || 
               new java.io.File("/.dockerenv").exists();
    }

    private static boolean isSupervised() {
        // Check for the custom flag set in our run.sh or environment
        return "supervised".equalsIgnoreCase(System.getProperty("jettra.mode")) || 
               "SUPERVISED".equals(System.getenv("JETTRAMODE"));
    }

    private static boolean isNativeImage() {
        // GraalVM property
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    private static boolean relaunch() {
        try {
            String javaPath = ProcessHandle.current().info().command()
                    .orElse(System.getProperty("java.home") + "/bin/java");
            
            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = runtimeMxBean.getInputArguments();
            
            String command = System.getProperty("sun.java.command");
            if (command == null || command.isEmpty()) {
                LOGGER.warning("sun.java.command is empty, cannot relaunch.");
                return false;
            }

            List<String> javaCmd = new ArrayList<>();
            javaCmd.add(javaPath);
            
            // Add JVM arguments
            javaCmd.addAll(jvmArgs);
            
            // Handle command (Main class or JAR)
            String[] parts = command.split(" ");
            
            if (parts[0].endsWith(".jar")) {
                javaCmd.add("-jar");
                javaCmd.add(parts[0]);
            } else {
                String classpath = System.getProperty("java.class.path");
                javaCmd.add("-cp");
                javaCmd.add(classpath);
                javaCmd.add(parts[0]);
            }
            
            // Add application arguments
            for (int i = 1; i < parts.length; i++) {
                javaCmd.add(parts[i]);
            }
            
            // Add sleep argument to give time for port liberation
            javaCmd.add("-sleep");
            javaCmd.add("3000");

            LOGGER.info("Relaunching standalone node: " + String.join(" ", javaCmd));
            
            ProcessBuilder pb = new ProcessBuilder(javaCmd);
            pb.inheritIO();
            // Redirect to log file just in case
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new java.io.File("relaunch.log")));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(new java.io.File("relaunch.log")));
            pb.start();
            
            return true;
        } catch (Exception e) {
            LOGGER.warning("Failed to construct relaunch command: " + e.getMessage());
            return false;
        }
    }
}

