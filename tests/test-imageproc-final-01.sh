#!/bin/bash

# CNV-23-24
# This script will issue in parallel on complex and one simple imageproc request.
# Modify it so it invokes your correct LB address and port in AWS, i.e., after http://
# If you need to change other request parameters to increase or decrease request complexity feel free to do so, provided they remain requests of different complexity.

ip=${1:-localhost}

function simple {
    echo "started imageproc simple"
    touch temp1.txt
    # Encode in Base64.
	base64 airplane.jpg > temp1.txt                                            

	# Append a formatting string.
	echo -e "data:image/jpg;base64,$(cat temp1.txt)" > temp1.txt               

	# Send the request.
	curl -X POST http://$ip:8000/blurimage --data @"./temp1.txt" > result.txt   

	# Remove a formatting string (remove everything before the comma).
	sed -i 's/^[^,]*,//' result.txt                                          

	# Decode from Base64.
	base64 -d result.txt > result.jpg                                 

    echo "finished imageproc simple"
}

function complex {
    echo "started imageproc complex"
    touch temp2.txt
    # Encode in Base64.
	base64 horse.jpg > temp2.txt                                            

	# Append a formatting string.
	echo -e "data:image/jpg;base64,$(cat temp2.txt)" > temp2.txt               

	# Send the request.
	curl -X POST http://$ip:8000/blurimage --data @"./temp2.txt" > result.txt   

	# Remove a formatting string (remove everything before the comma).
	sed -i 's/^[^,]*,//' result.txt                                          

	# Decode from Base64.
	base64 -d result.txt > result.jpg                                 

    echo "finished imageproc complex"
}

complex &
simple &
