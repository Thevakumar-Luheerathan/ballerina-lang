#!/bin/bash
# Endpoint URL
IFS=$' \t\n'
endpoint="https://api.dev-central.ballerina.io/2.0/registry/packages/ballerina/http/*"

# Retrieve JSON from the endpoint
json_response=$(curl -s "$endpoint")

# Check if the curl command was successful
if [ $? -ne 0 ]; then  
    echo "Error: Unable to retrieve JSON from $endpoint"  
    exit 1
fi

# Extract the .balaURL from the JSON using jq (assuming jq is installed)
balaURL=$(echo "$json_response" | jq -r '.balaURL')
balaURL=${balaURL%?}

# Check if .balaURL is empty
if [ -z "$balaURL" ]; then  
    echo "Error: .balaURL not found in the JSON response"  
    exit 1
    fi
    
echo "balaURL: $balaURL"
# Invoke another curl command with .balaURL as the location
`curl --output http.bala "${balaURL}"`

# Check if the second curl command was successful
if [ $? -ne 0 ]; then  
    echo "Error: Unable to download file from $balaURL"  
    exit 1
fi
    
echo "File downloaded successfully from $balaURL"
