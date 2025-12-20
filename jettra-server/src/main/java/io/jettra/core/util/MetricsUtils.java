package io.jettra.core.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import com.sun.management.OperatingSystemMXBean;

public class MetricsUtils {

    public static Map<String, Object> getSystemMetrics(String dataDir) {
        Map<String, Object> metrics = new HashMap<>();
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            
            // CPU
            double cpuLoad = osBean.getCpuLoad();
            if (cpuLoad < 0) cpuLoad = 0; // Sometimes returns -1 if not available yet
            metrics.put("cpuUsage", Math.round(cpuLoad * 1000.0) / 10.0); // e.g. 15.5
            
            // RAM
            long totalMemory = osBean.getTotalMemorySize();
            long freeMemory = osBean.getFreeMemorySize();
            metrics.put("ramTotal", totalMemory);
            metrics.put("ramFree", freeMemory);
            metrics.put("ramUsed", totalMemory - freeMemory);
            metrics.put("ramUsage", Math.round(((double)(totalMemory - freeMemory) / totalMemory) * 1000.0) / 10.0);
            metrics.put("ramTotalStr", formatSize(totalMemory));
            metrics.put("ramUsedStr", formatSize(totalMemory - freeMemory));
            metrics.put("ramFreeStr", formatSize(freeMemory));
            
            // Disk (Data Directory)
            File dataFolder = new File(dataDir != null ? dataDir : ".");
            if (!dataFolder.exists()) dataFolder.mkdirs();
            
            long totalDisk = dataFolder.getTotalSpace();
            long freeDisk = dataFolder.getFreeSpace();
            metrics.put("diskTotal", totalDisk);
            metrics.put("diskFree", freeDisk);
            metrics.put("diskUsed", totalDisk - freeDisk);
            metrics.put("diskUsage", Math.round(((double)(totalDisk - freeDisk) / totalDisk) * 1000.0) / 10.0);
            metrics.put("diskTotalStr", formatSize(totalDisk));
            metrics.put("diskUsedStr", formatSize(totalDisk - freeDisk));
            metrics.put("diskFreeStr", formatSize(freeDisk));
            
            // System
            metrics.put("availableProcessors", osBean.getAvailableProcessors());
            metrics.put("osName", osBean.getName());
            metrics.put("osVersion", osBean.getVersion());
            
        } catch (Exception e) {
            metrics.put("error", e.getMessage());
        }
        return metrics;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
