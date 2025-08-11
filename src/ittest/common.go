package main

import (
	"fmt"
	"os"
)

type TestEnvironmentConfig struct {
	Apihost string
	Apiport string
}

func ReadEnviornmentVariables() TestEnvironmentConfig {
	api_host := os.Getenv("API_HOST")
	if api_host == "" {
		api_host_default := "localhost"
		fmt.Println("Using default Value of API_HOST:", api_host_default)
		api_host = api_host_default
	}
	fmt.Println("Value of API_HOST environment variable (or default):", api_host)

	api_port := os.Getenv("API_PORT")
	if api_port == "" {
		api_port_default := "9000"
		fmt.Println("Using default Value of API_PORT", api_port_default)
		api_port = api_port_default
	}
	fmt.Println("Value of API_PORT environment variable (or default):", api_port)

	envConfig := TestEnvironmentConfig{
		Apihost: api_host,
		Apiport: api_port,
	}

	return envConfig
}
