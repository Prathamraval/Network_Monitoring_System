package ssh

import (
	"context"
	"fmt"
	"time"

	"golang.org/x/crypto/ssh"
	"plugin-final/models"
)

// Client manages SSH connections to a device
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
		HostKeyCallback: ssh.InsecureIgnoreHostKey(), // For testing; use proper host key verification in production
		Timeout:         time.Duration(device.WaitTime) * time.Second,
	}
	addr := fmt.Sprintf("%s:%d", device.IP, device.Port)
	return &Client{config: config, addr: addr}
}

// CheckConnectivityWithContext tests SSH connectivity with a 3-second timeout
func (c *Client) CheckConnectivityWithContext() (bool, string) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		select {
		case <-ctx.Done():
			return false, fmt.Sprintf("connection timed out: %v", ctx.Err())
		default:
			return false, fmt.Sprintf("failed to connect: %v", err)
		}
	}
	defer client.Close()

	// Try a simple command to verify connectivity
	session, err := client.NewSession()
	if err != nil {
		return false, fmt.Sprintf("failed to create session: %v", err)
	}
	defer session.Close()

	// Run a simple command (e.g., "true") to confirm connectivity
	if err := session.Run("true"); err != nil {
		return false, fmt.Sprintf("failed to run test command: %v", err)
	}

	return true, "SSH connection successful"
}

// RunCommandWithContext executes a command over SSH with a context
func (c *Client) RunCommandWithContext(ctx context.Context, cmd string) (string, error) {
	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		return "", fmt.Errorf("failed to connect: %v", err)
	}
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return "", fmt.Errorf("failed to create session: %v", err)
	}
	defer session.Close()

	// Create a pipe for command output
	outputChan := make(chan string, 1)
	errChan := make(chan error, 1)

	go func() {
		output, err := session.CombinedOutput(cmd)
		if err != nil {
			errChan <- fmt.Errorf("failed to run command: %v", err)
			return
		}
		outputChan <- string(output)
	}()

	select {
	case <-ctx.Done():
		return "", ctx.Err() // Return context error (e.g., timeout)
	case err := <-errChan:
		return "", err // Return command execution error
	case output := <-outputChan:
		return output, nil // Return command output
	}
}