package monitor

import
(
	"modular-plugin/models"
	"modular-plugin/ssh"
)

// CheckDeviceConnectivity verifies SSH connectivity to a device
func CheckDeviceConnectivity(device models.DeviceInput) (bool, string){
	if device.Protocol != "ssh"{
		return false, "Only SSH protocol is supported"
	}

	client := ssh.NewClient(device)
	connected := client.CheckConnectivity()

	if !connected{
		return false, "Failed to connect via SSH"
	}

	return true, "Successfully connected via SSH"
}

// HandleDiscovery processes a discovery request for a device
func HandleDiscovery(device models.DeviceInput) models.DiscoveryResult{
	result := models.DiscoveryResult{}

	if device.Protocol != "ssh"{
		result.Success = false
		result.Details = "Only SSH protocol is supported"
		return result
	}

	// Verify SSH connectivity
	connected, details := CheckDeviceConnectivity(device)
	result.Success = connected
	result.Details = details

	return result
}