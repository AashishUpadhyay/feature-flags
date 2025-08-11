package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

// CustomTime handles Java LocalDateTime format
type CustomTime struct {
	time.Time
}

func (ct *CustomTime) UnmarshalJSON(b []byte) error {
	s := strings.Trim(string(b), "\"")
	if s == "null" || s == "" {
		return nil
	}

	// Try parsing Java LocalDateTime format (without timezone)
	layouts := []string{
		"2006-01-02T15:04:05.999999", // with microseconds
		"2006-01-02T15:04:05.999",    // with milliseconds
		"2006-01-02T15:04:05",        // without fractional seconds
		time.RFC3339,                 // standard format
	}

	for _, layout := range layouts {
		if t, err := time.Parse(layout, s); err == nil {
			ct.Time = t
			return nil
		}
	}

	return fmt.Errorf("cannot parse time: %s", s)
}

// Organization struct that matches the Java model
type Organization struct {
	ID        *int64      `json:"id,omitempty"` // Pointer for nullable ID
	Name      string      `json:"name"`
	ParentID  *int64      `json:"parentId,omitempty"`  // Pointer for nullable parent ID
	CreatedAt *CustomTime `json:"createdAt,omitempty"` // Pointer for nullable timestamps
	UpdatedAt *CustomTime `json:"updatedAt,omitempty"`
}

// OrganizationCreateRequest for creating organizations (without ID/timestamps)
type OrganizationCreateRequest struct {
	ID       *int64 `json:"id"`
	Name     string `json:"name"`
	ParentID *int64 `json:"parentId,omitempty"`
}

type OrganizationBulkResponse struct {
	Status  string  `json:"status"`
	Message string  `json:"message"`
	OrgIds  []int64 `json:"orgIds"`
}

func TestBulkCreateOrganizations(t *testing.T) {
	env_vars := ReadEnviornmentVariables()

	url := fmt.Sprintf("http://%s:%s/organizations/bulk", env_vars.Apihost, env_vars.Apiport)
	fmt.Println("URL: ", url)

	// Create request body using OrganizationCreateRequest (without ID/timestamps)
	orgID1 := int64(1)
	orgID2 := int64(2)
	reqBody := []OrganizationCreateRequest{
		{
			ID:       &orgID1,
			Name:     "Organization 1",
			ParentID: nil, // No parent
		},
		{
			ID:       &orgID2,
			Name:     "Organization 2",
			ParentID: &orgID1, // Has parent with ID 1
		},
	}

	// Serialize to JSON
	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		t.Errorf("Error marshaling JSON: %v", err)
		return
	}

	fmt.Printf("Request JSON: %s\n", string(jsonData))

	// Send POST request
	bulkResp, err := http.Post(url, "application/json", bytes.NewBuffer(jsonData))
	if err != nil {
		t.Errorf("Error: %v", err)
		return
	}
	defer bulkResp.Body.Close()

	fmt.Println("Response Status:", bulkResp.Status)

	// Read response body
	bulkRespBody, err := io.ReadAll(bulkResp.Body)
	if err != nil {
		t.Errorf("Error reading response body: %v", err)
		return
	}

	fmt.Printf("Bulk Response Body: %s\n", string(bulkRespBody))

	// Check status code
	if bulkResp.StatusCode != http.StatusCreated && bulkResp.StatusCode != http.StatusOK {
		t.Errorf("Expected status 201 or 200, got %d", bulkResp.StatusCode)
		return
	}

	var bulkRespData OrganizationBulkResponse
	if err := json.Unmarshal(bulkRespBody, &bulkRespData); err != nil {
		t.Errorf("Error unmarshaling response JSON: %v", err)
		return
	}

	if bulkRespData.Status != "SUCCESS" {
		t.Errorf("Expected status SUCCESS, got %s", bulkRespData.Status)
		return
	}

	if len(bulkRespData.OrgIds) != 2 {
		t.Errorf("Expected 2 organizations, got %d", len(bulkRespData.OrgIds))
		return
	}

	for _, orgId := range bulkRespData.OrgIds {
		org, err := GetOrganization(orgId)
		if err != nil {
			t.Errorf("Error getting organization: %v", err)
			return
		}
		if org.ID == nil {
			t.Errorf("Organization %d: ID should not be nil", orgId)
		}
		if org.Name == "" {
			t.Errorf("Organization %d: Name should not be empty", orgId)
		}

		if *org.ID == 2 && *org.ParentID != 1 {
			t.Errorf("Organization 2 should have parent ID 1, got %d", *org.ParentID)
			return
		}
	}
}

func GetOrganization(orgId int64) (*Organization, error) {
	env_vars := ReadEnviornmentVariables()

	url := fmt.Sprintf("http://%s:%s/organizations/%d", env_vars.Apihost, env_vars.Apiport, orgId)
	fmt.Println("URL: ", url)

	resp, err := http.Get(url)
	if err != nil {
		return nil, fmt.Errorf("HTTP request failed: %v", err)
	}
	defer resp.Body.Close()

	fmt.Println("Response Status:", resp.Status)

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP status %d", resp.StatusCode)
	}

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %v", err)
	}

	fmt.Printf("Response Body: %s\n", string(body))

	// Deserialize JSON response
	var org Organization
	if err := json.Unmarshal(body, &org); err != nil {
		return nil, fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	return &org, nil
}
