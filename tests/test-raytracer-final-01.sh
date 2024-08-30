#!/bin/bash

# CNV-23-24
# This script will issue in parallel on complex and one simple raytracer request.
# Modify it so it invokes your correct LB address and port in AWS, i.e., after http://
# If you need to change other request parameters to increase or decrease request complexity feel free to do so, provided they remain requests of different complexity.

ip=${1:-localhost}

function simple {
    echo "started raytracer simple"
    # Add scene.txt raw content to JSON.
	cat test01.txt | jq -sR '{scene: .}' > payload_simple.json                                                                          
    # Add texmap.bmp binary to JSON (optional step, required only for some scenes).
	hexdump -ve '1/1 "%u\n"' calcada.jpeg | jq -s --argjson original "$(<payload_simple.json)" '$original * {texmap: .}' > payload_simple.json  
    # Send the request.
	curl -s -X POST http://$ip:8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"./payload_simple.json" > result_simple.txt   
    # Remove a formatting string (remove everything before the comma).
	sed -i 's/^[^,]*,//' result_simple.txt                                                                                             
    base64 -d result_simple.txt > result_simple.bmp
    echo "finished raytracer simple"
}

function complex {
    echo "started raytrace complex"
    # Add scene.txt raw content to JSON.
	cat test04.txt | jq -sR '{scene: .}' > payload_complex.json                                                                          
    # Add texmap.bmp binary to JSON (optional step, required only for some scenes).
	hexdump -ve '1/1 "%u\n"' calcada.jpeg | jq -s --argjson original "$(<payload_complex.json)" '$original * {texmap: .}' > payload_complex.json  
    # Send the request.
	curl -s -X POST http://$ip:8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"./payload_complex.json" > result_complex.txt   
    # Remove a formatting string (remove everything before the comma).
	sed -i 's/^[^,]*,//' result_complex.txt                                                                                             
    base64 -d result_complex.txt > result_complex.bmp
    echo "finished raytracer complex"
}

complex &
simple &
aws ec2 terminate-instances --instance-ids i-01f63a14df3f543e7
