#!/bin/bash

# NUM_INSTANCES=2
# NUM_ITERATIONS=1
# INTERVAL=10
# PREFIX="temp"
CLIENT_PREFIX="${PREFIX}_ins${NUM_INSTANCES}_itr${NUM_ITERATIONS}"


# Define the Ballerina command to run
BAL="./distribution/zip/jballerina-tools/build/extracted-distributions/jballerina-tools-2201.3.5"
BAL_PULL_HTTP="$BAL/bin/bal pull ballerina/http"
BAL_PULL_CHOREO="$BAL/bin/bal pull ballerinax/choreo"

# CURRENT_DIR=$(pwd)
CURRENT_DIR="$(pwd)/pull_script"
echo $CURRENT_DIR

# Define the user home
USER_HOME="${CURRENT_DIR}/pull_home"

# enable verbose mode
export CENTRAL_VERBOSE_ENABLED=true

mkdir -p "${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}"
mkdir -p "${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}/http"
mkdir -p "${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}/choreo"


speed_op_file="${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}/speed.txt"

# Loop for the specified number of iterations
for ((i = 0; i < NUM_ITERATIONS; i++)); do
    rm -rf "$USER_HOME"/*
    speedtest-cli >> $speed_op_file 2>&1 &
    sleep 90
    # Run the Ballerina command in the background for each instance
    for ((j = 1; j <= NUM_INSTANCES; j++)); do
        current_timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        export BALLERINA_HOME_DIR=${CURRENT_DIR}/pull_home/${j}
        http_op_file="${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}/http/instance${j}_output.txt"
        choreo_op_file="${CURRENT_DIR}/OLD_OP/${CLIENT_PREFIX}/choreo/instance${j}_output.txt"
        echo "Current timestamp: $current_timestamp" >> $http_op_file
        $BAL_PULL_HTTP >> $http_op_file 2>&1 &  # Redirect output to a text file
        echo "Current timestamp: $current_timestamp" >> $choreo_op_file
        $BAL_PULL_CHOREO >> $choreo_op_file 2>&1 &  # Redirect output to a text file 
        sleep 1  # Give a small time gap between starting instances
    done
    
    sleep $INTERVAL 
done
