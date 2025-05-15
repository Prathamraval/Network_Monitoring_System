package models

import (
	"encoding/json"
	"time"
)

// DeviceInput represents the input for a single device
type DeviceInput struct {
	IP          string `json:"ip"`
	Port        int    `json:"port"`
	Username    string `json:"username"`
	Password    string `json:"password"`
	Protocol    string `json:"protocol,omitempty"`
	DiscoveryID int    `json:"monitor_id"`
}

// BatchInput represents a batch of devices for metrics collection
type BatchInput []DeviceInput

// DeviceMetrics contains the collected metrics from a single device
type DeviceMetrics struct {
	IP          string    `json:"ip"`
	DiscoveryID int       `json:"monitor_id"`
	SystemType  string    `json:"system_type"`
	Hostname    string    `json:"hostname"`
	Uptime      string    `json:"uptime"`
	InTraffic   int64     `json:"in_traffic"`
	OutTraffic  int64     `json:"out_traffic"`
	Interfaces  []string  `json:"interfaces"`
	CPUUsage    string    `json:"cpu_usage"`
	MemoryUsage string    `json:"memory_usage"`
	DiskUsage   string    `json:"disk_usage"`
	OS          string    `json:"os_info"`
	Bandwidth   int64     `json:"bandwidth"`
	Timestamp   time.Time `json:"timestamp"`
	Error       string    `json:"error,omitempty"`
}

// BatchMetricsResult represents a response containing metrics for a batch of devices
type BatchMetricsResult struct {
	RequestID    string                `json:"request_id,omitempty"`
	Type         string                `json:"type,omitempty"`
	BatchID      int                   `json:"batch_id"`
	Metrics      map[int]DeviceMetrics `json:"metrics"`
	BatchSize    int                   `json:"batch_size"`
	TotalBatches int                   `json:"total_batches"`
	ProcessedAt  time.Time             `json:"processed_at"`
	ExecutionMs  int64                 `json:"execution_ms"`
}

// DiscoveryResult is used for basic discovery/connectivity checks
type DiscoveryResult struct {
	RequestID string `json:"request_id,omitempty"`
	Success   bool   `json:"success"`
	Details   string `json:"details"`
	Uptime    string `json:"uptime,omitempty"`
}

// ZMQRequest represents a request from the Vert.x verticle
type ZMQRequest struct {
	RequestID string          `json:"request_id"`
	Command   string          `json:"command"`
	Data      json.RawMessage `json:"data"`
}