package utils

import (
	"strconv"
	"strings"

	"modular-plugin/models"
)

// ParseMetricsOutput parses the output from the metrics collection script
func ParseMetricsOutput(output string, metrics *models.DeviceMetrics) {
	// Split output into sections
	sections := make(map[string]string)
	lines := strings.Split(output, "\n")

	var currentSection string
	var currentContent []string

	for _, line := range lines {
		line = strings.TrimSpace(line)

		if strings.HasPrefix(line, "===") && strings.HasSuffix(line, "===") {
			// If we already have a section, save it
			if currentSection != "" && len(currentContent) > 0 {
				sections[currentSection] = strings.Join(currentContent, "\n")
			}

			// Start a new section
			currentSection = strings.TrimPrefix(strings.TrimSuffix(line, "==="), "===")
			currentContent = []string{}
		} else if currentSection != "" {
			currentContent = append(currentContent, line)
		}
	}
	// Save the last section
	if currentSection != "" && len(currentContent) > 0 {
		sections[currentSection] = strings.Join(currentContent, "\n")
	}

	// Extract metrics from sections
	if sysType, ok := sections["SYSTEM_TYPE"]; ok {
		metrics.SystemType = strings.TrimSpace(sysType)
	}

	if hostname, ok := sections["HOSTNAME"]; ok {
		metrics.Hostname = strings.TrimSpace(hostname)
	}

	if uptime, ok := sections["UPTIME"]; ok {
		metrics.Uptime = strings.TrimSpace(uptime)
	}

	if osInfo, ok := sections["OS_INFO"]; ok {
		metrics.OS = strings.TrimSpace(osInfo)
	}

	if cpuUsage, ok := sections["CPU_USAGE"]; ok {
		metrics.CPUUsage = strings.TrimSpace(cpuUsage)
	}

	if memoryUsage, ok := sections["MEMORY_USAGE"]; ok {
		metrics.MemoryUsage = strings.TrimSpace(memoryUsage)
	}

	if diskUsage, ok := sections["DISK_USAGE"]; ok {
		metrics.DiskUsage = strings.TrimSpace(diskUsage)
	}

	if interfaces, ok := sections["INTERFACES"]; ok {
		metrics.Interfaces = ParseInterfaces(interfaces)
	}

	if traffic, ok := sections["TRAFFIC_STATS"]; ok {
		ParseTrafficStats(traffic, metrics)
	}
}

// ParseInterfaces extracts network interface names
func ParseInterfaces(output string) []string {
	var interfaces []string
	lines := strings.Split(output, "\n")

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line != "" {
			interfaces = append(interfaces, line)
		}
	}

	return interfaces
}

// ParseTrafficStats extracts traffic statistics from the output
func ParseTrafficStats(output string, metrics *models.DeviceMetrics) {
	var inTraffic, outTraffic, bandwidth int64

	lines := strings.Split(output, "\n")
	interfaceFound := false

	for i := 0; i < len(lines); i++ {
		line := strings.TrimSpace(lines[i])

		if strings.HasPrefix(line, "Interface:") {
			interfaceFound = true
			continue
		}

		if interfaceFound {
			if strings.HasPrefix(line, "RX:") {
				// Extract RX bytes
				rxStr := strings.TrimPrefix(line, "RX:")
				rxStr = strings.TrimSpace(rxStr)
				rx, err := strconv.ParseInt(rxStr, 10, 64)
				if err == nil {
					inTraffic += rx
				}
			} else if strings.HasPrefix(line, "TX:") {
				// Extract TX bytes
				txStr := strings.TrimPrefix(line, "TX:")
				txStr = strings.TrimSpace(txStr)
				tx, err := strconv.ParseInt(txStr, 10, 64)
				if err == nil {
					outTraffic += tx
				}
			} else if strings.HasPrefix(line, "Speed:") {
				// Extract speed/bandwidth in Mbps
				speedStr := strings.TrimPrefix(line, "Speed:")
				speedStr = strings.TrimSpace(speedStr)
				speed, err := strconv.ParseInt(speedStr, 10, 64)
				if err == nil {
					// Convert Mbps to bps (bits per second)
					bandwidth += speed * 1000000
				}
			}
		}
	}

	metrics.InTraffic = inTraffic
	metrics.OutTraffic = outTraffic
	metrics.Bandwidth = bandwidth
}