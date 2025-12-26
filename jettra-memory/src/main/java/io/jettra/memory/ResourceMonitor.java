package io.jettra.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors system resources (RAM) and triggers optimizations.
 */
public class ResourceMonitor {
    private static final Logger LOGGER = Logger.getLogger(ResourceMonitor.class.getName());
    
    private final MemoryConfig config;
    private final ScheduledExecutorService scheduler;
    private final MemoryMXBean memoryBean;
    
    private volatile boolean isRunning = false;

    public ResourceMonitor(MemoryConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Jettra-Resource-Monitor");
            t.setDaemon(true);
            return t;
        });
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    public void startMonitoring() {
        if (isRunning) return;
        isRunning = true;
        // Check every 5 seconds
        scheduler.scheduleAtFixedRate(this::checkResources, 5, 5, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        isRunning = false;
        scheduler.shutdown();
    }

    private void checkResources() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        
        double usageRatio = (double) used / (double) max;
        
        // Log if pressure is high
        if (usageRatio > config.getCriticalMemoryThreshold()) {
            LOGGER.log(Level.WARNING, "Memory pressure high! Used: {0}%, limit: {1}%", 
                    new Object[]{String.format("%.2f", usageRatio * 100), config.getCriticalMemoryThreshold() * 100});
            
            triggerOptimization();
        }
    }
    
    private void triggerOptimization() {
        // Here we would trigger compaction of LSM structures or cleanup of old MVCC versions
        LOGGER.info("Triggering automatic memory optimization/compaction...");
        // System.gc(); // Typically avoided, but in a dedicated memory DB might be last resort
    }
    
    public long getAvailableMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getMax() - heapUsage.getUsed();
    }
}
