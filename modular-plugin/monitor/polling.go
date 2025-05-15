package monitor

import (
	"fmt"

	"modular-plugin/models"
	"modular-plugin/ssh"
)

// GetDeviceUptime fetches the uptime of a device
func GetDeviceUptime(device models.DeviceInput) (string, error) {
	if device.Protocol != "ssh" {
		return "", fmt.Errorf("only SSH protocol is supported")
	}

	client := ssh.NewClient(device)
	output, err := client.RunCommand("uptime")
	if err != nil {
		return "", err
	}

	return output, nil
}

// HandlePolling processes a polling request for a device
func HandlePolling(device models.DeviceInput) models.DiscoveryResult {
	result := models.DiscoveryResult{}

	if device.Protocol != "ssh" {
		result.Success = false
		result.Details = "Only SSH protocol is supported"
		return result
	}

	// Get uptime via SSH
	uptime, err := GetDeviceUptime(device)
	if err != nil {
		result.Success = false
		result.Details = fmt.Sprintf("Failed to get uptime: %s", err.Error())
		return result
	}

	result.Success = true
	result.Details = "Successfully polled device"
	result.Uptime = uptime

	return result
}