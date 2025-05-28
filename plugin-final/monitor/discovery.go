package monitor

import (
	"fmt"
	"time"

	"plugin-final/models"
	"plugin-final/ssh"
)

// CheckDeviceConnectivity verifies SSH connectivity to a device within 3 seconds
func CheckDeviceConnectivity(device models.DeviceInput) (bool, string) {
	if device.Protocol != "ssh" {
		return false, "Only SSH protocol is supported"
	}

	client := ssh.NewClient(device)
	connected, message := client.CheckConnectivityWithContext()
	if !connected {
		return false, message // Use the error message from SSH connection attempt
	}

	return true, "device is reachable, ssh connection successful"
}

// HandleDiscovery processes a discovery request for a device
func HandleDiscovery(requestID string, device models.DeviceInput, sendResponse func(interface{}) error) { // Use monitor.DeviceInput
	result := models.DiscoveryResult{
		RequestID: requestID,
		Type:      "discovery",
		Timestamp: time.Now(),
	}

	result.Success, result.Details = CheckDeviceConnectivity(device)

	if err := sendResponse(result); err != nil {
		fmt.Printf("Failed to send discovery response for monitor ID %d: %v\n", device.MonitorID, err)
	}
}