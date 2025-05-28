package monitor

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"plugin-final/models" // Import models, aliased as monitor
	"plugin-final/ssh"
)

// MetricsScript returns a single SSH command to collect all required metrics
func MetricsScript() string {
	return `
set -e
meminfo=$(cat /proc/meminfo)
stat=$(cat /proc/stat)
loadavg=$(cat /proc/loadavg)
netstat=$(netstat -s 2>/dev/null || ss -s)
df=$(df -B1 / | tail -1)
uptime=$(uptime)
uname=$(uname -a)
ps=$(ps aux | wc -l)
threads=$(ps -eL | wc -l)
blocked=$(ps -e -o state | grep T | wc -l)
hostname=$(hostname)
nproc=$(nproc)
echo "meminfo:$meminfo"
echo "stat:$stat"
echo "loadavg:$loadavg"
echo "netstat:$netstat"
echo "df:$df"
echo "uptime:$uptime"
echo "uname:$uname"
echo "ps:$ps"
echo "threads:$threads"
echo "blocked:$blocked"
echo "hostname:$hostname"
echo "nproc:$nproc"
`
}

// ParseMetricsOutput parses the output of the metrics script into DeviceMetrics
func ParseMetricsOutput(output string, metrics *models.DeviceMetrics) {
	lines := strings.Split(output, "\n")
	meminfo := map[string]int64{}
	for _, line := range lines {
		if strings.HasPrefix(line, "meminfo:") {
			for _, memLine := range strings.Split(line[8:], "\n") {
				parts := strings.Fields(memLine)
				if len(parts) >= 2 {
					if val, err := strconv.ParseInt(parts[1], 10, 64); err == nil {
						key := strings.TrimSuffix(parts[0], ":")
						if key == "MemTotal" || key == "MemFree" || key == "MemAvailable" ||
							key == "Buffers" || key == "Cached" || key == "SwapTotal" || key == "SwapFree" {
							meminfo[key] = val * 1024 // Convert kB to bytes
						}
					}
				}
			}
		} else if strings.HasPrefix(line, "stat:") {
			for _, statLine := range strings.Split(line[5:], "\n") {
				if strings.HasPrefix(statLine, "cpu ") {
					parts := strings.Fields(statLine)
					if len(parts) >= 8 {
						user, _ := strconv.ParseInt(parts[1], 10, 64)
						nice, _ := strconv.ParseInt(parts[2], 10, 64)
						system, _ := strconv.ParseInt(parts[3], 10, 64)
						idle, _ := strconv.ParseInt(parts[4], 10, 64)
						iowait, _ := strconv.ParseInt(parts[5], 10, 64)
						irq, _ := strconv.ParseInt(parts[6], 10, 64)
						total := user + nice + system + idle + iowait + irq
						if total > 0 {
							metrics.SystemCPUPercent = float64(user+nice+system+irq) * 100 / float64(total)
							metrics.SystemCPUKernelPercent = float64(system) * 100 / float64(total)
							metrics.SystemCPUIdlePercent = float64(idle) * 100 / float64(total)
							metrics.SystemCPUIOPercent = float64(iowait) * 100 / float64(total)
							metrics.SystemCPUInterruptPercent = float64(irq) * 100 / float64(total)
						}
					}
				} else if strings.HasPrefix(statLine, "ctxt ") {
					parts := strings.Fields(statLine)
					if len(parts) >= 2 {
						metrics.SystemContextSwitchesPerSec, _ = strconv.ParseInt(parts[1], 10, 64)
					}
				}
			}
		} else if strings.HasPrefix(line, "loadavg:") {
			parts := strings.Fields(line[8:])
			if len(parts) >= 3 {
				metrics.SystemLoadAvg1Min, _ = strconv.ParseFloat(parts[0], 64)
				metrics.SystemLoadAvg5Min, _ = strconv.ParseFloat(parts[1], 64)
				metrics.SystemLoadAvg15Min, _ = strconv.ParseFloat(parts[2], 64)
			}
		} else if strings.HasPrefix(line, "netstat:") {
			for _, netLine := range strings.Split(line[8:], "\n") {
				if strings.Contains(netLine, "total packets received") {
					parts := strings.Fields(netLine)
					if len(parts) >= 1 {
						metrics.SystemNetworkErrorPackets, _ = strconv.ParseInt(parts[0], 10, 64)
					}
				} else if strings.Contains(netLine, "TCP") && strings.Contains(netLine, "active connections") {
					parts := strings.Fields(netLine)
					if len(parts) >= 1 {
						metrics.SystemNetworkTCPConnections, _ = strconv.ParseInt(parts[0], 10, 64)
					}
				} else if strings.Contains(netLine, "UDP") {
					parts := strings.Fields(netLine)
					if len(parts) >= 1 {
						metrics.SystemNetworkUDPConnections, _ = strconv.ParseInt(parts[0], 10, 64)
					}
				}
			}
		} else if strings.HasPrefix(line, "df:") {
			parts := strings.Fields(line[3:])
			if len(parts) >= 4 {
				metrics.SystemDiskCapacityBytes, _ = strconv.ParseInt(parts[1], 10, 64)
				metrics.SystemDiskUsedBytes, _ = strconv.ParseInt(parts[2], 10, 64)
				metrics.SystemDiskFreeBytes, _ = strconv.ParseInt(parts[3], 10, 64)
				if metrics.SystemDiskCapacityBytes > 0 {
					metrics.SystemDiskUsedPercent = float64(metrics.SystemDiskUsedBytes) * 100 / float64(metrics.SystemDiskCapacityBytes)
					metrics.SystemDiskFreePercent = float64(metrics.SystemDiskFreeBytes) * 100 / float64(metrics.SystemDiskCapacityBytes)
				}
			}
		} else if strings.HasPrefix(line, "uptime:") {
			metrics.Uptime = strings.TrimPrefix(line, "uptime:")
			parts := strings.Fields(metrics.Uptime)
			if len(parts) >= 3 {
				metrics.StartedTime = parts[0]
				if seconds, err := strconv.ParseInt(strings.Replace(parts[2], ",", "", -1), 10, 64); err == nil {
					metrics.StartedTimeSeconds = seconds
				}
			}
		} else if strings.HasPrefix(line, "uname:") {
			parts := strings.Fields(line[6:])
			if len(parts) >= 2 {
				metrics.SystemOSName = parts[0]
				metrics.SystemOSVersion = parts[1]
			}
		} else if strings.HasPrefix(line, "ps:") {
			metrics.SystemRunningProcesses, _ = strconv.ParseInt(strings.TrimSpace(line[3:]), 10, 64)
		} else if strings.HasPrefix(line, "threads:") {
			metrics.SystemThreads, _ = strconv.ParseInt(strings.TrimSpace(line[8:]), 10, 64)
		} else if strings.HasPrefix(line, "blocked:") {
			metrics.SystemBlockedProcesses, _ = strconv.ParseInt(strings.TrimSpace(line[8:]), 10, 64)
		} else if strings.HasPrefix(line, "hostname:") {
			metrics.SystemName = strings.TrimSpace(line[9:])
		} else if strings.HasPrefix(line, "nproc:") {
			metrics.SystemCPUCores, _ = strconv.ParseInt(strings.TrimSpace(line[6:]), 10, 64)
		}
	}

	// Calculate memory metrics
	metrics.SystemMemoryInstalledBytes = meminfo["MemTotal"]
	metrics.SystemMemoryFreeBytes = meminfo["MemFree"]
	metrics.SystemMemoryAvailableBytes = meminfo["MemAvailable"]
	metrics.SystemBufferMemoryBytes = meminfo["Buffers"]
	metrics.SystemCacheMemoryBytes = meminfo["Cached"]
	metrics.SystemSwapMemoryFreeBytes = meminfo["SwapFree"]
	metrics.SystemSwapMemoryUsedBytes = meminfo["SwapTotal"] - meminfo["SwapFree"]
	metrics.SystemOverallMemoryUsedBytes = meminfo["MemTotal"] - meminfo["MemFree"]
	metrics.SystemMemoryUsedBytes = meminfo["MemTotal"] - meminfo["MemAvailable"]
	if meminfo["MemTotal"] > 0 {
		metrics.SystemMemoryFreePercent = float64(meminfo["MemFree"]) * 100 / float64(meminfo["MemTotal"])
		metrics.SystemMemoryUsedPercent = float64(metrics.SystemMemoryUsedBytes) * 100 / float64(meminfo["MemTotal"])
		metrics.SystemOverallMemoryUsedPercent = float64(metrics.SystemOverallMemoryUsedBytes) * 100 / float64(meminfo["MemTotal"])
		metrics.SystemOverallMemoryFreePercent = float64(meminfo["MemFree"]) * 100 / float64(meminfo["MemTotal"])
	}
	if meminfo["SwapTotal"] > 0 {
		metrics.SystemSwapMemoryFreePercent = float64(meminfo["SwapFree"]) * 100 / float64(meminfo["SwapTotal"])
		metrics.SystemSwapMemoryUsedPercent = float64(metrics.SystemSwapMemoryUsedBytes) * 100 / float64(meminfo["SwapTotal"])
	}
}

// CollectDeviceMetrics collects metrics from a single device with a dynamic timeout
func CollectDeviceMetrics(device models.DeviceInput) models.DeviceMetrics { // Use monitor.DeviceInput and monitor.DeviceMetrics
	metrics := models.DeviceMetrics{
		MonitorID: device.MonitorID,
		IP:        device.IP,
		Timestamp: time.Now(),
	}

	if device.Protocol != "ssh" || !device.Status {
		metrics.Error = "Only SSH protocol is supported or device is disabled"
		return metrics
	}

	// Use wait_time as timeout, default to 5 seconds if invalid
	waitTimeMs := device.WaitTime * 1000
	if waitTimeMs <= 0 {
		waitTimeMs = 5000 // Default to 5 seconds if invalid or not provided
	}

	client := ssh.NewClient(device)
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(waitTimeMs)*time.Millisecond)
	defer cancel()

	output, err := client.RunCommandWithContext(ctx, MetricsScript())
	if err != nil {
		metrics.Error = "device is not up: " + err.Error()
		return metrics
	}

	ParseMetricsOutput(output, &metrics)
	return metrics
}

// HandleMetrics processes metrics collection for a batch of devices
func HandleMetrics(requestID string, input models.BatchInput, sendResponse func(interface{}) error) {
	var wg sync.WaitGroup
	sem := make(chan struct{}, 50) // Limit to 50 goroutines

	responseChan := make(chan models.BatchMetricsResult, len(input))

	// Start a goroutine to send responses from the channel
	var sendWg sync.WaitGroup
	sendWg.Add(1)
	go func() {
		defer sendWg.Done()
		for result := range responseChan {
			if err := sendResponse(result); err != nil {
				fmt.Printf("Failed to send metrics response for monitor ID %d: %v\n", result.MonitorID, err)
			}
		}
	}()

	for _, device := range input {
		wg.Add(1)
		sem <- struct{}{} // Acquire semaphore
		go func(dev models.DeviceInput) {
			defer wg.Done()
			defer func() { <-sem }() // Release semaphore
			metrics := CollectDeviceMetrics(dev)
			result := models.BatchMetricsResult{
				RequestID: requestID,
				Type:      "metrics",
				MonitorID: dev.MonitorID,
				Metrics:   metrics,
				Timestamp: time.Now(),
			}
			responseChan <- result
		}(device)
	}

	wg.Wait()
	close(responseChan) // Close the response channel after all goroutines are done
	sendWg.Wait()       // Wait for all responses to be sent
}