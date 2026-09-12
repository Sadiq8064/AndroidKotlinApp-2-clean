#!/bin/bash
set -e

# Change directory to script location
cd "$(dirname "$0")"

echo "1. Creating trust-policy.json..."
cat <<EOF > trust-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

echo "2. Creating DynamoDB table 'FocusSessions'..."
aws dynamodb create-table \
    --table-name FocusSessions \
    --attribute-definitions \
        AttributeName=deviceId,AttributeType=S \
        AttributeName=timestamp,AttributeType=N \
    --key-schema \
        AttributeName=deviceId,KeyType=HASH \
        AttributeName=timestamp,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST || echo "DynamoDB Table FocusSessions might already exist, continuing..."

echo "2b. Creating DynamoDB table 'FocusTasks'..."
aws dynamodb create-table \
    --table-name FocusTasks \
    --attribute-definitions \
        AttributeName=deviceId,AttributeType=S \
        AttributeName=timestamp,AttributeType=N \
    --key-schema \
        AttributeName=deviceId,KeyType=HASH \
        AttributeName=timestamp,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST || echo "DynamoDB Table FocusTasks might already exist, continuing..."

echo "3. Creating IAM Role 'FocusSessionsLambdaRole'..."
aws iam create-role --role-name FocusSessionsLambdaRole --assume-role-policy-document file://trust-policy.json || echo "IAM Role might already exist, continuing..."

echo "4. Attaching policies to IAM Role..."
aws iam attach-role-policy --role-name FocusSessionsLambdaRole --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam attach-role-policy --role-name FocusSessionsLambdaRole --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess

echo "Waiting for IAM Role propagation..."
sleep 10

echo "5. Zipping Lambda function..."
zip lambda.zip lambda_function.py

echo "6. Getting IAM Role ARN..."
ROLE_ARN=$(aws iam get-role --role-name FocusSessionsLambdaRole --query 'Role.Arn' --output text)

echo "7. Creating/Updating Lambda Function 'SyncFocusSessions'..."
aws lambda create-function \
    --function-name SyncFocusSessions \
    --runtime python3.9 \
    --zip-file fileb://lambda.zip \
    --handler lambda_function.lambda_handler \
    --role "$ROLE_ARN" || aws lambda update-function-code --function-name SyncFocusSessions --zip-file fileb://lambda.zip

echo "8. Creating API Gateway HTTP API..."
API_VAL=$(aws apigatewayv2 create-api --name SyncFocusAPI --protocol-type HTTP --target arn:aws:lambda:ap-south-1:451626663010:function:SyncFocusSessions || aws apigatewayv2 get-apis --query "Items[?Name=='SyncFocusAPI']" --output json)

API_ID=$(echo "$API_VAL" | grep -o '"ApiId": "[^"]*' | grep -o '[^"]*$')
if [ -z "$API_ID" ]; then
    API_ID=$(aws apigatewayv2 get-apis --query "Items[?Name=='SyncFocusAPI'].ApiId" --output text)
fi

echo "9. Adding API Gateway invoke permissions..."
aws lambda add-permission \
    --function-name SyncFocusSessions \
    --statement-id ApiGatewayInvokePermission \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:ap-south-1:451626663010:$API_ID/*/*" || echo "Invoke permission already added..."

echo "10. Deployed successfully!"
echo "API Endpoint: https://$API_ID.execute-api.ap-south-1.amazonaws.com"

# Cleanup temporary files
rm -f trust-policy.json lambda.zip
