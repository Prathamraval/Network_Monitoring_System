package main

import
(
	"fmt"
	"os"

	"modular-plugin/zmq" // Removed the leading slash
)

func main(){
	// Set default ZMQ endpoints
	pullEndpoint := "tcp://*:5555"         // For receiving requests from Vert.x
	pushEndpoint := "tcp://localhost:5556" // For sending responses to Vert.x

	// Override endpoints if provided
	if len(os.Args) > 2{
		pullEndpoint = os.Args[1]
		pushEndpoint = os.Args[2]
	}

	// Create and start ZMQ server
	server, err := zmq.NewServer(pullEndpoint, pushEndpoint)
	if err != nil{
		fmt.Printf("Failed to create ZMQ server: %v\n", err)
		os.Exit(1)
	}
	defer server.Close()

	fmt.Printf("Starting device monitor server on %s\n", pullEndpoint)
	err = server.Start()
	if err != nil{
		fmt.Printf("Failed to start ZMQ server: %v\n", err)
		os.Exit(1)
	}
}