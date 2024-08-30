#!/bin/bash

ip=${1:-localhost}

function complex {
    local txt=$1
    local jpeg=$2
    local id=$3

    echo "started raytracer for scene $txt with texture $jpeg"
    payload_file="payload_complex_$id.json"
    result_file="result_complex_$id.txt"

    # Add scene.txt raw content to JSON.
    cat "$txt" | jq -sR '{scene: .}' > "$payload_file"

    # Add texmap.bmp binary to JSON (optional step, required only for some scenes).
    hexdump -ve '1/1 "%u\n"' "$jpeg" | jq -s --argjson original "$(<$payload_file)" '$original * {texmap: .}' > "$payload_file"

    # Send the request.
    curl -s -X POST http://"$ip":8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"$payload_file" > "$result_file"

    rm "$payload_file" "$result_file"
    echo "Finished for scene $txt with texture $jpeg"
}

image_file=calcada.jpeg
for text_file in ./workload/text_files/*; do
    for i in {0..1}; do
        unique_id=$(uuidgen)  # Generate a unique ID for each call
        complex "$text_file" "$image_file" "$unique_id" &
    done
done

wait
