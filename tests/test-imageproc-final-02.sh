#!/bin/bash

# CNV-23-24
# This script will issue in parallel on complex and one simple imageproc request.
# Modify it so it invokes your correct LB address and port in AWS, i.e., after http://
# If you need to change other request parameters to increase or decrease request complexity feel free to do so, provided they remain requests of different complexity.

ip=${1:-localhost}

function simple {
	local id=$(uuidgen)

    temp="./temp_$id.txt"
    echo "started imageproc simple"
    # Encode in Base64.
	base64 airplane.jpg > $temp

	# Append a formatting string.
	echo -e "data:image/jpg;base64,$(cat $temp)" > $temp

	# Send the request.
	curl -X POST http://$ip:8000/blurimage --data @"$temp" > result.txt   

    echo "finished imageproc simple"
}

function complex {
	local id=$(uuidgen)

    temp="./temp_$id.txt"
    echo "started imageproc complex"
    # Encode in Base64.
	base64 horse.jpg > $temp

	# Append a formatting string.
	echo -e "data:image/jpg;base64,$(cat $temp)" > $temp

	# Send the request.
	curl -X POST http://$ip:8000/blurimage --data @"$temp" > result.txt   

    echo "finished imageproc complex"
}

complex &
simple &
simple &
simple &
simple &
complex &
simple &
rm ./temp_*.txt
