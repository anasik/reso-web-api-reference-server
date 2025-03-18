#!/bin/bash

# Define project names
BUILDER_PROJECT="builder-stack"
APP_PROJECT="app-stack"

# Delete build/libs folder if it exists
echo "Cleaning build/libs directory..."
if [ -d "build/libs" ]; then
    rm -rf build/libs
    echo "build/libs directory deleted."
else
    echo "build/libs directory does not exist. Continuing..."
fi

# Check if the user requested a builder rebuild
if [ "$1" == "--rebuild-builder" ]; then
    echo "Forcing rebuild of the builder container with --no-cache."
    docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT build --no-cache builder
else
    # Check if the builder container exists
    if [ "$(docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT ps -q builder)" ]; then
        echo "Builder container exists. Skipping rebuild."
    else
        echo "Builder container does not exist. Building it now."
        docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT build builder
    fi
fi

# Ensure the builder container is running
docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT up -d builder

# Wait until the container is fully running
echo "Waiting for builder container to be ready..."
until docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT ps | grep "builder" | grep "Up"; do
    sleep 2
    echo "Still waiting..."
done

# Run startup script inside the builder container
docker-compose -f docker-compose.builder.yml --project-name $BUILDER_PROJECT exec builder sh /usr/src/app/docker/scripts/startup.sh

# Build the odata-manager-app service
docker-compose --project-name $APP_PROJECT build odata-manager-app --no-cache
