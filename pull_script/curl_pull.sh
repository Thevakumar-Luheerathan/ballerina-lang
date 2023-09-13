# Define the number of instances you want to run
NUM_INSTANCES=15

# Define the total duration in seconds (2 hours)
TOTAL_DURATION=$((2 * 60 * 60))

# Calculate the number of iterations needed
NUM_ITERATIONS=$((TOTAL_DURATION / (5 * 60)))

# Define the user home
USER_HOME="/Users/luheerathan/luhee/Ballerina-Project-Files/Test/isb-411/pull_script"

# clear pull home
rm -rf "$USER_HOME/curl_home"/*

# Loop for the specified number of iterations
for ((i = 0; i < NUM_ITERATIONS; i++)); do
    mkdir $USER_HOME/curl_home/${i}
    # Run the Ballerina command in the background for each instance
    for ((j = 1; j <= NUM_INSTANCES; j++)); do
        current_timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        echo "Current timestamp: $current_timestamp" >> $USER_HOME/curl_op/choreo-${j}.txt
        echo "Current timestamp: $current_timestamp" >> $USER_HOME/curl_op/http-${j}.txt
        mkdir -p $USER_HOME/curl_home/choreo-${i}
        mkdir -p $USER_HOME/curl_home/http-${i}
        curl --output $USER_HOME/curl_home/choreo-${i}/${j}.bala "$(curl -s "https://api.dev-central.ballerina.io/2.0/registry/packages/ballerinax/choreo/*" | jq -r '.balaURL')" >> $USER_HOME/curl_op/choreo-${j}.txt 2>&1 & 
        curl --output $USER_HOME/curl_home/http-${i}/${j}.bala "$(curl -s "https://api.dev-central.ballerina.io/2.0/registry/packages/ballerina/http/*" | jq -r '.balaURL')" >> $USER_HOME/curl_op/http-${j}.txt 2>&1 & 
        
        # location=$(curl https://api.dev-central.ballerina.io/2.0/registry/packages/ballerinax/choreo/* | jq -r '.balaURL | tostring | gsub("^ +| +$";"")')
        # curl -v --output "$USER_HOME/pull_home/${j}.bala" $location >> "$USER_HOME/curl_op/${j}.txt" 2>&1 &
    done
    
    sleep 300 # 5 minutes in seconds
done
