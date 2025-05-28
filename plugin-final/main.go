package main

import (
	"fmt"
	"net/http"
	_ "net/http/pprof"
	"os"

	"plugin-final/zmq"
)

func main() {

	go func() {
		http.ListenAndServe("localhost:6060", nil)
	}()

	// Set default ZMQ endpoints
	pullEndpoint := "tcp://*:5555"         // For receiving requests from Vert.x
	pushEndpoint := "tcp://localhost:5556" // For sending responses to Vert.x

	// Override endpoints if provided
	if len(os.Args) > 2 {
		pullEndpoint = os.Args[1]
		pushEndpoint = os.Args[2]
	}

	// Create and start ZMQ server
	server, err := zmq.NewZmqServer(pullEndpoint, pushEndpoint)

	if err != nil {
		fmt.Printf("Failed to create ZMQ server: %v\n", err)
		os.Exit(1)
	}

	defer server.Close()

	fmt.Printf("Starting device monitor server on %s\n", pullEndpoint)

	err = server.Start()

	if err != nil {
		fmt.Printf("Failed to start ZMQ server: %v\n", err)
		os.Exit(1)
	}
}
