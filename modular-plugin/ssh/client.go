package ssh

import
(
	"fmt"
	"time"

	"golang.org/x/crypto/ssh"
	"modular-plugin/models"
)

// Client represents an SSH connection client
type Client struct {
	config *ssh.ClientConfig
	addr   string
}

// NewClient creates a new SSH client for a device
func NewClient(device models.DeviceInput) *Client {
	config := &ssh.ClientConfig{
		User: device.Username,
		Auth: []ssh.AuthMethod{
			ssh.Password(device.Password),
		},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
		Timeout:         3 * time.Second,
	}

	addr := fmt.Sprintf("%s:%d", device.IP, device.Port)

	return &Client{
		config: config,
		addr:   addr,
	}
}

// CheckConnectivity attempts to connect to a device via SSH
func (c *Client) CheckConnectivity() bool {
	// Try to connect
	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		return false
	}
	defer client.Close()

	// Try to open a session
	session, err := client.NewSession()
	if err != nil {
		return false
	}
	defer session.Close()

	return true
}

// RunCommand executes a command on the remote host and returns the output
func (c *Client) RunCommand(cmd string) (string, error) {
	// Connect to the device
	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		return "", fmt.Errorf("connection failed: %v", err)
	}
	defer client.Close()

	// Create a session
	session, err := client.NewSession()
	if err != nil {
		return "", fmt.Errorf("session creation failed: %v", err)
	}
	defer session.Close()

	// Run command
	output, err := session.CombinedOutput(cmd)
	if err != nil {
		return "", fmt.Errorf("command execution failed: %v", err)
	}

	return string(output), nil
}

// GetMetricsScript returns the bash script used to collect metrics
func GetMetricsScript() string {
	return `#!/bin/bash
echo "===SYSTEM_TYPE==="
uname -s

echo "===HOSTNAME==="
hostname

echo "===UPTIME==="
uptime

echo "===OS_INFO==="
if [ -f /etc/os-release ]; then
    cat /etc/os-release | grep "PRETTY_NAME" | cut -d= -f2
elif [ -f /etc/lsb-release ]; then
    cat /etc/lsb-release | grep "DESCRIPTION" | cut -d= -f2
else
    echo "Unknown OS"
fi

echo "===CPU_USAGE==="
top -bn1 | grep "Cpu(s)" | awk '{print $2 + $4 "%"}'

echo "===MEMORY_USAGE==="
free -m | grep Mem | awk '{print "Total: " $2 "MB, Used: " $3 "MB, Free: " $4 "MB, Usage: " int(($3*100)/$2) "%"}'

echo "===DISK_USAGE==="
df -h / | grep -v "Filesystem" | awk '{print "Total: " $2 ", Used: " $3 ", Free: " $4 ", Usage: " $5}'

echo "===INTERFACES==="
ls /sys/class/net/ | grep -v "lo"

echo "===TRAFFIC_STATS==="
for iface in $(ls /sys/class/net/ | grep -v "lo"); do
    echo "Interface: $iface"
    rx=$(cat /sys/class/net/$iface/statistics/rx_bytes 2>/dev/null || echo "0")
    tx=$(cat /sys/class/net/$iface/statistics/tx_bytes 2>/dev/null || echo "0")
    speed=$(cat /sys/class/net/$iface/speed 2>/dev/null || echo "0")
    echo "RX: $rx"
    echo "TX: $tx"
    echo "Speed: $speed"
done
`
}