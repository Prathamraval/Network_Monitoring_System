package models

import (
	"encoding/json"
	"time"
)

// DeviceInput represents the input for a single device
type DeviceInput struct {
	MonitorID int    `json:"monitor_id"`
	IP        string `json:"ip_address"`
	Port      int    `json:"port_no"`
	Username  string `json:"username"`
	Password  string `json:"password"`
	Protocol  string `json:"protocol"`
	Status    bool   `json:"status"`
}

// BatchInput represents a batch of devices for metrics collection
type BatchInput []DeviceInput

// DeviceMetrics contains the collected metrics from a single device
type DeviceMetrics struct {
	MonitorID                      int       `json:"monitor_id"`
	IP                             string    `json:"ip"`
	Timestamp                      time.Time `json:"timestamp"`
	Error                          string    `json:"error,omitempty"`
	Uptime                         string    `json:"uptime,omitempty"`
	SystemOverallMemoryFreeBytes   int64     `json:"system.overall.memory.free.bytes,omitempty"`
	SystemLoadAvg15Min             float64   `json:"system.load.avg15.min,omitempty"`
	SystemSwapMemoryFreeBytes      int64     `json:"system.swap.memory.free.bytes,omitempty"`
	SystemSwapMemoryUsedPercent    float64   `json:"system.swap.memory.used.percent,omitempty"`
	SystemLoadAvg1Min              float64   `json:"system.load.avg1.min,omitempty"`
	SystemNetworkUDPConnections    int64     `json:"system.network.udp.connections,omitempty"`
	SystemLoadAvg5Min              float64   `json:"system.load.avg5.min,omitempty"`
	SystemBlockedProcesses         int64     `json:"system.blocked.processes,omitempty"`
	SystemCacheMemoryBytes         int64     `json:"system.cache.memory.bytes,omitempty"`
	SystemNetworkTCPConnections    int64     `json:"system.network.tcp.connections,omitempty"`
	SystemCPUCores                 int64     `json:"system.cpu.cores,omitempty"`
	SystemOSName                   string    `json:"system.os.name,omitempty"`
	SystemOSVersion                string    `json:"system.os.version,omitempty"`
	SystemContextSwitchesPerSec    int64     `json:"system.context.switches.per.sec,omitempty"`
	SystemDiskCapacityBytes        int64     `json:"system.disk.capacity.bytes,omitempty"`
	SystemBufferMemoryBytes        int64     `json:"system.buffer.memory.bytes,omitempty"`
	SystemSwapMemoryUsedBytes      int64     `json:"system.swap.memory.used.bytes,omitempty"`
	SystemCPUInterruptPercent      float64   `json:"system.cpu.interrupt.percent,omitempty"`
	SystemMemoryAvailableBytes     int64     `json:"system.memory.available.bytes,omitempty"`
	SystemOverallMemoryUsedBytes   int64     `json:"system.overall.memory.used.bytes,omitempty"`
	StartedTime                    string    `json:"started.time,omitempty"`
	StartedTimeSeconds             int64     `json:"started.time.seconds,omitempty"`
	SystemSwapMemoryFreePercent    float64   `json:"system.swap.memory.free.percent,omitempty"`
	SystemMemoryInstalledBytes     int64     `json:"system.memory.installed.bytes,omitempty"`
	SystemCPUPercent               float64   `json:"system.cpu.percent,omitempty"`
	SystemDiskFreeBytes            int64     `json:"system.disk.free.bytes,omitempty"`
	SystemMemoryUsedBytes          int64     `json:"system.memory.used.bytes,omitempty"`
	SystemMemoryFreeBytes          int64     `json:"system.memory.free.bytes,omitempty"`
	SystemOverallMemoryUsedPercent float64   `json:"system.overall.memory.used.percent,omitempty"`
	SystemRunningProcesses         int64     `json:"system.running.processes,omitempty"`
	SystemMemoryFreePercent        float64   `json:"system.memory.free.percent,omitempty"`
	SystemDiskFreePercent          float64   `json:"system.disk.free.percent,omitempty"`
	SystemCPUIOPercent             float64   `json:"system.cpu.io.percent,omitempty"`
	SystemDiskUsedPercent          float64   `json:"system.disk.used.percent,omitempty"`
	SystemNetworkErrorPackets      int64     `json:"system.network.error.packets,omitempty"`
	SystemThreads                  int64     `json:"system.threads,omitempty"`
	SystemName                     string    `json:"system.name,omitempty"`
	SystemDiskUsedBytes            int64     `json:"system.disk.used.bytes,omitempty"`
	SystemMemoryUsedPercent        float64   `json:"system.memory.used.percent,omitempty"`
	SystemOverallMemoryFreePercent float64   `json:"system.overall.memory.free.percent,omitempty"`
	SystemCPUKernelPercent         float64   `json:"system.cpu.kernel.percent,omitempty"`
	SystemCPUIdlePercent           float64   `json:"system.cpu.idle.percent,omitempty"`
}

// BatchMetricsResult represents a response containing metrics for a single device
type BatchMetricsResult struct {
	RequestID string        `json:"request_id"`
	Type      string        `json:"type"`
	MonitorID int           `json:"monitor_id"`
	Metrics   DeviceMetrics `json:"metrics"`
	Timestamp time.Time     `json:"timestamp"`
}

// DiscoveryResult is used for basic discovery/connectivity checks
type DiscoveryResult struct {
	RequestID string    `json:"request_id"`
	Type      string    `json:"type"`
// 	MonitorID int       `json:"monitor_id"`
	Success   bool      `json:"success"`
	Details   string    `json:"details"`
	Timestamp time.Time `json:"timestamp"`
}

// ZMQRequest represents a request from the Vert.x verticle
type ZMQRequest struct {
	RequestID string          `json:"request_id"`
	Command   string          `json:"command"`
	Data      json.RawMessage `json:"data"`
}
