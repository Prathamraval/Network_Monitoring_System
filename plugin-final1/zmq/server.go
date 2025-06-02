package zmq

import (
	"encoding/json"
	"fmt"
	"plugin-final1/linux"
	"plugin-final1/models"
	"sync"

	"time"

	"github.com/pebbe/zmq4"
)

type MetricsTask struct {
	Device    models.DeviceInput
	RequestID string
}

// Server handles ZeroMQ communication with PUSH/PULL pattern
type Server struct {
	pullSocket    *zmq4.Socket
	pushSocket    *zmq4.Socket
	pullEndpoint  string
	pushEndpoint  string
	requestsWg    sync.WaitGroup
	metricsChan   chan MetricsTask        // Channel for metrics devices
	discoveryChan chan models.DeviceInput // Channel for discovery devices
	responseChan  chan interface{}        // Channel for all responses
}

// StartServer creates a new ZMQ server
func StartServer(pullEndpoint, pushEndpoint string) (*Server, error) {

	// Create a socket to receive tasks (PULL)

	pullSocket, err := zmq4.NewSocket(zmq4.PULL)
	if err != nil {
		return nil, fmt.Errorf("failed to create ZMQ PULL socket: %v", err)
	}

	// Create a socket to send results (PUSH)
	pushSocket, err := zmq4.NewSocket(zmq4.PUSH)
	if err != nil {
		pullSocket.Close()
		return nil, fmt.Errorf("failed to create ZMQ PUSH socket: %v", err)
	}

	// Set high water mark to limit message loss
	pullSocket.SetRcvhwm(1000)
	pushSocket.SetSndhwm(1000)

	server := &Server{
		pullSocket:    pullSocket,
		pushSocket:    pushSocket,
		pullEndpoint:  pullEndpoint,
		pushEndpoint:  pushEndpoint,
		metricsChan:   make(chan MetricsTask, 1000),       // Buffered for 1000 devices
		discoveryChan: make(chan models.DeviceInput, 100), // Buffered for 100 discovery requests
		responseChan:  make(chan interface{}, 1000),       // Buffered for 1000 responses
	}

	// Start response-sending goroutine
	go server.sendResponses()

	// Start 50 fixed goroutines for metrics processing
	for i := 0; i < 50; i++ {
		go server.processMetrics()
	}

	return server, nil
}

// Start begins listening for messages
func (s *Server) Start() error {

	// Bind the PULL socket to receive tasks
	err := s.pullSocket.Connect(s.pullEndpoint)

	if err != nil {
		return fmt.Errorf("failed to bind ZMQ PULL socket: %v", err)
	}

	// Connect the PUSH socket to send results
	err = s.pushSocket.Connect(s.pushEndpoint)

	if err != nil {
		return fmt.Errorf("failed to connect ZMQ PUSH socket: %v", err)
	}

	fmt.Printf("ZMQ server started successfully. PULL socket bound to %s, PUSH socket connected to %s\n",
		s.pullEndpoint, s.pushEndpoint)

	for {

		// Wait for the next request from the client
		message, err := s.pullSocket.RecvBytes(0)
		if err != nil {
			fmt.Printf("Error receiving message: %v\n", err)
			continue
		}

		fmt.Printf("Received request: %d bytes\n", len(message))

		// Parse and process the message
		var request models.ZMQRequest
		if err := json.Unmarshal(message, &request); err != nil {
			fmt.Printf("Failed to parse request: %v\n", err)
			s.responseChan <- map[string]interface{}{
				"request_id": request.RequestID,
				"type":       "error",
				"details":    fmt.Sprintf("Failed to parse request: %v", err),
				"timestamp":  time.Now(),
			}
			continue
		}

		var input models.BatchInput

		if err := json.Unmarshal(request.Data, &input); err != nil {

			fmt.Printf("Failed to parse input: %v\n", err)

			s.responseChan <- map[string]interface{}{
				"request_id": request.RequestID,
				"type":       request.Command,
				"details":    fmt.Sprintf("Failed to parse input: %v", err),
				"timestamp":  time.Now(),
			}
			continue
		}

		// Dispatch to appropriate channel
		switch request.Command {
		case "metrics":
			for _, device := range input {
				s.metricsChan <- MetricsTask{
					Device:    device,
					RequestID: request.RequestID,
				}
			}
		case "discovery":
			for _, device := range input {
				s.discoveryChan <- device
				// Spawn a goroutine for each discovery request
				go s.processDiscovery(request.RequestID)
			}
		default:
			s.responseChan <- map[string]interface{}{
				"request_id": request.RequestID,
				"type":       request.Command,
				"details":    fmt.Sprintf("Unknown command: %s", request.Command),
				"timestamp":  time.Now(),
			}
		}
	}
}

// Close shuts down the ZMQ server
func (s *Server) Close() {
	// Wait for all channels to drain
	close(s.metricsChan)
	close(s.discoveryChan)
	close(s.responseChan)
	s.requestsWg.Wait()

	if s.pullSocket != nil {
		s.pullSocket.Close()
	}
	if s.pushSocket != nil {
		s.pushSocket.Close()
	}
}

// processMetrics runs in 50 fixed goroutines to handle metrics requests
func (s *Server) processMetrics() {

	for task := range s.metricsChan {

		metrics := linux.CollectDeviceMetrics(task.Device)

		result := models.BatchMetricsResult{
			RequestID: task.RequestID, // RequestID set by sender
			Type:      "metrics",
			MonitorID: task.Device.MonitorID,
			Metrics:   metrics,
			Timestamp: time.Now(),
		}
		s.responseChan <- result
	}
}

// processDiscovery handles a single discovery request
func (s *Server) processDiscovery(requestID string) {

	if device, ok := <-s.discoveryChan; ok {

		result := models.DiscoveryResult{
			RequestID: requestID,
			Type:      "discovery",
			Timestamp: time.Now(),
		}

		result.Success, result.Details = linux.CheckDeviceConnectivity(device)

		s.responseChan <- result
	}
}

// sendResponses runs in a single goroutine to send all responses
func (s *Server) sendResponses() {

	for response := range s.responseChan {

		jsonData, err := json.Marshal(response)

		if err != nil {
			fmt.Printf("Failed to marshal response: %v\n", err)
			continue
		}

		// Send with retries
		for retries := 3; retries > 0; retries-- {

			_, err = s.pushSocket.SendBytes(jsonData, 0)

			if err == nil {
				break
			}

			fmt.Printf("Failed to send response, retrying (%d retries left): %v\n", retries-1, err)

			time.Sleep(100 * time.Millisecond)
		}

		if err != nil {
			fmt.Printf("Failed to send response after retries: %v\n", err)
		}
	}
}
