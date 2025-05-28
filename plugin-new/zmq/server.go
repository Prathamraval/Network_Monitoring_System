package zmq

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"plugin-new/models"
	"plugin-new/monitor"

	"github.com/pebbe/zmq4"
)

// Server handles ZeroMQ communication with PUSH/PULL pattern
type Server struct {
	pullSocket   *zmq4.Socket
	pushSocket   *zmq4.Socket
	pullEndpoint string
	pushEndpoint string
	requestsWg   sync.WaitGroup
}

// NewServer creates a new ZMQ server with PUSH/PULL pattern
func NewServer(pullEndpoint, pushEndpoint string) (*Server, error) {

	// Socket to receive tasks (PULL)
	pullSocket, err := zmq4.NewSocket(zmq4.PULL)

	if err != nil {
		return nil, fmt.Errorf("failed to create ZMQ PULL socket: %v", err)
	}

	// Socket to send results (PUSH)
	pushSocket, err := zmq4.NewSocket(zmq4.PUSH)

	if err != nil {
		pullSocket.Close()
		return nil, fmt.Errorf("failed to create ZMQ PUSH socket: %v", err)
	}

	// Set high water mark to prevent message loss
	pullSocket.SetRcvhwm(1000) // Corrected method name
	pushSocket.SetSndhwm(1000) // Corrected method name

	return &Server{
		pullSocket:   pullSocket,
		pushSocket:   pushSocket,
		pullEndpoint: pullEndpoint,
		pushEndpoint: pushEndpoint,
		requestsWg:   sync.WaitGroup{},
	}, nil
}

// Start begins listening for messages
func (s *Server) Start() error {

	// Bind the PULL socket to receive tasks

	err := s.pullSocket.Bind(s.pullEndpoint)

	if err != nil {
		return fmt.Errorf("failed to bind ZMQ PULL socket: %v", err)
	}

	// Connect the PUSH socket to send results
	err = s.pushSocket.Connect(s.pushEndpoint)

	if err != nil {
		return fmt.Errorf("failed to connect ZMQ PUSH socket: %v", err)
	}

	fmt.Printf("ZMQ server started. PULL socket bound to %s, PUSH socket connected to %s\n",
		s.pullEndpoint, s.pushEndpoint)

	for {
		// Wait for next request from client
		message, err := s.pullSocket.RecvBytes(0)

		if err != nil {
			fmt.Printf("Error receiving message: %v\n", err)
			time.Sleep(100 * time.Millisecond) // Prevent tight loop on error
			continue
		}

		fmt.Printf("Received request: %d bytes\n", len(message))

		// Process the message in a goroutine
		s.requestsWg.Add(1)

		go func(msg []byte) {

			defer s.requestsWg.Done()

			// Process the message
			err := s.processMessage(msg)

			if err != nil {

				fmt.Printf("Error processing message: %v\n", err)

				// Try to extract the request ID for error response
				var request models.ZMQRequest

				if jsonErr := json.Unmarshal(msg, &request); jsonErr == nil {

					errorResponse := map[string]interface{}{
						"request_id": request.RequestID,
						"type":       request.Command,
						"details":    fmt.Sprintf("Error processing message: %v", err),
					}

					responseJSON, _ := json.Marshal(errorResponse)
					s.pushSocket.SendBytes(responseJSON, 0)
				}
			}
		}(message)
	}
}

// Close shuts down the ZMQ server
func (s *Server) Close() {

	// Wait for all request goroutines to finish
	s.requestsWg.Wait()

	if s.pullSocket != nil {
		s.pullSocket.Close()
	}

	if s.pushSocket != nil {
		s.pushSocket.Close()
	}
}

// processMessage handles incoming ZMQ messages
func (s *Server) processMessage(message []byte) error {

	// Parse the command and data from the message
	var request models.ZMQRequest

	err := json.Unmarshal(message, &request)

	if err != nil {
		return fmt.Errorf("failed to parse request: %v", err)
	}

	switch request.Command {

	case "discovery":
		return s.handleDiscovery(request)

	case "metrics":
		return s.handleMetrics(request)

	default:
		return fmt.Errorf("unknown command: %s", request.Command)

	}
}

// handleDiscovery processes discovery requests
func (s *Server) handleDiscovery(request models.ZMQRequest) error {

	var input models.BatchInput

	err := json.Unmarshal(request.Data, &input)

	if err != nil {
		return fmt.Errorf("failed to parse discovery input: %v", err)
	}

	var wg sync.WaitGroup
	sem := make(chan struct{}, 50) // Limit to 50 goroutines

	for _, device := range input {
		wg.Add(1)
		sem <- struct{}{}

		go func(dev models.DeviceInput) {
			defer wg.Done()
			defer func() { <-sem }()
			monitor.HandleDiscovery(request.RequestID, dev, s.sendResponse)
		}(device)
	}

	wg.Wait()
	return nil
}

// handleMetrics processes metrics collection requests
func (s *Server) handleMetrics(request models.ZMQRequest) error {
	var input models.BatchInput
	err := json.Unmarshal(request.Data, &input)
	if err != nil {
		return fmt.Errorf("failed to parse metrics input: %v", err)
	}

	monitor.HandleMetrics(request.RequestID, input, s.sendResponse)
	return nil
}

// sendResponse sends a response back to the client
func (s *Server) sendResponse(response interface{}) error {
	// Marshal the response to JSON
	jsonData, err := json.Marshal(response)
	if err != nil {
		return fmt.Errorf("failed to marshal response: %v", err)
	}

	// Send the response with retry
	for retries := 3; retries > 0; retries-- {
		_, err = s.pushSocket.SendBytes(jsonData, 0)
		if err == nil {
			return nil
		}
		fmt.Printf("Failed to send response, retrying (%d retries left): %v\n", retries-1, err)
		time.Sleep(100 * time.Millisecond)
	}

	return fmt.Errorf("failed to send response after retries: %v", err)
}
