package monitor

import
(
	"sync"
	"time"

	"modular-plugin/models"
	"modular-plugin/ssh"
	"modular-plugin/utils"
)

// CollectDeviceMetrics collects comprehensive metrics from a single device
func CollectDeviceMetrics(device models.DeviceInput) models.DeviceMetrics{
	metrics := models.DeviceMetrics{
		IP:          device.IP,
		DiscoveryID: device.DiscoveryID,
		Timestamp:   time.Now(),
	}

	if device.Protocol != "ssh"{
		metrics.Error = "Only SSH protocol is supported"
		return metrics
	}

	client := ssh.NewClient(device)
	output, err := client.RunCommand(ssh.GetMetricsScript())
	if err != nil {
		metrics.Error = err.Error()
		return metrics
	}

	// Parse the output
	utils.ParseMetricsOutput(output, &metrics)
	return metrics
}

// CollectMetricsBatch collects metrics for a batch of devices in parallel
func CollectMetricsBatch(devices models.BatchInput) map[int]models.DeviceMetrics {
	var wg sync.WaitGroup

	// Create map to store results
	metricsMap := make(map[int]models.DeviceMetrics)
	var mapMutex sync.Mutex

	// Process each device in the batch concurrently
	for _, device := range devices {
		wg.Add(1)

		go func(dev models.DeviceInput) {
			defer wg.Done()

			// Collect metrics for this device
			metric := CollectDeviceMetrics(dev)

			// Store in the map
			mapMutex.Lock()
			metricsMap[dev.DiscoveryID] = metric
			mapMutex.Unlock()
		}(device)
	}

	// Wait for all devices in this batch to complete
	wg.Wait()

	return metricsMap
}

// ProcessBatch processes a batch of devices and returns metrics
func ProcessBatch(devices models.BatchInput, batchID, totalBatches int) models.BatchMetricsResult {
	// Start time for this batch
	startTime := time.Now()

	// Collect metrics in parallel
	metricsMap := CollectMetricsBatch(devices)

	// Calculate execution time
	executionTime := time.Since(startTime)

	// Create batch result
	return models.BatchMetricsResult{
		BatchID:      batchID,
		Metrics:      metricsMap,
		BatchSize:    len(devices),
		TotalBatches: totalBatches,
		ProcessedAt:  time.Now(),
		ExecutionMs:  executionTime.Milliseconds(),
	}
}

// HandleMetrics processes all devices in batches and returns results
func HandleMetrics(requestID string, input models.BatchInput) []models.BatchMetricsResult {
	// Determine batch size and total number of batches
	batchSize := 20
	totalDevices := len(input)
	totalBatches := (totalDevices + batchSize - 1) / batchSize // Ceiling division

	results := make([]models.BatchMetricsResult, 0, totalBatches)

	// Process each batch
	for batchIdx := 0; batchIdx < totalBatches; batchIdx++ {
		startIdx := batchIdx * batchSize
		endIdx := startIdx + batchSize
		if endIdx > totalDevices {
			endIdx = totalDevices
		}

		// Get the current batch of devices
		batchDevices := input[startIdx:endIdx]

		// Process this batch and collect results
		result := ProcessBatch(batchDevices, batchIdx+1, totalBatches)

		// Add request ID and type
		result.RequestID = requestID
		result.Type = "metrics"

		// Add to results
		results = append(results, result)
	}

	return results
}