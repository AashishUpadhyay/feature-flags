package main

import (
	"fmt"
	"io"
	"net/http"
	"testing"
	"time"
)

func TestHealthcheck(t *testing.T) {
	env_vars := ReadEnviornmentVariables()

	url := fmt.Sprintf("http://%s:%s/v1/hc", env_vars.Apihost, env_vars.Apiport)
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

		received := string(body)
		want := "{\n\t\"environment\": \"dev\",\n\t\"status\": \"available\",\n\t\"version\": \"1.0.0\"\n}\n"
		if received != want {
			t.Errorf("expected body %q, got %q", want, received)
			return
		}
		fmt.Println("Healthcheck passed successfully.")
		return
	}
}
