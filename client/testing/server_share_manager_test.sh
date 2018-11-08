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
TEST_UTIL="$ROOT_DIR/client/testing/test_util.sh"

source $TEST_UTIL || exit 1

REQ_ID_FILENAME=$TEST_TMPDIR/req_id.txt
SHARE_FILENAME=$TEST_TMPDIR/a_share.txt
BOLT_SHARE_STORE_FILENAME="$TEST_TMPDIR/bolt_share_store.db"

# Start Svalbard server.
mkdir -p $FILECHANNEL_DIR
SERVER_PORT=$($PORTPICKER_CLI $$)
$SERVER_BIN -port=$SERVER_PORT -filechannel_root_dir=$FILECHANNEL_DIR\
    -bolt_share_store_file=$BOLT_SHARE_STORE_FILENAME\
    -tls_key_file=$TLS_KEY_FILENAME -tls_cert_file=$TLS_CERT_FILENAME\
    -token_validity=2s &
SERVER_PID=$!
trap "kill $SERVER_PID" EXIT
echo "... Starting server at port $SERVER_PORT as process $SERVER_PID"
sleep 2

# Run actual tests.
echo "+++ Request storage token ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "get_storage_token"\
    $REQ_ID_FILENAME "GmailKey" $location)
req_id=$(cat $REQ_ID_FILENAME)
token=$(get_token "Alice" $req_id)
echo "    Got response: $response"
assert_has_substring "$response" "$token"

echo "+++ Use the token to store a share..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
share_value="SomeShareValue"
response=$(send_request_to_server "store_share_with_token"\
    "GmailKey" $share_value $location $token)
echo "    Got response: $response"
assert_has_substring "$response" "Stored."

echo "+++ Request retrieval token for non-exising share ..."
location=$(get_share_location_proto "Bob" $SERVER_PORT)
response=$(send_request_to_server "get_retrieval_token"\
    $REQ_ID_FILENAME "GmailKey" $location)
echo "    Got response: $response"
assert_has_substring "$response" "Failure:"
assert_has_substring "$response" "share not found"

echo "+++ Request retrieval token for the stored share ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "get_retrieval_token"\
    $REQ_ID_FILENAME "GmailKey" $location)
req_id=$(cat $REQ_ID_FILENAME)
token=$(get_token "Alice" $req_id)
echo "    Got response: $response"
assert_has_substring "$response" "$token"

echo "+++ Retrieve the stored share ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "retrieve_share_with_token"\
    "GmailKey" $location $token $SHARE_FILENAME)
retrieved_share=$(cat $SHARE_FILENAME)
echo "    Got response: $response"
assert_has_substring "$response" "Retrieved."
assert_equals $share_value $retrieved_share

echo "+++ Store another share directly..."
location=$(get_share_location_proto "Bob" $SERVER_PORT)
another_share_value="SomeOtherShareValue"
response=$(send_request_to_server "store_share"\
    "DropboxKey" $another_share_value $location)
echo "    Got response: $response"
assert_has_substring "$response" "Stored."

echo "+++ Retrieve that another share directly ..."
location=$(get_share_location_proto "Bob" $SERVER_PORT)
response=$(send_request_to_server "retrieve_share"\
    "DropboxKey" $location $SHARE_FILENAME)
retrieved_share=$(cat $SHARE_FILENAME)
echo "    Got response: $response"
assert_has_substring "$response" "Retrieved."
assert_equals $another_share_value $retrieved_share

echo "+++ Retrieve the first share directly ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "retrieve_share"\
    "GmailKey" $location $SHARE_FILENAME)
retrieved_share=$(cat $SHARE_FILENAME)
echo "    Got response: $response"
assert_has_substring "$response" "Retrieved."
assert_equals $share_value $retrieved_share

echo "+++ Request deletion token for the first stored share ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "get_deletion_token"\
    $REQ_ID_FILENAME "GmailKey" $location)
req_id=$(cat $REQ_ID_FILENAME)
token=$(get_token "Alice" $req_id)
echo "    Got response: $response"
assert_has_substring "$response" "$token"

echo "+++ Delete the first stored share using the token ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "delete_share_with_token"\
    "GmailKey" $location $token)
echo "    Got response: $response"
assert_has_substring "$response" "Deleted."

echo "+++ Request retrieval token for the deleted share ..."
location=$(get_share_location_proto "Alice" $SERVER_PORT)
response=$(send_request_to_server "get_retrieval_token"\
    $REQ_ID_FILENAME "GmailKey" $location)
echo "    Got response: $response"
assert_has_substring "$response" "Failure:"
assert_has_substring "$response" "share not found"

echo "+++ Delete the second stored share directly ..."
location=$(get_share_location_proto "Bob" $SERVER_PORT)
response=$(send_request_to_server "delete_share"\
    "DropboxKey" $location)
echo "    Got response: $response"
assert_has_substring "$response" "Deleted."

echo "+++ Request retrieval token for the second deleted share ..."
location=$(get_share_location_proto "Bob" $SERVER_PORT)
response=$(send_request_to_server "get_retrieval_token"\
    $REQ_ID_FILENAME "DropboxKey" $location)
echo "    Got response: $response"
assert_has_substring "$response" "Failure:"
assert_has_substring "$response" "share not found"

echo "PASS"

