#!/bin/bash

# CNV-23-24

ip=${1:-localhost}

function simple {
    local image_name=$1
    local task=$2
    local endpoint

    if [[ "$task" == "0" ]]; then
    	endpoint="blurimage"
    else 
	endpoint="enhanceimage"
    fi

    echo "started $endpoint for $image_name"
    
    # Encode in Base64.
    touch temp.txt
    base64 "$image_name" > temp.txt

    # Append a formatting string.
    echo -e "data:image/jpg;base64,$(cat temp.txt)" > temp.txt

    # Send the request.
    curl -X POST "http://$ip:8000/$endpoint" --data @"./temp.txt" > result.txt

    # Remove a formatting string (remove everything before the comma).
    sed -i 's/^[^,]*,//' result.txt

    # Decode from Base64.
    base64 -d result.txt > result.jpg

    echo "finished $endpoint for $image_name"
}     

function run {
	local task=$1
	# Call the function with the image name as an argument
	for image_file in ./images/*; do
		for i in {0..4}; do
			simple "$image_file" "$task"
		done
	done
}
run 0
run 1
