# Copyright 2018 The Svalbard Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
################################################################################

#!/bin/bash

ROOT_DIR="$TEST_SRCDIR/svalbard"
SERVER_BIN="$ROOT_DIR/server/go/linux_amd64_stripped/server"
PORTPICKER_CLI="$ROOT_DIR/external/org_python_pypi_portpicker/portpicker_cli"
SERVER_PORT=$($PORTPICKER_CLI $$)
HTTP_RESPONSE_BODY="$TEST_TMPDIR/http_response_body.txt"
FILECHANNEL_DIR="$TEST_TMPDIR/filechannel"

BOLT_SHARE_STORE_FILE="$TEST_TMPDIR/bolt_share_store.db"
TLS_KEY_FILE="$ROOT_DIR/server/testdata/test_server_key.pem"
TLS_CERT_FILE="$ROOT_DIR/server/testdata/test_server_certificate.crt"

# Test helpers.
get_token() {
  local owner_id="$1"
  local request_id="$2"
  local channel_file="$FILECHANNEL_DIR/${owner_id}_secondary_channel.txt"
  local token=$(grep "SVBD:$request_id" $channel_file | cut -d: -f3)
  echo $token
}

send_request_to_server() {
  local target_url="https://localhost:$SERVER_PORT/$1"
  shift
  local post_data="owner_id_type=FILE"
  for d do
    post_data="$post_data&$d"
  done
    local response=$(wget -q -O $HTTP_RESPONSE_BODY\
        --server-response --ca-certificate=$TLS_CERT_FILE\
        --post-data="$post_data" $target_url 2>&1) || die "request to $target_url failed"
  echo "$response"
}

get_http_response_code() {
  local response="$1"
  local code=$(echo "$response" | grep "HTTP/" | cut -d" " -f4)
  echo "$code"
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [ "$expected" != "$actual" ]; then
    echo "--- Failure. expected value: \"$expected\", actual value: \"$actual\""
    exit 1
  fi
  echo "    Success: got [$actual], as expected."
}

# Start Svalbard server.
mkdir -p $FILECHANNEL_DIR
$SERVER_BIN -port=$SERVER_PORT -filechannel_root_dir=$FILECHANNEL_DIR\
    -bolt_share_store_file=$BOLT_SHARE_STORE_FILE -token_validity=2s\
    -tls_key_file=$TLS_KEY_FILE -tls_cert_file=$TLS_CERT_FILE &
SERVER_PID=$!
sleep 2
echo "Started server at port $SERVER_PORT as process $SERVER_PID"
trap "kill $SERVER_PID" EXIT


# Run actual tests.
echo "+++ Requesting storage token via incomplete request..."
response=$(send_request_to_server "get_storage_token" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "400" "$response_code"

echo "+++ Requesting storage token..."
response=$(send_request_to_server "get_storage_token" "request_id=1234" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"
token=$(get_token "Alice" "1234")
echo "   Got token: $token"

SHARE_VALUE="Some Share Value To Be Stored."
echo "+++ Storing a share ..."
response=$(send_request_to_server "store_share" "token=$token" "owner_id=Alice" "secret_name=GmailKey" "share_value=$SHARE_VALUE")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"

echo "+++ Storing a share via an incomplete request..."
response=$(send_request_to_server "store_share" "token=$token" "owner_id=Alice" "share_value=$SHARE_VALUE")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "400" "$response_code"

echo "+++ Storing a share with a wrong token..."
response=$(send_request_to_server "store_share" "token=anotherBad" "owner_id=Alice" "secret_name=AmazonKey" "share_value=$SHARE_VALUE")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "403" "$response_code"

echo "+++ Requesting retrieval token..."
response=$(send_request_to_server "get_retrieval_token" "request_id=4273" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"
token=$(get_token "Alice" "4273")
echo "   Got token: $token"

echo "+++ Retrieving a share ..."
response=$(send_request_to_server "retrieve_share" "token=$token" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"
response_body=$(cat $HTTP_RESPONSE_BODY)
assert_equals "$SHARE_VALUE" "$response_body"

echo "+++ Retrieving a share via an incomplete request ..."
response=$(send_request_to_server "retrieve_share" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "400" "$response_code"

echo "+++ Retrieving a share via another incomplete request ..."
response=$(send_request_to_server "retrieve_share" "token=$token" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "400" "$response_code"

echo "+++ Retrieving a share using an expired token..."
sleep 2
response=$(send_request_to_server "retrieve_share" "token=$token" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "403" "$response_code"

echo "+++ Retrieving a non-existing share ..."
response=$(send_request_to_server "retrieve_share" "token=$token" "owner_id=Alice" "secret_name=AmazonKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "403" "$response_code"

echo "+++ Retrieving an existing share with wrong token ..."
response=$(send_request_to_server "retrieve_share" "token=badToken" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "403" "$response_code"

echo "+++ Requesting retrieval token for non-existing share..."
response=$(send_request_to_server "get_retrieval_token" "request_id=7636" "owner_id=Alice" "secret_name=AmazonKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "404" "$response_code"

echo "+++ Requesting deletion token for non-existing share..."
response=$(send_request_to_server "get_deletion_token" "request_id=6256" "owner_id=Alice" "secret_name=AmazonKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "404" "$response_code"

echo "+++ Requesting deletion token..."
response=$(send_request_to_server "get_deletion_token" "request_id=9237" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"
token=$(get_token "Alice" "9237")
echo "   Got token: $token"

echo "+++ Deleting a share with a wrong owner ID ..."
response=$(send_request_to_server "delete_share" "token=$token" "owner_id=Bob" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "403" "$response_code"

echo "+++ Deleting a share via incomplete request..."
response=$(send_request_to_server "delete_share" "token=$token" "owner_id=Alice")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "400" "$response_code"

echo "+++ Deleting a share ..."
response=$(send_request_to_server "delete_share" "token=$token" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "200" "$response_code"

echo "+++ Deleting a deleted share ..."
response=$(send_request_to_server "delete_share" "token=$token" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "500" "$response_code"

echo "+++ Requesting retrieval token for the deleted share..."
response=$(send_request_to_server "get_retrieval_token" "request_id=6625" "owner_id=Alice" "secret_name=GmailKey")
echo "    Got response: $response"
echo "    Body: $(cat $HTTP_RESPONSE_BODY)"
response_code=$(get_http_response_code "$response")
assert_equals "404" "$response_code"

echo "PASS"
