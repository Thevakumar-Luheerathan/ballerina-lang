#!/bin/bash

# NUM_INSTANCES=2
# NUM_ITERATIONS=1
# INTERVAL=600
# PREFIX
CLIENT_PREFIX="${PREFIX}_ins${NUM_INSTANCES}_itr${NUM_ITERATIONS}"


# Define the Ballerina command to run
BAL="../distribution/zip/jballerina-tools/build/extracted-distributions/jballerina-tools-2201.3.5"
BAL_PULL_HTTP="$BAL/bin/bal pull ballerina/http"
BAL_PULL_CHOREO="$BAL/bin/bal pull ballerinax/choreo"

CURRENT_DIR=$(pwd)
echo $CURRENT_DIR

# Define the user home
USER_HOME="${CURRENT_DIR}/pull_home"

# enable verbose mode
export CENTRAL_VERBOSE_ENABLED=true
export BALLERINA_DEV_CENTRAL=true

mkdir -p "output/http"
mkdir -p "output/choreo"
sudo apt-get install speedtest-cli

# Loop for the specified number of iterations
for ((i = 0; i < NUM_ITERATIONS; i++)); do
    rm -rf "$USER_HOME"/*
    speedtest-cli >> $choreo_op_file 2>&1 &
    speedtest-cli >> $http_op_file 2>&1 &
    # Run the Ballerina command in the background for each instance
    for ((j = 1; j <= NUM_INSTANCES; j++)); do
        current_timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        export BALLERINA_HOME_DIR=${CURRENT_DIR}/pull_home/${j}
        http_op_file="output/http/instance${j}_output.txt"
        choreo_op_file="output/choreo/instance${j}_output.txt"
        echo "Current timestamp: $current_timestamp" >> $http_op_file
        $BAL_PULL_HTTP >> $http_op_file 2>&1 &  # Redirect output to a text file
        echo "Current timestamp: $current_timestamp" >> $choreo_op_file
        $BAL_PULL_CHOREO >> $choreo_op_file 2>&1 &  # Redirect output to a text file 
        sleep 1  # Give a small time gap between starting instances
    done
    
    # Wait for 5 minutes before the next iteration
    # sleep 900  # 15 minutes in seconds
    sleep $INTERVAL # 5 minutes in seconds
done

mv output OLD_OP/${CLIENT_PREFIX}
