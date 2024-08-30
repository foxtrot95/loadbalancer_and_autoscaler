#!/bin/bash

# CNV-23-24
# This script will issue in parallel on complex and one simple raytracer request.
# Modify it so it invokes your correct LB address and port in AWS, i.e., after http://
# If you need to change other request parameters to increase or decrease request complexity feel free to do so, provided they remain requests of different complexity.
source ../deployment_scripts/config.sh

ip=${1:-localhost}

function simple {
    local txt=test01.txt
    local jpeg=calcada.jpeg
    local id=$(uuidgen)

    echo "started raytracer simple"
    payload_file="payload_complex_$id.json"
    result_file="result_complex_$id.txt"

    # Add scene.txt raw content to JSON.
    cat "$txt" | jq -sR '{scene: .}' > "$payload_file"

    # Add texmap.bmp binary to JSON (optional step, required only for some scenes).
    hexdump -ve '1/1 "%u\n"' "$jpeg" | jq -s --argjson original "$(<$payload_file)" '$original * {texmap: .}' > "$payload_file"

    # Send the request.
    curl -s -X POST http://"$ip":8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"$payload_file" > "$result_file"

    rm "$payload_file" "$result_file"
    echo "finished raytracer simple"
}

function complex {
    local txt=test04.txt
    local jpeg=calcada.jpeg
    local id=$(uuidgen)

    echo "started raytracer complex"
    payload_file="payload_complex_$id.json"
    result_file="result_complex_$id.txt"

    # Add scene.txt raw content to JSON.
    cat "$txt" | jq -sR '{scene: .}' > "$payload_file"

    # Add texmap.bmp binary to JSON (optional step, required only for some scenes).
    hexdump -ve '1/1 "%u\n"' "$jpeg" | jq -s --argjson original "$(<$payload_file)" '$original * {texmap: .}' > "$payload_file"

    # Send the request.
    curl -s -X POST http://"$ip":8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"$payload_file" > "$result_file"

    rm "$payload_file" "$result_file"
    echo "finished raytracer complex"
}

complex &
simple &
aws ec2 terminate-instances --instance-ids i-0379735978257e6d0 &
simple &
simple &
simple &
complex &
simple &

