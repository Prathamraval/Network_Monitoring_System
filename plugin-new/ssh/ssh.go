package ssh

import (
	"context"
	"strconv"
	"strings"
	"time"

	"plugin-new/models"

	"golang.org/x/crypto/ssh"
)

// Client wraps an SSH client
type Client struct {
	config *ssh.ClientConfig
	addr   string
}

// NewClient creates a new SSH client
func NewClient(device models.DeviceInput) *Client {
	config := &ssh.ClientConfig{
		User: device.Username,
		Auth: []ssh.AuthMethod{
			ssh.Password(device.Password),
		},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
		Timeout:         3 * time.Second,
	}
	return &Client{
		config: config,
		addr:   device.IP + ":" + strconv.Itoa(device.Port),
	}
}

// CheckConnectivityWithContext checks SSH connectivity with a context
func (c *Client) CheckConnectivityWithContext() (bool, string) {
	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		return false, err.Error()
	}
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return false, err.Error()
	}
	defer session.Close()

	return true, "Successfully connected via SSH"
}

// RunCommandWithContext runs a command with a context
func (c *Client) RunCommandWithContext(ctx context.Context, cmd string) (string, error) {
	client, err := ssh.Dial("tcp", c.addr, c.config)
	if err != nil {
		return "", err
	}
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return "", err
	}
	defer session.Close()

	// Create a pipe to capture output
	outputChan := make(chan string, 1)
	errorChan := make(chan error, 1)

	go func() {
		output, err := session.CombinedOutput(cmd)
		if err != nil {
			errorChan <- err
			return
		}
		outputChan <- strings.TrimSpace(string(output))
	}()

	select {
	case <-ctx.Done():
		return "", ctx.Err()
	case err := <-errorChan:
		return "", err
	case output := <-outputChan:
		return output, nil
	}
}
