#!/bin/bash

# Configuration
CONTAINER_NAME="dev_dataverse"
PID_PROVIDER="did1"
AUTHORITY="oyd"
API_URL="https\:\/\/odrl.dev.codata.org\/api\/did\/create"

echo "--------------------------------------------------------"
echo "Configuring Dataverse for DID ($PID_PROVIDER) in $CONTAINER_NAME"
echo "Authority: $AUTHORITY"
echo "--------------------------------------------------------"

# We feed the commands directly into bash inside the container via stdin
docker exec -i "$CONTAINER_NAME" bash <<EOF
echo "AS_ADMIN_PASSWORD=admin" > /tmp/adminpass
echo "Setting JVM options..."

# Removing old authority if it exists to avoid conflicts, then adding new one
asadmin --user admin --passwordfile /tmp/adminpass delete-jvm-options "-Ddataverse.pid.$PID_PROVIDER.authority=web\:example.com"
asadmin --user admin --passwordfile /tmp/adminpass delete-jvm-options "-Ddataverse.pid.$PID_PROVIDER.authority=web:example.com"

asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.providers=$PID_PROVIDER"
asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.default-provider=$PID_PROVIDER"
asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.$PID_PROVIDER.type=DID"
asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.$PID_PROVIDER.label=DIDProvider"
asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.$PID_PROVIDER.authority=$AUTHORITY"
asadmin --user admin --passwordfile /tmp/adminpass create-jvm-options "-Ddataverse.pid.$PID_PROVIDER.did.api-url=$API_URL"

echo "Restarting Payara..."
asadmin --user admin --passwordfile /tmp/adminpass restart-domain domain1

echo "Verification..."
asadmin --user admin --passwordfile /tmp/adminpass list-jvm-options | grep "pid"

rm /tmp/adminpass
EOF

echo "--------------------------------------------------------"
echo "DID configuration complete."
echo "--------------------------------------------------------"
