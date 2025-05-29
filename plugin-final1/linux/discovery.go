package linux

import (
	"plugin-final1/models"
	"plugin-final1/ssh"
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
