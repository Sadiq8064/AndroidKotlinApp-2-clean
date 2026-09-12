import json
import boto3
from boto3.dynamodb.conditions import Key

# Initialize DynamoDB resource
dynamodb = boto3.resource('dynamodb')
sessions_table = dynamodb.Table('FocusSessions')
tasks_table = dynamodb.Table('FocusTasks')

def lambda_handler(event, context):
    """
    AWS Lambda function to sync focus analytics sessions and task records with DynamoDB.
    """
    try:
        body = event.get('body', event)
        if isinstance(body, str):
            body = json.loads(body)
            
        action = body.get('action')
        device_id = body.get('deviceId')
        
        if not device_id:
            return build_response(400, {'error': 'Missing deviceId'})
            
        if action == 'upload':
            sessions = body.get('sessions', [])
            uploaded_count = 0
            
            with sessions_table.batch_writer() as batch:
                for session in sessions:
                    timestamp = session.get('timestamp')
                    duration_seconds = session.get('duration_seconds')
                    date_string = session.get('date_string')
                    
                    if timestamp is not None and duration_seconds is not None:
                        batch.put_item(
                            Item={
                                'deviceId': device_id,
                                'timestamp': int(timestamp),
                                'duration_seconds': int(duration_seconds),
                                'date_string': str(date_string)
                            }
                        )
                        uploaded_count += 1
                        
            return build_response(200, {
                'message': 'Upload completed successfully',
                'uploaded_count': uploaded_count
            })
            
        elif action == 'fetch':
            response = sessions_table.query(
                KeyConditionExpression=Key('deviceId').eq(device_id)
            )
            items = response.get('Items', [])
            
            sessions = []
            for item in items:
                sessions.append({
                    'timestamp': int(item['timestamp']),
                    'duration_seconds': int(item['duration_seconds']),
                    'date_string': str(item['date_string'])
                })
                
            return build_response(200, {
                'deviceId': device_id,
                'sessions': sessions
            })

        elif action == 'upload_tasks':
            tasks = body.get('tasks', [])
            uploaded_count = 0
            
            with tasks_table.batch_writer() as batch:
                for task in tasks:
                    timestamp = task.get('timestamp')
                    text = task.get('text')
                    completed = task.get('completed')
                    selected_for_session = task.get('selected_for_session')
                    session_id = task.get('session_id')
                    date_completed = task.get('date_completed')
                    display_order = task.get('display_order')
                    
                    if timestamp is not None and text is not None:
                        batch.put_item(
                            Item={
                                'deviceId': device_id,
                                'timestamp': int(timestamp),
                                'text': str(text),
                                'completed': int(completed),
                                'selected_for_session': int(selected_for_session),
                                'session_id': str(session_id or ""),
                                'date_completed': str(date_completed or ""),
                                'display_order': int(display_order)
                            }
                        )
                        uploaded_count += 1
                        
            return build_response(200, {
                'message': 'Tasks upload completed successfully',
                'uploaded_count': uploaded_count
            })

        elif action == 'fetch_tasks':
            response = tasks_table.query(
                KeyConditionExpression=Key('deviceId').eq(device_id)
            )
            items = response.get('Items', [])
            
            tasks = []
            for item in items:
                tasks.append({
                    'timestamp': int(item['timestamp']),
                    'text': str(item['text']),
                    'completed': int(item['completed']),
                    'selected_for_session': int(item['selected_for_session']),
                    'session_id': str(item.get('session_id', "")),
                    'date_completed': str(item.get('date_completed', "")),
                    'display_order': int(item['display_order'])
                })
                
            return build_response(200, {
                'deviceId': device_id,
                'tasks': tasks
            })
            
        else:
            return build_response(400, {'error': f'Unsupported action: {action}'})
            
    except Exception as e:
        return build_response(500, {'error': str(e)})

def build_response(status_code, body_data):
    return {
        'statusCode': status_code,
        'headers': {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
        },
        'body': json.dumps(body_data)
    }
