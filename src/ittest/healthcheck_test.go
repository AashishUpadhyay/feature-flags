package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"testing"
	"time"
)

func TestHealthcheck(t *testing.T) {
	env_vars := ReadEnviornmentVariables()

	url := fmt.Sprintf("http://%s:%s/actuator/health", env_vars.Apihost, env_vars.Apiport)
	fmt.Println("URL: ", url)
	maxRetries := 9
	for retries := 0; retries < maxRetries; retries++ {
		resp, err := http.Get(url)
		if err != nil {
			if retries < maxRetries-1 {
				fmt.Println("Error:", err)
				fmt.Printf("Retrying in 5 seconds...\n")
				time.Sleep(5 * time.Second) // Wait for 5 seconds before retrying
				continue
			}
			fmt.Printf("Failed after %d retries\n", maxRetries)
			return
		}

		defer resp.Body.Close()

		// Read the response body
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			t.Error("Error:", err)
			fmt.Println("Error:", err)
			return
		}

		// Parse JSON response
		var healthResponse map[string]interface{}
		if err := json.Unmarshal(body, &healthResponse); err != nil {
			t.Errorf("Failed to parse JSON response: %v", err)
			return
		}

		// Check overall status
		status, ok := healthResponse["status"].(string)
		if !ok || status != "UP" {
			t.Errorf("Expected overall status 'UP', got %v", healthResponse["status"])
			return
		}

		// Check components exist and have UP status
		components, ok := healthResponse["components"].(map[string]interface{})
		if !ok {
			t.Error("Expected 'components' field in response")
			return
		}

		expectedComponents := []string{"db", "featureFlag", "livenessState", "ping", "readinessState"}
		for _, componentName := range expectedComponents {
			component, exists := components[componentName].(map[string]interface{})
			if !exists {
				t.Errorf("Expected component '%s' not found", componentName)
				return
			}

			componentStatus, ok := component["status"].(string)
			if !ok || componentStatus != "UP" {
				t.Errorf("Expected component '%s' status 'UP', got %v", componentName, component["status"])
				return
			}
		}

		// Check groups array exists
		groups, ok := healthResponse["groups"].([]interface{})
		if !ok {
			t.Error("Expected 'groups' field in response")
			return
		}

		if len(groups) < 2 {
			t.Errorf("Expected at least 2 groups, got %d", len(groups))
			return
		}

		fmt.Println("Healthcheck passed successfully.")
		fmt.Printf("Overall Status: %s\n", status)
		fmt.Printf("Components checked: %d\n", len(expectedComponents))
		return
	}
}
